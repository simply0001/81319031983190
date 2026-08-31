package com.pocketpass.app.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthCallbackPolicyTest {
    @Test
    fun acceptsExactHttpsCallbackWithAuthorizationCode() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/auth/callback?code=one-time-code",
        )

        assertEquals(AuthCallbackDecision.AuthorizationCode("one-time-code"), result)
    }

    @Test
    fun rejectsAnotherPathOnTheTrustedHost() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/not-auth?code=one-time-code",
        )

        assertEquals(ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedPath), result)
    }

    @Test
    fun rejectsUntrustedOrigin() {
        val result = AuthCallbackPolicy.evaluate(
            "https://attacker.example/auth/callback?code=one-time-code",
        )

        assertEquals(ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin), result)
    }

    @Test
    fun returnsProviderErrorWithoutTreatingItAsAnAuthorizationCode() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/auth/callback" +
                "?error=access_denied&error_description=User%20cancelled",
        )

        assertTrue(result is AuthCallbackDecision.ProviderError)
        result as AuthCallbackDecision.ProviderError
        assertEquals("access_denied", result.code)
        assertEquals("User cancelled", result.description)
    }

    @Test
    fun doesNotAcceptCodeFromFragmentInPkceFlow() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/auth/callback#code=not-a-query-code",
        )

        assertEquals(ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedFragment), result)
    }

    @Test
    fun ignoresNullAndBlankUris() {
        val missing = AuthCallbackDecision.Ignored.Reason.MissingUri
        assertEquals(ignored(missing), AuthCallbackPolicy.evaluate(null))
        assertEquals(ignored(missing), AuthCallbackPolicy.evaluate(""))
        assertEquals(ignored(missing), AuthCallbackPolicy.evaluate("   "))
    }

    @Test
    fun ignoresAnOverlongUriBeforeParsingIt() {
        val padded = "https://links.pocketpass.xyz/auth/callback?code=" + "a".repeat(4_096)

        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.MalformedUri),
            AuthCallbackPolicy.evaluate(padded),
        )
    }

    @Test
    fun ignoresGarbageThatIsNotAUriAtAll() {
        val result = AuthCallbackPolicy.evaluate("not a uri at all")

        assertTrue(result is AuthCallbackDecision.Ignored)
    }

    @Test
    fun rejectsPlainHttp() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin),
            AuthCallbackPolicy.evaluate("http://links.pocketpass.xyz/auth/callback?code=abc"),
        )
    }

    @Test
    fun acceptsAnUppercaseSchemeAndHost() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("abc"),
            AuthCallbackPolicy.evaluate("HTTPS://LINKS.POCKETPASS.XYZ/auth/callback?code=abc"),
        )
    }

    @Test
    fun rejectsAUriWithoutAScheme() {
        val result = AuthCallbackPolicy.evaluate("//links.pocketpass.xyz/auth/callback?code=abc")

        assertTrue(result is AuthCallbackDecision.Ignored)
    }

    @Test
    fun rejectsAnEmptyAuthority() {
        val result = AuthCallbackPolicy.evaluate("https:///auth/callback?code=abc")

        assertTrue(result is AuthCallbackDecision.Ignored)
    }

    @Test
    fun rejectsUserInfoSmuggledIntoTheAuthority() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin),
            AuthCallbackPolicy.evaluate(
                "https://attacker.example@links.pocketpass.xyz/auth/callback?code=abc",
            ),
        )
    }

    @Test
    fun acceptsTheExplicitDefaultPortButNoOther() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("abc"),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz:443/auth/callback?code=abc"),
        )
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz:8443/auth/callback?code=abc"),
        )
    }

    @Test
    fun rejectsATrailingSlashOnTheCallbackPath() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedPath),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz/auth/callback/?code=abc"),
        )
    }

    @Test
    fun rejectsDotSegmentsThatWouldNormaliseOntoTheCallbackPath() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedPath),
            AuthCallbackPolicy.evaluate(
                "https://links.pocketpass.xyz/auth/callback/../auth/callback?code=abc",
            ),
        )
    }

    @Test
    fun rejectsAPercentEncodedCallbackPath() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/%61uth/callback?code=abc",
        )

        assertEquals(ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedPath), result)
    }

    @Test
    fun rejectsAnEmptyFragment() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedFragment),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz/auth/callback?code=abc#"),
        )
    }

    @Test
    fun rejectsAHostSmuggledIntoTheFragment() {
        val result = AuthCallbackPolicy.evaluate(
            "https://attacker.example#@links.pocketpass.xyz/auth/callback?code=abc",
        )

        assertTrue(result is AuthCallbackDecision.Ignored)
    }

    @Test
    fun rejectsABackslashSmuggledIntoTheAuthority() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz\\@attacker.example/auth/callback?code=abc",
        )

        assertTrue(result is AuthCallbackDecision.Ignored)
    }

    @Test
    fun reportsMissingResultWhenThereIsNoQuery() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.MissingResult),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz/auth/callback"),
        )
    }

    @Test
    fun reportsMissingResultForABlankCode() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.MissingResult),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz/auth/callback?code="),
        )
    }

    @Test
    fun takesTheFirstValueOfARepeatedCode() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("first"),
            AuthCallbackPolicy.evaluate(
                "https://links.pocketpass.xyz/auth/callback?code=first&code=second",
            ),
        )
    }

    @Test
    fun decodesAPercentEncodedCode() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("a b+c"),
            AuthCallbackPolicy.evaluate(
                "https://links.pocketpass.xyz/auth/callback?code=a%20b%2Bc",
            ),
        )
    }

    @Test
    fun treatsAPlusInTheQueryAsASpace() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("a b"),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz/auth/callback?code=a+b"),
        )
    }

    @Test
    fun dropsAParameterWithAMalformedPercentEscape() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.MissingResult),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz/auth/callback?code=%zz"),
        )
    }

    @Test
    fun stillAcceptsACodeAlongsideAMalformedParameter() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("good"),
            AuthCallbackPolicy.evaluate(
                "https://links.pocketpass.xyz/auth/callback?code=good&state=%zz",
            ),
        )
    }

    @Test
    fun ignoresAMalformedEscapeInThePath() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.MalformedUri),
            AuthCallbackPolicy.evaluate("https://links.pocketpass.xyz/%zz/callback?code=abc"),
        )
    }

    @Test
    fun reportsAProviderErrorWithoutADescription() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/auth/callback?error=server_error",
        )

        assertEquals(AuthCallbackDecision.ProviderError("server_error", null), result)
    }

    @Test
    fun prefersAProviderErrorOverACodeInTheSameQuery() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/auth/callback?code=abc&error=access_denied",
        )

        assertEquals(AuthCallbackDecision.ProviderError("access_denied", null), result)
    }

    @Test
    fun truncatesAnOverlongProviderError() {
        val result = AuthCallbackPolicy.evaluate(
            "https://links.pocketpass.xyz/auth/callback" +
                "?error=" + "e".repeat(400) + "&error_description=" + "d".repeat(600),
        )

        assertTrue(result is AuthCallbackDecision.ProviderError)
        result as AuthCallbackDecision.ProviderError
        assertEquals(100, result.code.length)
        assertEquals(300, result.description?.length)
    }

    @Test
    fun acceptsTheMobileSchemeCallbackWithAnAuthorizationCode() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("one-time-code"),
            AuthCallbackPolicy.evaluate("pocketpass://auth/callback?code=one-time-code"),
        )
    }

    @Test
    fun acceptsAnUppercaseMobileScheme() {
        assertEquals(
            AuthCallbackDecision.AuthorizationCode("abc"),
            AuthCallbackPolicy.evaluate("POCKETPASS://auth/callback?code=abc"),
        )
    }

    @Test
    fun reportsAProviderErrorOnTheMobileSchemeCallback() {
        assertEquals(
            AuthCallbackDecision.ProviderError("access_denied", null),
            AuthCallbackPolicy.evaluate("pocketpass://auth/callback?error=access_denied"),
        )
    }

    @Test
    fun rejectsAnotherPathOnTheMobileScheme() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin),
            AuthCallbackPolicy.evaluate("pocketpass://auth/other?code=abc"),
        )
    }

    @Test
    fun rejectsATrailingSlashOnTheMobileSchemeCallback() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin),
            AuthCallbackPolicy.evaluate("pocketpass://auth/callback/?code=abc"),
        )
    }

    @Test
    fun rejectsUserInfoSmuggledIntoTheMobileSchemeAuthority() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedOrigin),
            AuthCallbackPolicy.evaluate("pocketpass://attacker@auth/callback?code=abc"),
        )
    }

    @Test
    fun rejectsAFragmentOnTheMobileSchemeCallback() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.UnexpectedFragment),
            AuthCallbackPolicy.evaluate("pocketpass://auth/callback?code=abc#extra"),
        )
    }

    @Test
    fun reportsMissingResultForAMobileSchemeCallbackWithoutAQuery() {
        assertEquals(
            ignored(AuthCallbackDecision.Ignored.Reason.MissingResult),
            AuthCallbackPolicy.evaluate("pocketpass://auth/callback"),
        )
    }

    @Test
    fun keepsTheCodeOutOfToString() {
        val rendered = AuthCallbackDecision.AuthorizationCode("super-secret").toString()

        assertTrue(rendered.contains("redacted"))
        assertTrue(!rendered.contains("super-secret"))
    }

    private fun ignored(reason: AuthCallbackDecision.Ignored.Reason) =
        AuthCallbackDecision.Ignored(reason)
}
