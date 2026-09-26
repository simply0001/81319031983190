package com.pocketpass.app.domain.repository

interface PuzzleArtworkStore {
    fun pathFor(key: String): String?

    suspend fun write(key: String, bytes: ByteArray): String?
}

object NoPuzzleArtworkStore : PuzzleArtworkStore {
    override fun pathFor(key: String): String? = null

    override suspend fun write(key: String, bytes: ByteArray): String? = null
}
