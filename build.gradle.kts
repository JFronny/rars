plugins {
    application
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.formdev:flatlaf:3.5.2")
    implementation("io.gitlab.jfronny:dbusmenu4j:1.2.0-SNAPSHOT")
//    implementation("com.github.hypfvieh:dbus-java-core:5.1.0")
//    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.0")
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
    this.run.configure {
        jvmArgs(
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt.X11=ALL-UNNAMED"
        )
    }
}