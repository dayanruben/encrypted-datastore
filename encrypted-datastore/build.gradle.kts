import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    convention.library.kmp
    convention.publish
    convention.testFixtures
}

description = "Extensions to encrypt DataStore using Tink"

kotlin.sourceSets {
    commonJvmMain.dependencies {
        api(kotlin("stdlib", version = libs.versions.kotlin.get()))
        api(libs.androidx.datastore)
        compileOnly(libs.tink)
    }
    jvmMain.dependencies {
        api(libs.tink)
    }
    androidMain.dependencies {
        api(libs.tink.android)
    }
}
