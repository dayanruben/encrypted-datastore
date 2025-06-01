plugins {
    convention.library.kmp
    convention.publish
    // convention.testFixtures // TODO: Re-evaluate test fixtures strategy for KMP
}

description = "Extensions to encrypt DataStore using Tink"

android {
    namespace = "$group.datastore.encrypted"
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // api(kotlin("stdlib")) // Should come from convention.library.kmp or applyKotlinDefaults
                implementation(libs.kotlinx.coroutines.core) // Already in convention.library.kmp
                // Define API dependencies for expect declarations
                // api(libs.androidx.datastore.core) // This will be an expect/actual
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.androidx.datastore.core) // Actual for Android
                api(libs.tink.android)
            }
        }
        // iosMain will be configured by convention.library.kmp
        // No explicit dependencies here for Apple's CryptoKit/CommonCrypto yet
        // as they are system frameworks.

        // Removing jvmMain specific dependencies for now
        // val jvmMain by getting {
        //     dependencies {
        //         api(libs.tink)
        //     }
        // }
    }
}
