@file:OptIn(ExperimentalUuidApi::class)

package com.pocketpass.app.domain.model

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class FriendCode(val value: String) {
    init {
        require(value.matches(Regex("""\d{8}"""))) {
            "FriendCode must contain exactly eight digits"
        }
    }

    val formatted: String
        get() = "${value.take(4)} ${value.drop(4)}"

    override fun toString(): String = value

    companion object {
        fun parseOrNull(value: String): FriendCode? =
            value.filter(Char::isDigit)
                .takeIf { it.length == 8 }
                ?.let(::FriendCode)
    }
}

@JvmInline
value class NotificationId(val value: String) {
    init {
        require(value.isNotBlank()) { "NotificationId cannot be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ConversationId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConversationId cannot be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class MessageId(val value: String) {
    init {
        require(value.isNotBlank()) { "MessageId cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun new(): MessageId = MessageId(Uuid.random().toString())
    }
}

@JvmInline
value class ClientOperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "ClientOperationId cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun new(): ClientOperationId = ClientOperationId(Uuid.random().toString())
    }
}

@JvmInline
value class EncounterId(val value: String) {
    init {
        require(value.isNotBlank()) { "EncounterId cannot be blank" }
    }

    override fun toString(): String = value
}
