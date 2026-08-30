package com.pocketpass.app.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsentLinkPolicyTest {
    @Test
    fun acceptsTheExactConsentLink() {
        assertEquals(
            ConsentLinkDecision.Consent("auth-12345678"),
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz/oauth/consent?authorization_id=auth-12345678",
            ),
        )
    }

    @Test
    fun ignoresNullBlankAndOverlongInput() {
        assertEquals(ConsentLinkDecision.Ignored, ConsentLinkPolicy.evaluate(null))
        assertEquals(ConsentLinkDecision.Ignored, ConsentLinkPolicy.evaluate(""))
        assertEquals(ConsentLinkDecision.Ignored, ConsentLinkPolicy.evaluate("   "))
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz/oauth/consent?authorization_id=" + "a".repeat(4_096),
            ),
        )
    }

    @Test
    fun ignoresGarbage() {
        assertEquals(ConsentLinkDecision.Ignored, ConsentLinkPolicy.evaluate("not a uri at all"))
    }

    @Test
    fun ignoresAnUntrustedHost() {
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate(
                "https://attacker.example/oauth/consent?authorization_id=auth-12345678",
            ),
        )
    }

    @Test
    fun ignoresPlainHttp() {
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate(
                "http://links.pocketpass.xyz/oauth/consent?authorization_id=auth-12345678",
            ),
        )
    }

    @Test
    fun ignoresUserInfoAndNonDefaultPorts() {
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate(
                "https://attacker.example@links.pocketpass.xyz/oauth/consent" +
                    "?authorization_id=auth-12345678",
            ),
        )
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz:8443/oauth/consent?authorization_id=auth-12345678",
            ),
        )
    }

    @Test
    fun acceptsTheExplicitDefaultPort() {
        assertEquals(
            ConsentLinkDecision.Consent("auth-12345678"),
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz:443/oauth/consent?authorization_id=auth-12345678",
            ),
        )
    }

    @Test
    fun ignoresAnotherPathOnTheTrustedHost() {
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz/auth/callback?authorization_id=auth-12345678",
            ),
        )
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz/oauth/consent/?authorization_id=auth-12345678",
            ),
        )
    }

    @Test
    fun ignoresAMissingAuthorizationId() {
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate("https://links.pocketpass.xyz/oauth/consent"),
        )
        assertEquals(
            ConsentLinkDecision.Ignored,
            ConsentLinkPolicy.evaluate("https://links.pocketpass.xyz/oauth/consent?other=value"),
        )
    }

    @Test
    fun ignoresAnIdOutsideTheAllowedPattern() {
        val tooShort = "a".repeat(7)
        val tooLong = "a".repeat(129)
        val illegal = "auth/12345678"

        listOf(tooShort, tooLong, illegal, "").forEach { candidate ->
            assertEquals(
                ConsentLinkDecision.Ignored,
                ConsentLinkPolicy.evaluate(
                    "https://links.pocketpass.xyz/oauth/consent?authorization_id=$candidate",
                ),
                "expected $candidate to be ignored",
            )
        }
    }

    @Test
    fun acceptsTheBoundaryLengths() {
        listOf("a".repeat(8), "a".repeat(128)).forEach { candidate ->
            assertEquals(
                ConsentLinkDecision.Consent(candidate),
                ConsentLinkPolicy.evaluate(
                    "https://links.pocketpass.xyz/oauth/consent?authorization_id=$candidate",
                ),
            )
        }
    }

    @Test
    fun findsTheIdAmongOtherParameters() {
        assertEquals(
            ConsentLinkDecision.Consent("auth-12345678"),
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz/oauth/consent" +
                    "?state=xyz&authorization_id=auth-12345678&scope=friends",
            ),
        )
    }

    @Test
    fun decodesAPercentEncodedId() {
        assertEquals(
            ConsentLinkDecision.Consent("auth.12345678"),
            ConsentLinkPolicy.evaluate(
                "https://links.pocketpass.xyz/oauth/consent?authorization_id=auth%2E12345678",
            ),
        )
    }
}
