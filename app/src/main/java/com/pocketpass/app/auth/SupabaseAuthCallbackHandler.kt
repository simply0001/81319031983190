package com.pocketpass.app.auth

import android.content.Intent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks

class SupabaseAuthCallbackHandler(
    private val client: SupabaseClient,
) {
    fun handle(
        intent: Intent,
        onResult: (AuthCallbackResult) -> Unit,
    ): AuthCallbackDispatch =
        when (val decision = AuthCallbackPolicy.evaluate(intent.dataString)) {
            is AuthCallbackDecision.AuthorizationCode -> {
                client.handleDeeplinks(
                    intent = intent,
                    onSessionSuccess = { onResult(AuthCallbackResult.Authenticated(it)) },
                    onError = { onResult(AuthCallbackResult.Failed(it)) },
                )
                AuthCallbackDispatch.Dispatched
            }

            is AuthCallbackDecision.ProviderError -> {
                onResult(
                    AuthCallbackResult.Failed(
                        AuthProviderCallbackException(
                            providerError = decision.code,
                            providerDescription = decision.description,
                        ),
                    ),
                )
                AuthCallbackDispatch.Dispatched
            }

            is AuthCallbackDecision.Ignored -> AuthCallbackDispatch.Ignored(decision.reason)
        }
}
