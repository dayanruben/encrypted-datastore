import com.android.build.gradle.internal.lint.*
import defaultKmpAndroidNamespace
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("convention.library.kmp") apply false
}

plugins.withId("convention.library.kmp") {
    val testFixturesProject = project("testFixtures")
    val thisProject = project

    with(testFixturesProject) {
        plugins.apply("convention.library.kmp")

        // Disable most of the Lint tasks for the test fixtures project
        tasks.withType<AndroidLintAnalysisTask>().configureEach { enabled = false }
        tasks.withType<AndroidLintTask>().configureEach { enabled = false }
        tasks.withType<AndroidLintTextOutputTask>().configureEach { enabled = false }
        tasks.withType<LintModelWriterTask>().configureEach { enabled = false }

        extensions.configure<KotlinMultiplatformExtension>("kotlin") {
            explicitApi = null

            sourceSets {
                commonMain.dependencies {
                    implementation(thisProject)
                }
            }
        }
    }

    extensions.configure<KotlinMultiplatformExtension>("kotlin") {
        sourceSets {
            commonTest.dependencies {
                implementation(testFixturesProject)
            }
        }
    }
}
