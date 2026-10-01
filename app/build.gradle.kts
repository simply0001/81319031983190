import java.util.Properties
import com.google.gms.googleservices.GoogleServicesTask
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val firebaseConfigurationFile = providers.gradleProperty("POCKETPASS_GOOGLE_SERVICES_JSON")
    .orNull?.let(rootProject::file)
    ?: rootProject.file("../PocketPass-backups/backend/google-services.json").takeIf { it.isFile }
    ?: file("google-services.json")
val firebaseConfigured = firebaseConfigurationFile.isFile
check(providers.gradleProperty("POCKETPASS_REQUIRE_FIREBASE").orNull != "true" || firebaseConfigured) {
    "Firebase configuration is missing. Set POCKETPASS_GOOGLE_SERVICES_JSON or place google-services.json in PocketPass-backups/backend or app/."
}
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    androidComponents.onVariants { variant ->
        val taskName = "process${variant.name.replaceFirstChar { it.uppercaseChar() }}GoogleServices"
        tasks.named<GoogleServicesTask>(taskName).configure {
            googleServicesJsonFiles.set(listOf(firebaseConfigurationFile))
        }
    }
}

abstract class MinifyMiiRendererTask : DefaultTask() {
    @get:InputFiles
    abstract val bundles: ConfigurableFileCollection

    @get:Input
    abstract val bunExecutable: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun minify() {
        val target = outputDir.get().asFile
        target.deleteRecursively()
        bundles.files.forEach { bundle ->
            val output = target.resolve("mii_renderer/dist/${bundle.name}")
            output.parentFile.mkdirs()
            execOperations.exec {
                commandLine(
                    bunExecutable.get(),
                    "build",
                    bundle.absolutePath,
                    "--target",
                    "browser",
                    "--minify",
                    "--outfile",
                    output.absolutePath,
                )
            }
            check(output.isFile && output.length() > 0) {
                "Minified Mii renderer bundle was not produced: ${bundle.name}"
            }
        }
    }
}

fun resolveBunExecutable(project: Project): String {
    (project.findProperty("pocketpass.bun") as String?)
        ?.takeIf { file(it).isFile }
        ?.let { return it }
    val home = System.getProperty("user.home")
    return listOf(
        "$home/.bun/bun-windows-x64/bun.exe",
        "$home/.bun/bin/bun.exe",
        "$home/.bun/bin/bun",
    ).firstOrNull { file(it).isFile } ?: "bun"
}

System.getenv("LOCALAPPDATA")?.let { localAppData ->
    layout.buildDirectory.set(file("$localAppData/PocketPass/gradle/app"))
}

val pocketPassSigningPropertiesFile =
    file("${System.getProperty("user.home")}/.pocketpass/signing/signing.properties")
val pocketPassSigningProperties = Properties().apply {
    if (pocketPassSigningPropertiesFile.isFile) {
        pocketPassSigningPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.pocketpass.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pocketpass.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 28
        versionName = "0.2.2-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val supabaseUrl = providers.gradleProperty("POCKETPASS_SUPABASE_URL")
            .orElse("https://api.pocketpass.xyz")
            .get()
        val supabasePublishableKey = providers
            .gradleProperty("POCKETPASS_SUPABASE_PUBLISHABLE_KEY")
            .orElse("")
            .get()
        val backendEnabled = providers.gradleProperty("POCKETPASS_BACKEND_ENABLED")
            .orElse("false")
            .get()
            .toBooleanStrictOrNull()
            ?: false

        val releaseCertificateSha256 = pocketPassSigningProperties
            .getProperty("releaseCertSha256")
            .orEmpty()

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        buildConfigField("String", "RELEASE_CERT_SHA256", "\"$releaseCertificateSha256\"")
        buildConfigField("String", "AUTH_CALLBACK_URL", "\"https://links.pocketpass.xyz/auth/callback\"")
        buildConfigField("boolean", "BACKEND_ENABLED", backendEnabled.toString())
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        manifestPlaceholders["pocketPassLinkHost"] = "links.pocketpass.xyz"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (pocketPassSigningPropertiesFile.isFile) {
            create("pocketPassRelease") {
                storeFile = file(pocketPassSigningProperties.getProperty("storeFile"))
                storePassword = pocketPassSigningProperties.getProperty("storePassword")
                keyAlias = pocketPassSigningProperties.getProperty("keyAlias")
                keyPassword = pocketPassSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (pocketPassSigningPropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("pocketPassRelease")
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    androidResources {
        noCompress += listOf("dat", "glb", "wasm", "zip")
    }
}

androidComponents {
    val readableRenderer = providers
        .gradleProperty("pocketpass.readableRenderer")
        .orNull == "true"
    onVariants(
        selector().withBuildType("release"),
    ) { variant ->
        if (readableRenderer) return@onVariants
        val minifyRenderer = tasks.register<MinifyMiiRendererTask>(
            "minify${variant.name.replaceFirstChar(Char::uppercase)}MiiRenderer",
        ) {
            bundles.from(
                file("src/main/assets/mii_renderer/dist/renderer.js"),
                file("src/main/assets/mii_renderer/dist/three.js"),
            )
            bunExecutable.set(resolveBunExecutable(project))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            minifyRenderer,
            MinifyMiiRendererTask::outputDir,
        )
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    constraints {
        implementation(libs.androidx.fragment) {
            because("Firebase's transitive Fragment version must support Activity Result permissions")
        }
    }
    implementation(project(":shared"))
    implementation(project(":ui"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.glance.appwidget)

    val supabaseBom = platform(libs.supabase.bom)
    implementation(supabaseBom)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
