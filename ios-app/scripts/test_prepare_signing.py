from datetime import datetime, timedelta, timezone
from pathlib import Path
import tempfile
import unittest

from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import Encoding, pkcs12
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

from prepare_signing import TEAM_ID, complete, prepare


class SigningPreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def certificate(self, key, team=TEAM_ID):
        name = x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, "Apple Distribution: Signing Test"),
            x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, team),
        ])
        now = datetime.now(timezone.utc)
        certificate = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                       .public_key(key.public_key()).serial_number(x509.random_serial_number())
                       .not_valid_before(now - timedelta(minutes=1)).not_valid_after(now + timedelta(days=1))
                       .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.CODE_SIGNING]), critical=False)
                       .sign(key, hashes.SHA256()))
        path = self.root / "test.cer"
        path.write_bytes(certificate.public_bytes(Encoding.DER))
        return path

    def test_reuses_key_and_valid_signed_request(self):
        _, key, password, csr = prepare(self.root)
        saved_request = csr.read_bytes()
        _, again, same_password, _ = prepare(self.root)
        self.assertEqual(key.private_numbers(), again.private_numbers())
        self.assertEqual(password, same_password)
        self.assertEqual(saved_request, csr.read_bytes())
        self.assertTrue(x509.load_pem_x509_csr(saved_request).is_signature_valid)

    def test_matching_certificate_produces_encrypted_p12(self):
        _, key, password, _ = prepare(self.root)
        output = complete(self.root, self.certificate(key))
        loaded_key, cert, _ = pkcs12.load_key_and_certificates(output.read_bytes(), password)
        self.assertEqual(key.public_key().public_numbers(), loaded_key.public_key().public_numbers())
        self.assertEqual(key.public_key().public_numbers(), cert.public_key().public_numbers())
        with self.assertRaises(ValueError):
            pkcs12.load_key_and_certificates(output.read_bytes(), None)

    def test_rejects_another_key_or_team(self):
        _, key, _, _ = prepare(self.root)
        other_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        with self.assertRaisesRegex(ValueError, "different private key"):
            complete(self.root, self.certificate(other_key))
        with self.assertRaisesRegex(ValueError, "different Apple Developer team"):
            complete(self.root, self.certificate(key, team="WRONGTEAM1"))
        self.assertFalse((self.root / "PocketPass-AppleDistribution.p12").exists())


if __name__ == "__main__":
    unittest.main()
