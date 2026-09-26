package com.pocketpass.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pocketpass.app.PocketPassApplication

class PocketPassMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        MessagePushRegistrationWorker.enqueue(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        (application as PocketPassApplication).container.messagePush.receive(message.data)
    }
}
