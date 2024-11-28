plugins {
    application
    id("com.gradleup.shadow") version "8.3.5"
    id("org.graalvm.buildtools.native") version "0.10.3"
}

repositories {
    mavenCentral()
    maven("https://maven.frohnmeyer-wds.de/artifacts")
}

dependencies {
    implementation("com.formdev:flatlaf:3.5.2")
    implementation("io.gitlab.jfronny:dbusmenu4j:1.2.0-SNAPSHOT")
    implementation("io.gitlab.jfronny:commons-logger:1.8.0-SNAPSHOT")
    implementation("io.gitlab.jfronny:slf4j-over-jpl:1.8.0-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
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

graalvmNative.binaries {
    named("main") {
        buildArgs(
            "-march=compatibility",
            "--gc=G1",
            "-Djava.awt.headless=false",
        )
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
            vendor = JvmVendorSpec.matching("Oracle Corporation")
        }
        sharedLibrary = false
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
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