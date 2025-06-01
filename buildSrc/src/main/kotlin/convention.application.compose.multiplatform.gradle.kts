import internal.libs
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension

plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_1_8.toString()
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.activity.compose) // For Android Activity integration
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.core) // androidx.core:core-ktx
                implementation(libs.lifecycle.runtime)
                implementation(libs.lifecycle.viewmodel.compose)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            // Add specific iOS dependencies here if needed
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
    jvmToolchain(11) // Ensure consistent JVM toolchain
}

android {
    namespace = "com.example.app" // TODO: Replace with your app's namespace
    compileSdk = Defaults.COMPILE_SDK
    defaultConfig {
        minSdk = Defaults.MIN_SDK
        targetSdk = Defaults.TARGET_SDK
        applicationId = "com.example.app" // TODO: Replace with your app's applicationId
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

// Optional: Configure XCFramework for iOS
// val xcf = XCFramework()
// kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
//     binaries.framework {
//         baseName = "YourFrameworkName" // TODO: Replace with your framework name
//         xcf.add(this)
//     }
// }

// Helper to configure Kotlin Multiplatform Extension
fun Project.kotlin(configure: KotlinMultiplatformExtension.() -> Unit): Unit =
    extensions.configure(KotlinMultiplatformExtension::class.java, configure)

// Helper to configure Android Application Extension
fun Project.android(configure: BaseAppModuleExtension.() -> Unit): Unit =
    extensions.configure(BaseAppModuleExtension::class.java, configure)
