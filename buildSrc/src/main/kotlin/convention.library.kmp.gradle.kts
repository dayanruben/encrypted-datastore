import internal.libs
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release") // Configure variant for publishing
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Remove JVM target if not explicitly needed for this library type alongside Android
    // targets.removeIf { it.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate() // Using default hierarchy

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project.dependencies.platform(project(":encrypted-datastore-bom")))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting // Alias for android target
        val iosMain by creating { // Create iosMain source set
            dependsOn(commonMain)
            // Add specific iOS dependencies here if needed
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        valandroidTest by getting { // Alias for android test
            dependencies {
                implementation(libs.junit.jupiter) // Keep JUnit for Android tests
            }
        }
        val iosTest by creating { // Create iosTest source set
            dependsOn(commonTest)
            // Add specific iOS test dependencies here if needed
        }
        val iosX64Test by getting { dependsOn(iosTest) }
        val iosArm64Test by getting { dependsOn(iosTest) }
        val iosSimulatorArm64Test by getting { dependsOn(iosTest) }
    }
}

applyKotlinDefaults()
android.applyAndroidDefaults()

// Optional: Configure XCFramework for iOS
// val xcf = XCFramework()
// kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
//     binaries.framework {
//         baseName = "YourFrameworkName" // TODO: Replace with your framework name
//         xcf.add(this)
//     }
// }
