import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)

    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withIos()
            }
        }
    }

    jvm("desktop")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Probe"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.storage)
            implementation(libs.coil.compose)
            implementation(libs.coil.svg)
            implementation(libs.coil.network.ktor)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.get().dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.pocketpass.spike.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.pocketpass.spike.DesktopProbeKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "PocketPassProbe"
            packageVersion = "1.0.0"
        }
    }
}
