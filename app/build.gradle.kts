import java.util.Properties
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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

// Windows only: elsewhere the default keeps build output inside the workspace, where CI finds it.
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pocketpass.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 14
        versionName = "0.1.1-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
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
    implementation(project(":shared"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.webkit)
    ksp(libs.androidx.room.compiler)

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
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
