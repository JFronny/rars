plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
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
