plugins {
    application
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
    maven("https://maven.frohnmeyer-wds.de/artifacts")
}

dependencies {
    implementation("com.formdev:flatlaf:3.5.2")
    implementation("io.gitlab.jfronny:dbusmenu4j:1.2.0-SNAPSHOT")
    implementation("io.gitlab.jfronny:commons-logger:1.8.0-SNAPSHOT")
}

sourceSets {
    main {
        java {
            srcDirs("src/jsoftfloat")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "rars.Launch"
}

tasks {
    run.configure {
        jvmArgs(
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt.X11=ALL-UNNAMED"
        )
    }
    jar {
        manifest {
            attributes(
                "Add-Opens" to "java.desktop/java.awt",
                "Add-Exports" to "java.desktop/sun.awt.X11"
            )
        }
    }
}