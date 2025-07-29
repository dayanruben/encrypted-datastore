import com.redmadrobot.build.dsl.*
import java.io.ByteArrayOutputStream

fun Project.hasGpgKey(): Boolean {
    val result = ByteArrayOutputStream()
    exec {
        commandLine("gpg", "--list-secret-keys")
        standardOutput = result
        errorOutput = result
        isIgnoreExitValue = true
    }
    return result.toString().trim().isNotEmpty()
}

plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    signing
}

signing {
    if (hasGpgKey()) {
        useGpgCmd()
    } else {
        logger.lifecycle("GPG key not available - signing will be skipped")
    }
    isRequired = hasGpgKey()
}

mavenPublishing {
    publishToMavenCentral()
    if (hasGpgKey()) {
        signAllPublications()
    }

    coordinates(artifactId = project.name)

    pom {
        name = project.name
        description = "Extensions to store DataStore in EncryptedFile"

        setGitHubProject("dayanruben/encrypted-datastore")
        licenses {
            mit()
        }
        developers {
            developer(id = "osipxd", name = "Osip Fatkullin", email = "osip.fatkullin@gmail.com")
            developer(id = "dayanruben", name = "Dayan Ruben", email = "mail@dayanruben.com")
        }
    }
}

apiValidation {
    ignoredPackages.add("com.dayanruben.datastore.encrypted.internal")
    nonPublicMarkers.add("androidx.annotation.RestrictTo")

    // Check only the project to which BCV is applied
    ignoredProjects.addAll(subprojects.map { it.name })
}
