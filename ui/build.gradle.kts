plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

System.getenv("LOCALAPPDATA")?.let { localAppData ->
    layout.buildDirectory.set(file("$localAppData/PocketPass/gradle/ui"))
}

kotlin {
    android {
        namespace = "com.pocketpass.ui"
        compileSdk = 37
        minSdk = 30

        androidResources.enable = true

        withHostTestBuilder {}.configure {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PocketPassUi"
            isStatic = true
            export(project(":shared"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.components.resources)
            implementation(libs.jb.compose.material.icons.extended)
            implementation(libs.coil.compose)
            implementation(libs.coil.svg)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.pocketpass.ui.resources"
    generateResClass = always
}
