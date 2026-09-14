allprojects {
    group = "br.ufms"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
