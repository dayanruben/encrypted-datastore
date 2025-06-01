plugins {
    convention.library.kmp
    convention.publish
}

description = "Extensions to encrypt DataStore Preferences using Tink"

android {
    namespace = "$group.datastore.encrypted.preferences"
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.encryptedDatastore)
                api(libs.androidx.datastore.core) // Add common DataStore interface
                // implementation(libs.kotlinx.coroutines.core) // Should come from convention.library.kmp
                // API for expect declarations related to preference storage will be added here
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.androidx.datastore.preferences)
            }
        }
        val androidUnitTest by getting { // Changed from commonTest to androidUnitTest for testFixtures
            dependencies {
                implementation(projects.encryptedDatastore.testFixtures)
            }
        }
        // iosMain will be configured by convention.library.kmp
        // No explicit dependencies here for NSUserDefaults or file system access yet.

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test")) // Explicitly add kotlin-test
                implementation(projects.encryptedDatastore) // For ExpectedAead, PlatformSerializer etc.
            }
        }
    }
}

// FriendPaths configuration removed for now.
// If internal access to androidx.datastore.preferences.core is still needed for androidMain,
// it might require a more targeted approach or reconsidering the commonMain logic.
// tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    if (name.contains("android", ignoreCase = true)) { // Apply only to Android compilation tasks
//        // The way to get datastore-preferences-core might need adjustment
//        // val datastoreLibrary = configurations.getByName("androidMainImplementation")
//        //     .resolvedConfiguration.resolvedArtifacts.find {
//        //         it.moduleVersion.id.name == "datastore-preferences-core"
//        //     }?.file
//        // if (datastoreLibrary != null) {
//        //     compilerOptions.options.get().friendPaths.from(datastoreLibrary)
//        // }
//    }
// }
