plugins {
    kotlin("jvm") version "2.0.0"
    id("fabric-loom") version "1.7.4"
    `maven-publish`
}

version = project.property("mod_version") as String
group   = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "FabricMC" }
    maven("https://maven.cobblemon.com/releases") { name = "Cobblemon" }
}

dependencies {
    // Minecraft + mappings
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")

    // Fabric
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Kotlin adapter for Fabric
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_language_kotlin_version")}")

    // Cobblemon – required at compile time; expected to be present at runtime via the user's mod list
    modCompileOnly("com.cobblemon:cobblemon-fabric:${project.property("cobblemon_version")}")
    // Uncomment to run from IDE without a full modpack:
    // modLocalRuntime("com.cobblemon:cobblemon-fabric:${project.property("cobblemon_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
