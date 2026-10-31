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
    iosArm64()
    iosSimulatorArm64()
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
        val appleMain = create("appleMain") { dependsOn(commonMain.get()) }
        val appleTest = create("appleTest") { dependsOn(commonTest.get()) }
        for (target in listOf("iosArm64", "iosSimulatorArm64", "macosArm64")) {
            named("${target}Main") { dependsOn(appleMain) }
            named("${target}Test") { dependsOn(appleTest) }
        }
    }
}
