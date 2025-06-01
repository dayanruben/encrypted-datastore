plugins {
    convention.library.kmp // Changed from convention.library.android
}

description = "KMP Security Crypto Extensions (with DataStore support)" // Updated description

android {
    namespace = "$group.security.crypto.datastore.common" // Updated namespace
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.encryptedDatastore)
                implementation(libs.kotlinx.coroutines.core) // Explicitly add if not guaranteed by convention
                api(libs.okio) // For okio.Path, if used for file paths
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.androidx.datastore.core) // For DataStoreFactory, if used directly
                api(libs.androidx.security.crypto)
                // Okio is already in commonMain, but Android specific parts might be needed if any
            }
        }
        // iosMain will be configured by convention.library.kmp
        // No explicit Gradle deps for Apple frameworks yet.
    }
}

// Dependencies block outside kotlin.sourceSets is for platform-agnostic (e.g. Java library) or all-source-sets dependencies.
// For KMP, dependencies are typically specified inside sourceSets.
// The original dependencies block is removed/merged into kotlin.sourceSets.
