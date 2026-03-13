import internal.libs
import internal.catalogVersion
import defaultKmpAndroidNamespace
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.kotlin.dsl.configure

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

extensions.configure<KotlinMultiplatformExtension>("kotlin") {
    jvm()
    android {
        namespace = project.defaultKmpAndroidNamespace()
        compileSdk = project.catalogVersion("compile-sdk").toInt()
        minSdk = project.catalogVersion("min-sdk").toInt()
        withHostTestBuilder {}

        lint {
            checkDependencies = true
            abortOnError = true
            warningsAsErrors = true
        }
    }

    sourceSets {
        val commonJvmMain by creating {
            dependsOn(commonMain.get())
        }
        val commonJvmTest by creating {
            dependsOn(commonTest.get())
        }

        named("androidMain") {
            dependsOn(commonJvmMain)
        }
        named("androidHostTest") {
            dependsOn(commonJvmTest)
        }
        named("jvmMain") {
            dependsOn(commonJvmMain)
        }
        commonMain.dependencies {
            api(project.dependencies.platform(project(":encrypted-datastore-bom")))
        }
        named("jvmTest") {
            dependsOn(commonJvmTest)
        }
        commonJvmTest.dependencies {
            implementation(kotlin("test", version = libs.versions.kotlin.get()))
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter)
        }
    }
}

applyKotlinDefaults()

