-keep class com.pocketpass.app.sync.PocketPassOutboxWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers enum com.pocketpass.app.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.pocketpass.app.data.local.** { *; }

-keepclassmembers class com.pocketpass.app.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.pocketpass.app.**$$serializer { *; }
-keep class com.pocketpass.app.model.PocketPassRoute { *; }
-keep class com.pocketpass.app.model.PocketPassRoute$* { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}
