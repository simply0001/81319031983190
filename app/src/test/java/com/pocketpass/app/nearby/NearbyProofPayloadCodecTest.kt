package com.pocketpass.app.nearby

import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.SubmitNearbyEncounterCommand
import com.pocketpass.app.domain.model.UserId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NearbyProofPayloadCodecTest {
    @Test
    fun `protected receipt payload round trips without losing identity`() {
        val command = fixtureCommand()

        val decoded = NearbyProofPayloadCodec.decode(
            NearbyProofPayloadCodec.encode(command),
        )

        assertEquals(command, decoded)
    }

    @Test
    fun `malformed protected receipt is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            NearbyProofPayloadCodec.decode("not-a-receipt")
        }
    }

    private fun fixtureCommand() = SubmitNearbyEncounterCommand(
        accountId = UserId("10000000-0000-0000-0000-000000000001"),
        encounterId = EncounterId("20000000-0000-0000-0000-000000000002"),
        clientOperationId = ClientOperationId("20000000-0000-0000-0000-000000000002"),
        ownToken = "30000000-0000-0000-0000-000000000003",
        peerToken = "40000000-0000-0000-0000-000000000004",
        ownSigningPublicKey = "own-public-key",
        peerSigningPublicKey = "peer-public-key",
        transcriptHash = "transcript-hash",
        ownSignature = "own-signature",
        peerSignature = "peer-signature",
        occurredAt = Instant.parse("2026-07-29T12:46:00Z"),
    )
}
