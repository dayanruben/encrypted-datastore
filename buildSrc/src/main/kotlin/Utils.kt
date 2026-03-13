import org.gradle.api.Project
import org.gradle.api.NamedDomainObjectProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

fun NamedDomainObjectProvider<KotlinSourceSet>.dependencies(
    handler: KotlinDependencyHandler.() -> Unit,
): Unit = configure { dependencies(handler) }

fun Project.defaultKmpAndroidNamespace(): String = when (path) {
    ":encrypted-datastore" -> "$group.datastore.encrypted"
    ":encrypted-datastore-preferences" -> "$group.datastore.encrypted.preferences"
    ":encrypted-datastore:testFixtures" -> "$group.datastore.encrypted.testFixtures"
    else -> error("Please configure the Android namespace for KMP project '$path'")
}
