import org.gradle.api.artifacts.VersionCatalogsExtension

val datastoreVersion = libs.versions.androidx.datastore.get()
val libVersion = libs.versions.encrypted.datastore.get()
val libsCatalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

tasks.wrapper {
    gradleVersion = libsCatalog.findVersion("gradle").get().requiredVersion
}

allprojects {
    group = "com.dayanruben"
    version = "$datastoreVersion-$libVersion"
}
