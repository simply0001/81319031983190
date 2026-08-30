package com.pocketpass.app.nearby

import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyCredentialPoolTest {
    @Test
    fun offlineRefillUsesRemainingCachedCredentials() {
        val failure = RepositoryResult.Failure(
            RepositoryFailure(
                kind = RepositoryFailureKind.Offline,
                message = "Offline",
            ),
        )

        assertTrue(cachedCredentialFallback(1, failure) is RepositoryResult.Success)
        assertSame(failure, cachedCredentialFallback(0, failure))
    }
}
