import org.gradle.api.NamedDomainObjectProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

fun NamedDomainObjectProvider<KotlinSourceSet>.dependencies(
    handler: KotlinDependencyHandler.() -> Unit,
): Unit = configure { dependencies(handler) }

fun KotlinMultiplatformExtension.iosTargets(
    deploymentTarget: String = "14.1" // Default iOS deployment target
) {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = project.name // Or a custom name
            // Export dependencies for the framework
            // export(libs.kotlinx.coroutines.core) // Example
        }
        target.compilations.all {
            kotlinOptions.freeCompilerArgs += listOf(
                "-Xlinker-option", "-framework", "-framework Foundation", // Link Foundation framework
                "-XobjCNameChars", "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_", // Allow more chars in ObjC names
                "-Xexport-kdoc" // Export KDoc comments to generated Objective-C headers
            )
            // Set iOS deployment target
            compilationOptions.configure {
                 freeCompilerArgs.addAll("-target", "ios$deploymentTarget")
            }
        }
    }
}
