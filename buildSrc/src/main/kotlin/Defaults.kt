import com.android.build.api.dsl.LibraryExtension
import internal.catalogVersion
import internal.libs
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

internal fun Project.applyKotlinDefaults() {
    with(kotlinExtension) {
        jvmToolchain(catalogVersion("jvm-toolchain").toInt())
        explicitApi()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

internal fun LibraryExtension.applyAndroidDefaults(project: Project) {
    compileSdk = project.catalogVersion("compile-sdk").toInt()

    // Min SDK should be aligned with min SDK in androidx.security:security-crypto
    defaultConfig.minSdk = project.catalogVersion("min-sdk").toInt()

    buildFeatures {
        resValues = false
        shaders = false
    }

    androidResources {
        enable = false
    }

    lint {
        checkDependencies = true
        abortOnError = true
        warningsAsErrors = true
    }
}
