package com.pocketpass.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotSame
import org.junit.Test

class SignOutCleanupTest {
    @Test
    fun databaseCleanupLeavesTheCallingThread() = runTest {
        val caller = Thread.currentThread()
        var cleanupThread: Thread? = null

        clearSignOutData {
            cleanupThread = Thread.currentThread()
        }

        assertNotSame(caller, cleanupThread)
    }
}
