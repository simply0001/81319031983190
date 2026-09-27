import FirebaseCore
import FirebaseMessaging
import PocketPassUi
import UIKit
import UserNotifications

final class MessageNotifications: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {
    private var enabled = false
    private var permissionAllowed = false
    private var permissionRequestInFlight = false
    private var tokenRequestInFlight = false
    private var deletingToken = false
    private var generation = 0
    private var token = ""

    func configure() {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path),
              options.bundleID == Bundle.main.bundleIdentifier else { return }
        FirebaseApp.configure(options: options)
        Messaging.messaging().isAutoInitEnabled = false
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        PhoneEntryKt.PhoneAppSetMessagePushHandler { [weak self] command in
            self?.handle(command)
        }
    }

    private func handle(_ command: String) {
        switch command {
        case "enable":
            enabled = true
            refreshPermission()
        case "request":
            enabled = true
            refreshPermission(openSettingsWhenDenied: true)
        case "disable":
            enabled = false
            permissionAllowed = false
            generation += 1
            Messaging.messaging().isAutoInitEnabled = false
            clearMessages()
            publish(allowed: false)
        case "reset":
            let wasEnabled = enabled || !token.isEmpty
            enabled = false
            permissionAllowed = false
            generation += 1
            token = ""
            Messaging.messaging().isAutoInitEnabled = false
            UIApplication.shared.unregisterForRemoteNotifications()
            clearMessages()
            publish(allowed: false)
            if wasEnabled || !hasResetToken { resetToken() }
        case "clear": clearMessages()
        default: break
        }
    }

    private var hasResetToken = false

    private func resetToken() {
        guard !deletingToken else { return }
        deletingToken = true
        Messaging.messaging().deleteToken { [weak self] error in
            DispatchQueue.main.async {
                guard let self else { return }
                self.deletingToken = false
                self.hasResetToken = error == nil
                if self.enabled { self.refreshPermission() }
            }
        }
    }

    func becameActive() {
        if enabled { refreshPermission() }
    }

    private func refreshPermission(openSettingsWhenDenied: Bool = false) {
        guard enabled, !deletingToken else { return }
        let expectedGeneration = generation
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { [weak self] settings in
            DispatchQueue.main.async {
                guard let self, self.enabled, self.generation == expectedGeneration else { return }
                switch settings.authorizationStatus {
                case .notDetermined:
                    guard !self.permissionRequestInFlight else { return }
                    self.permissionRequestInFlight = true
                    center.requestAuthorization(options: [.alert, .sound]) { [weak self] _, _ in
                        DispatchQueue.main.async {
                            self?.permissionRequestInFlight = false
                            self?.refreshPermission()
                        }
                    }
                case .authorized, .provisional, .ephemeral:
                    self.permissionAllowed = true
                    Messaging.messaging().isAutoInitEnabled = true
                    UIApplication.shared.registerForRemoteNotifications()
                    if Messaging.messaging().apnsToken != nil { self.refreshToken() }
                case .denied:
                    self.permissionAllowed = false
                    self.generation += 1
                    Messaging.messaging().isAutoInitEnabled = false
                    self.clearMessages()
                    self.publish(allowed: false)
                    if openSettingsWhenDenied, let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                @unknown default:
                    self.permissionAllowed = false
                    self.generation += 1
                    self.publish(allowed: false)
                }
            }
        }
    }

    func registered(deviceToken: Data) {
        guard enabled, permissionAllowed else { return }
        Messaging.messaging().apnsToken = deviceToken
        refreshToken()
    }

    func registrationFailed() {
    }

    private func refreshToken() {
        guard enabled, permissionAllowed, !deletingToken, !tokenRequestInFlight else { return }
        tokenRequestInFlight = true
        let expectedGeneration = generation
        Messaging.messaging().token { [weak self] token, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.tokenRequestInFlight = false
                guard self.enabled, self.permissionAllowed else { return }
                guard self.generation == expectedGeneration else {
                    self.refreshPermission()
                    return
                }
                guard let token, !token.isEmpty else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 30) { [weak self] in
                        guard let self, self.enabled, self.generation == expectedGeneration else { return }
                        self.refreshPermission()
                    }
                    return
                }
                self.token = token
                self.hasResetToken = false
                self.publish(allowed: true)
            }
        }
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        DispatchQueue.main.async { [weak self] in
            self?.refreshPermission()
        }
    }

    private func publish(allowed: Bool) {
        PhoneEntryKt.PhoneAppMessagePushState(token: token, allowed: allowed)
    }

    private func clearMessages() {
        let center = UNUserNotificationCenter.current()
        center.getDeliveredNotifications { notifications in
            let ids = notifications.filter {
                $0.request.content.userInfo["type"] as? String == "message"
            }.map { $0.request.identifier }
            center.removeDeliveredNotifications(withIdentifiers: ids)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler(notification.request.content.userInfo["type"] as? String == "message"
            ? [] : [.banner, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else {
            completionHandler()
            return
        }
        let data = response.notification.request.content.userInfo.reduce(into: [String: String]()) { result, entry in
            if let key = entry.key as? String, let value = entry.value as? String { result[key] = value }
        }
        DispatchQueue.main.async {
            PhoneEntryKt.PhoneAppMessageNotificationTapped(data: data)
            completionHandler()
        }
    }
}
