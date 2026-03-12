import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.library")
    id("convention.publish")
}

applyKotlinDefaults()
extensions.configure<LibraryExtension>("android") {
    applyAndroidDefaults()
}

dependencies {
    add("api", platform(project(":encrypted-datastore-bom")))
}
