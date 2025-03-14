plugins {
    `java-library`
    id("velocity-checkstyle") apply false
    id("velocity-spotless") apply false
}

subprojects {
    apply<JavaLibraryPlugin>()

    //apply(plugin = "velocity-checkstyle")
    apply(plugin = "velocity-spotless")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    dependencies {
        testImplementation(rootProject.libs.junit)
        implementation("org.mindrot:jbcrypt:0.4")
        // Driver MongoDB (wersja 4.x)
        implementation("org.mongodb:mongodb-driver-sync:4.10.2")
        implementation("org.json:json:20210307")

    }

    tasks {
        test {
            useJUnitPlatform()
            reports {
                junitXml.required.set(true)
            }
        }
    }
}
