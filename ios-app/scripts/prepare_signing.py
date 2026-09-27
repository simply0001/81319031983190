#!/usr/bin/env python3
"""Prepare an Apple Distribution CSR on Windows/macOS, then package its certificate.

Requires cryptography. All private material must stay outside the repository.
"""
import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import secrets

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

TEAM_ID = "72PMGDSD3B"
REPO = Path(__file__).resolve().parents[2]


def prepare(directory):
    directory = Path(directory).resolve()
    if directory.is_relative_to(REPO):
        raise ValueError("Signing material must be stored outside the repository")
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    key_path = directory / "PocketPass-AppleDistribution.key.pem"
    password_path = directory / "PocketPass-AppleDistribution.password"
    csr_path = directory / "PocketPass-AppleDistribution.certSigningRequest"
    if key_path.exists() != password_path.exists():
        raise ValueError("Incomplete existing signing material; restore its key/password pair before continuing")
    if key_path.exists():
        password = password_path.read_bytes()
        key = serialization.load_pem_private_key(key_path.read_bytes(), password)
    else:
        password = secrets.token_urlsafe(40).encode("ascii")
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        for path, contents in (
            (password_path, password),
            (key_path, key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                         serialization.BestAvailableEncryption(password))),
        ):
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as output:
                output.write(contents)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size != 2048:
        raise ValueError("The Apple Distribution request requires the expected RSA-2048 key")
    if csr_path.exists():
        csr = x509.load_pem_x509_csr(csr_path.read_bytes())
        if not csr.is_signature_valid or csr.public_key().public_numbers() != key.public_key().public_numbers():
            raise ValueError("The saved certificate request does not match the saved private key")
    else:
        csr = x509.CertificateSigningRequestBuilder().subject_name(x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, "PocketPass Apple Distribution"),
            x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, TEAM_ID),
        ])).sign(key, hashes.SHA256())
        csr_path.write_bytes(csr.public_bytes(serialization.Encoding.PEM))
    return directory, key, password, csr_path


def complete(directory, certificate_path):
    directory, key, password, _ = prepare(directory)
    data = Path(certificate_path).read_bytes()
    cert = x509.load_pem_x509_certificate(data) if data.startswith(b"-----BEGIN") else x509.load_der_x509_certificate(data)
    if cert.public_key().public_numbers() != key.public_key().public_numbers():
        raise ValueError("This certificate was created from a different private key/CSR")
    teams = cert.subject.get_attributes_for_oid(NameOID.ORGANIZATIONAL_UNIT_NAME)
    if not any(value.value == TEAM_ID for value in teams):
        raise ValueError("This certificate belongs to a different Apple Developer team")
    names = cert.subject.get_attributes_for_oid(NameOID.COMMON_NAME)
    if not any(value.value.startswith("Apple Distribution:") for value in names):
        raise ValueError("Choose an Apple Distribution certificate, not a push or development certificate")
    usage = cert.extensions.get_extension_for_class(x509.ExtendedKeyUsage).value
    if ExtendedKeyUsageOID.CODE_SIGNING not in usage:
        raise ValueError("This certificate cannot sign app code")
    now = datetime.now(timezone.utc)
    if not cert.not_valid_before_utc <= now < cert.not_valid_after_utc:
        raise ValueError("This certificate is not currently valid")
    result = directory / "PocketPass-AppleDistribution.p12"
    bundle = pkcs12.serialize_key_and_certificates(
        b"PocketPass Apple Distribution", key, cert, None,
        serialization.BestAvailableEncryption(password),
    )
    fd = os.open(result, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as output:
        output.write(bundle)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--certificate", type=Path)
    args = parser.parse_args()
    try:
        if args.certificate:
            output = complete(args.directory, args.certificate)
            print("Distribution P12 created; certificate matches the local key and Apple team:")
        else:
            _, _, _, output = prepare(args.directory)
            print("Upload this public certificate request to Apple Distribution certificates:")
        print(output)
    except (ValueError, OSError, x509.ExtensionNotFound) as error:
        parser.exit(1, f"Signing preparation failed: {error}\n")
