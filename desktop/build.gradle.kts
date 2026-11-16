plugins { application }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

val lwjglVersion = "3.3.3"   // matches Minecraft 1.21.1
val jomlVersion = "1.10.5"
val nativePlatforms = listOf("natives-linux", "natives-windows", "natives-macos", "natives-macos-arm64")

dependencies {
    implementation(project(":core"))
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    for (m in listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-openal", "lwjgl-stb")) {
        implementation("org.lwjgl:$m")
        for (p in nativePlatforms) runtimeOnly("org.lwjgl:$m::$p")
    }
    implementation("org.joml:joml:$jomlVersion")
}

application {
    mainClass.set("net.kragnoth.abyssal.desktop.Main")
    applicationName = "abyssal-breaker"
    if (System.getProperty("os.name").lowercase().contains("mac")) applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
}

// single runnable jar: java -jar desktop/build/libs/abyssal-breaker-<version>-all.jar
val fatJar by tasks.registering(Jar::class) {
    group = "build"
    archiveBaseName.set("abyssal-breaker")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "net.kragnoth.abyssal.desktop.Main") }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    }
}
tasks.build { dependsOn(fatJar) }
