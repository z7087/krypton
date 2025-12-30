import dev.kikugie.semver.data.SemanticVersion

plugins {
    id("net.fabricmc.fabric-loom-remap")

    id("maven-publish")
    // `maven-publish`
    // id("me.modmuss50.mod-publish-plugin")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava = when {
    sc.current.parsed >= "1.20.6" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
//    maven {
//        name = 'ParchmentMC'
//        url = 'https://maven.parchmentmc.org'
//    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://repo.papermc.io/repository/maven-public/", "PaperMC", "com.velocitypowered")
    strictMaven("https://jitpack.io", "JitPack", "com.github.z7087")
    strictMaven("https://maven.parchmentmc.org", "ParchmentMC", "org.parchmentmc.data")
}

dependencies {
    /**
     * Fetches only the required Fabric API modules to not waste time downloading all of them for each version.
     * @see <a href="https://github.com/FabricMC/fabric">List of Fabric API modules</a>
     */
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, property("deps.fabric_api") as String))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    @Suppress("UnstableApiUsage")
    mappings(
        if (hasProperty("deps.parchment"))
            loom.layered {
                officialMojangMappings()
                parchment("org.parchmentmc.data:parchment-${property("deps.parchment")}@zip")
            }
        else
            loom.officialMojangMappings()
    )
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")


    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    include(implementation("com.velocitypowered:velocity-native:3.4.0-SNAPSHOT") {
        isTransitive = false
    })
    include(implementation("com.github.z7087:final2constant:37a189f903") {
        isTransitive = false
    })
    include(implementation("com.github.z7087:Incubatorapiloader:1384bceed4")!!)
    //fapi("fabric-lifecycle-events-v1", "fabric-resource-loader-v0", "fabric-content-registries-v0")
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
    if (requiredJava >= JavaVersion.VERSION_16)
        options.compilerArgs.add("--add-modules=jdk.incubator.vector")
    if (requiredJava <= JavaVersion.VERSION_1_8)
        options.compilerArgs.add("-Xlint:-options")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json") // Useful for interface injection
    accessWidenerPath = rootProject.file("src/main/resources/krypton.accesswidener")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
    }

    runConfigs.all {
        ideConfigGenerated(true)
        property("fabric.development=true")
        property("mixin.hotSwap")
        vmArgs("-Dmixin.debug.export=true") // Exports transformed classes for debugging
        runDir = "../../run" // Shares the run directory between versions
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from(rootProject.file("LICENSE"))
}

tasks {
    processResources {
        inputs.property("id", project.property("mod.id"))
        inputs.property("name", project.property("mod.name"))
        inputs.property("version", project.property("mod.version"))
        inputs.property("minecraft", project.property("mod.mc_dep"))

        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "minecraft" to project.property("mod.mc_dep")
        )

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    // Builds the version into a shared folder in `build/libs/${mod version}/`
    register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile }, remapSourcesJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

stonecutter {
    dependencies["java"] = requiredJava.majorVersion
}

/*
// Publishes builds to Modrinth and Curseforge with changelog from the CHANGELOG.md file
publishMods {
    file = tasks.remapJar.map { it.archiveFile.get() }
    additionalFiles.from(tasks.remapSourcesJar.map { it.archiveFile.get() })
    displayName = "${property("mod.name")} ${property("mod.version")} for ${property("mod.mc_title")}"
    version = property("mod.version") as String
    changelog = rootProject.file("CHANGELOG.md").readText()
    type = STABLE
    modLoaders.add("fabric")

    dryRun = providers.environmentVariable("MODRINTH_TOKEN").getOrNull() == null
        || providers.environmentVariable("CURSEFORGE_TOKEN").getOrNull() == null

    modrinth {
        projectId = property("publish.modrinth") as String
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(property("mod.mc_targets").toString().split(' '))
        requires {
            slug = "fabric-api"
        }
    }

    curseforge {
        projectId = property("publish.curseforge") as String
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.addAll(property("mod.mc_targets").toString().split(' '))
        requires {
            slug = "fabric-api"
        }
    }
}
 */
/*
// Publishes builds to a maven repository under `com.example:template:0.1.0+mc`
publishing {
    repositories {
        maven("https://maven.example.com/releases") {
            name = "myMaven"
            // To authenticate, create `myMavenUsername` and `myMavenPassword` properties in your Gradle home properties.
            // See https://stonecutter.kikugie.dev/wiki/tips/properties#defining-properties
            credentials(PasswordCredentials::class.java)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "${property("mod.group")}.${property("mod.id")}"
            artifactId = property("mod.id") as String
            version = project.version

            from(components["java"])
        }
    }
}
 */

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "${property("mod.group")}.${property("mod.id")}"
            artifactId = property("mod.id") as String
            version = "${property("mod.version")}+${sc.current.version}"

            from(components["java"])
        }
    }
}