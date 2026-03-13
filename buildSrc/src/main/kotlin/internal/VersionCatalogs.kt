package internal

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.the

internal val Project.libs: LibrariesForLibs
    get() = rootProject.the<LibrariesForLibs>()

internal val Project.libsCatalog: VersionCatalog
    get() = rootProject.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun Project.catalogVersion(name: String): String =
    libsCatalog.findVersion(name).orElseThrow { error("Missing version '$name' in libs.versions.toml") }.requiredVersion
