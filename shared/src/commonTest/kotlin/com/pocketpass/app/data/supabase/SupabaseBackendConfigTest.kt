package com.pocketpass.app.data.supabase

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SupabaseBackendConfigTest {
    @Test
    fun normalizesOnlyTrailingBaseUrlSlash() {
        val config = SupabaseBackendConfig(
            baseUrl = "https://api.pocketpass.xyz/",
            publishableKey = "sb_publishable_example",
        )

        assertEquals("https://api.pocketpass.xyz", config.normalizedBaseUrl)
    }

    @Test
    fun rejectsNonHttpsBaseUrl() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = "http://api.pocketpass.xyz",
                publishableKey = "sb_publishable_example",
            )
        }
    }

    @Test
    fun rejectsCallbackOnWrongPath() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
                publishableKey = "sb_publishable_example",
                authCallbackUrl = "https://links.pocketpass.xyz/wrong",
            )
        }
    }

    @Test
    fun acceptsTheExactMobileSchemeCallback() {
        val config = SupabaseBackendConfig(
            baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
            publishableKey = "sb_publishable_example",
            authCallbackUrl = SupabaseBackendConfig.MOBILE_AUTH_CALLBACK_URL,
        )

        assertEquals("pocketpass://auth/callback", config.authCallbackUrl)
    }

    @Test
    fun rejectsAnyOtherMobileSchemeCallback() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
                publishableKey = "sb_publishable_example",
                authCallbackUrl = "pocketpass://auth/callback/extra",
            )
        }
    }

    @Test
    fun rejectsObviousServiceRoleKey() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
                publishableKey = "service_role_do_not_ship",
            )
        }
    }

    @Test
    fun rejectsLegacyServiceRoleJwtWithoutLiteralKeyLabel() {
        val header = encodeUrl("""{"alg":"HS256","typ":"JWT"}""")
        val payload = encodeUrl("""{"role":"service_role","iss":"supabase"}""")

        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
                publishableKey = "$header.$payload.fake-signature",
            )
        }
    }

    @Test
    fun rejectsBaseUrlWithUserInfo() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = "https://attacker@api.pocketpass.xyz",
                publishableKey = "sb_publishable_example",
            )
        }
    }

    @Test
    fun rejectsBaseUrlWithFragment() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = "https://api.pocketpass.xyz#fragment",
                publishableKey = "sb_publishable_example",
            )
        }
    }

    @Test
    fun rejectsBaseUrlWithQuery() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = "https://api.pocketpass.xyz?apikey=leak",
                publishableKey = "sb_publishable_example",
            )
        }
    }

    @Test
    fun rejectsBaseUrlWithPath() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = "https://api.pocketpass.xyz/rest/v1",
                publishableKey = "sb_publishable_example",
            )
        }
    }

    @Test
    fun rejectsBaseUrlWithoutHost() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = "https:///nowhere",
                publishableKey = "sb_publishable_example",
            )
        }
    }

    @Test
    fun rejectsMalformedBaseUrl() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = "not a url at all",
                publishableKey = "sb_publishable_example",
            )
        }
    }

    @Test
    fun rejectsCallbackOnWrongHost() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
                publishableKey = "sb_publishable_example",
                authCallbackUrl = "https://evil.example.com/auth/callback",
            )
        }
    }

    @Test
    fun rejectsCallbackWithQueryOrFragment() {
        listOf(
            "https://links.pocketpass.xyz/auth/callback?next=evil",
            "https://links.pocketpass.xyz/auth/callback#token",
        ).forEach { callback ->
            assertFailsWith<IllegalArgumentException> {
                SupabaseBackendConfig(
                    baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
                    publishableKey = "sb_publishable_example",
                    authCallbackUrl = callback,
                )
            }
        }
    }

    @Test
    fun rejectsBlankPublishableKey() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseBackendConfig(
                baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
                publishableKey = "   ",
            )
        }
    }

    @Test
    fun acceptsAValidConfiguration() {
        val config = SupabaseBackendConfig(
            baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
            publishableKey = "sb_publishable_example",
        )

        assertEquals(SupabaseBackendConfig.DEFAULT_BASE_URL, config.normalizedBaseUrl)
        assertEquals(
            SupabaseBackendConfig.DEFAULT_AUTH_CALLBACK_URL,
            config.authCallbackUrl,
        )
    }

    @Test
    fun redactsThePublishableKeyFromToString() {
        val rendered = SupabaseBackendConfig(
            baseUrl = SupabaseBackendConfig.DEFAULT_BASE_URL,
            publishableKey = "sb_publishable_secret_value",
        ).toString()

        assertFalse(rendered.contains("sb_publishable_secret_value"))
    }

    private fun encodeUrl(value: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(value.encodeToByteArray())
}
