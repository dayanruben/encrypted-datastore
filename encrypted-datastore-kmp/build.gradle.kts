import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    convention.library.kmp
    convention.publish
    alias(libs.plugins.cryptography)
}

description = "Authenticated Kotlin Multiplatform DataStore for Android, JVM, iOS and macOS"

cryptography {
    configureSwiftLinkerOpts = true
}

kotlin {
    android {
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    iosArm64()
    iosSimulatorArm64 {
        binaries.withType<TestExecutable>().configureEach {
            // The simulator Security framework reads this test-only section (KT-61470).
            linkerOpts("-sectcreate", "__TEXT", "__entitlements",
                project.file("src/appleTest/resources/entitlements.plist").absolutePath)
        }
    }
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.datastore.core.okio)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
        commonJvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }
        named("androidDeviceTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.androidx.test.runner)
        }
        val appleMain = create("appleMain") { dependsOn(commonMain.get()) }
        val appleTest = create("appleTest") { dependsOn(commonTest.get()) }
        for (target in listOf("iosArm64", "iosSimulatorArm64", "macosArm64")) {
            named("${target}Main") { dependsOn(appleMain) }
            named("${target}Test") { dependsOn(appleTest) }
        }
    }
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    device.set(providers.gradleProperty("iosSimulatorDevice").orElse("booted"))
}
