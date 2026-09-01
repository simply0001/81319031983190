import UIKit
import WidgetKit
import PocketPassUi

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Registers the BGTaskScheduler background-refresh handler; must run
        // before this method returns.
        PhoneEntryKt.PhoneAppDidLaunch()
        // WidgetKit is Swift-only, so Kotlin hands widget refreshes back here.
        PhoneEntryKt.PhoneAppSetWidgetReloader {
            WidgetCenter.shared.reloadAllTimelines()
        }
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = PhoneEntryKt.PhoneAppViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
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
