import UIKit
import WidgetKit
import PocketPassUi

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private let messageNotifications = MessageNotifications()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        messageNotifications.configure()
        // Registers the BGTaskScheduler background-refresh handler; must run
        // before this method returns.
        PhoneEntryKt.PhoneAppDidLaunch()
        // WidgetKit is Swift-only, so Kotlin hands widget refreshes back here.
        PhoneEntryKt.PhoneAppSetWidgetReloader {
            WidgetCenter.shared.reloadAllTimelines()
            return KotlinUnit()
        }
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = PhoneEntryKt.PhoneAppViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        messageNotifications.becameActive()
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        messageNotifications.registered(deviceToken: deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        messageNotifications.registrationFailed()
    }

    // The pocketpass:// scheme carries the OAuth sign-in callback.
    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        PhoneEntryKt.PhoneAppHandleUrl(url: url.absoluteString)
        return true
    }
}
