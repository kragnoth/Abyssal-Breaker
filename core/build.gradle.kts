plugins { `java-library` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    api("com.google.code.gson:gson:2.11.0")          // save files (Minecraft ships Gson too)
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("simSeed", System.getProperty("simSeed") ?: "1")
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
