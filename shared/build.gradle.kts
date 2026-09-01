plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Windows only: elsewhere the default keeps build output inside the workspace, where CI finds it.
System.getenv("LOCALAPPDATA")?.let { localAppData ->
    layout.buildDirectory.set(file("$localAppData/PocketPass/gradle/shared"))
}

// iOS has no BuildConfig, so the backend coordinates arrive as generated code.
// Locally they come from ~/.gradle/gradle.properties (same POCKETPASS_* names
// the Android app uses); on CI from environment variables. No key = fixture mode.
val generateIosBuildConfig = tasks.register("generateIosBuildConfig") {
    val supabaseUrl = providers.gradleProperty("POCKETPASS_SUPABASE_URL")
        .orElse(providers.environmentVariable("POCKETPASS_SUPABASE_URL"))
        .orElse("https://api.pocketpass.xyz")
    val publishableKey = providers.gradleProperty("POCKETPASS_SUPABASE_PUBLISHABLE_KEY")
        .orElse(providers.environmentVariable("POCKETPASS_SUPABASE_PUBLISHABLE_KEY"))
        .orElse("")
    val backendEnabled = providers.gradleProperty("POCKETPASS_BACKEND_ENABLED")
        .orElse(providers.environmentVariable("POCKETPASS_BACKEND_ENABLED"))
        .orElse("false")
    val outputDirectory = layout.buildDirectory.dir("generated/pocketpass/iosMain")
    inputs.property("supabaseUrl", supabaseUrl)
    inputs.property("publishableKey", publishableKey)
    inputs.property("backendEnabled", backendEnabled)
    outputs.dir(outputDirectory)
    doLast {
        fun quoted(value: String) = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
        val file = outputDirectory.get().file("com/pocketpass/app/IosBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.pocketpass.app
            |
            |object IosBuildConfig {
            |    const val SUPABASE_URL = "${quoted(supabaseUrl.get())}"
            |    const val SUPABASE_PUBLISHABLE_KEY = "${quoted(publishableKey.get())}"
            |    const val BACKEND_ENABLED = ${backendEnabled.get().trim() == "true"}
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "com.pocketpass.shared"
        compileSdk = 37
        minSdk = 30

        withHostTestBuilder {}.configure {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PocketPassShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            api(project.dependencies.platform(libs.supabase.bom))
            api(libs.supabase.auth)
            api(libs.supabase.postgrest)
            api(libs.supabase.realtime)
            api(libs.supabase.storage)
            api(libs.androidx.room.runtime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // NavKeyMarker is a typealias for navigation3's NavKey on Android, and NavKey is
            // thereby a supertype of every route, so consumers need it on their classpath too.
            api(libs.androidx.navigation3.runtime)
        }
        iosMain {
            kotlin.srcDir(generateIosBuildConfig)
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
