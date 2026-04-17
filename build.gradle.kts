import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.user.UserExtension
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

buildscript {
    repositories {
        val repoLocalMaven = File(rootDir, "local-maven").takeIf { it.exists() }
        if (repoLocalMaven != null) {
            maven {
                url = uri(repoLocalMaven)
                name = "NESQL Repo Local Maven"
            }
        }
        val localMavenRepo = System.getenv("NESQL_LOCAL_MAVEN_REPO")?.takeIf { it.isNotBlank() }
        if (localMavenRepo != null) {
            maven {
                url = uri(localMavenRepo)
                name = "NESQL Local Maven"
            }
        }
        maven("https://jitpack.io") { name = "JitPack" }
        maven("https://maven.msrandom.net/repository/cloche") { name = "GTNH Cloche" }
        maven("http://files.minecraftforge.net/maven") { name = "Forge Legacy Files" }
        maven {
            url = uri("https://repo1.maven.org/maven2")
            name = "Maven Central First"
        }
        mavenCentral()
        maven("https://maven.minecraftforge.net") { name = "Forge" }
        maven("https://nexus.gtnewhorizons.com/repository/public/") { name = "GTNH Nexus" }
    }
    dependencies {
        classpath("com.github.GTNewHorizons:ForgeGradle:1.2.11")
    }
}

plugins {
    idea
    java
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.google.protobuf") version "0.9.3"
}

apply(plugin = "forge")

val projectJavaVersion = JavaLanguageVersion.of(8)

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Add stubs source directory for compilation
sourceSets.main.get().java.srcDir("src/stubs/java")

val protoBufferVersion: String by project
val protocPath = sequenceOf(
    System.getenv("NESQL_PROTOC_PATH"),
    File(rootDir, "bin/protoc.exe").absolutePath,
    File(rootDir, "protoc-33.4-win64/bin/protoc.exe").absolutePath,
    "E:/MC-test/protoc-33.4-win64/bin/protoc.exe"
).firstOrNull { it != null && File(it).exists() }
    ?: error("No protoc.exe found. Set NESQL_PROTOC_PATH or extract protoc-33.4-win64.zip into the repo root.")

protobuf {
    protoc {
        // Prefer an explicit env override, otherwise use the repo-local protoc bundle.
        path = protocPath
    }
    repositories {
        mavenCentral()
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

val nesqlExporterVersion: String by project
group = "com.github.dcysteine.nesql.exporter"
version = nesqlExporterVersion

val minecraftVersion: String by project
val forgeVersion: String by project
minecraft.version = "$minecraftVersion-$forgeVersion-$minecraftVersion"

configure<UserExtension> {
    replacements.putAll(
            mapOf(
                    Pair("@version@", project.version)
            )
    )
    runDir = "run"
}

val Project.minecraft: UserExtension
    get() = extensions.getByName<UserExtension>("minecraft")

val shadowImplementation: Configuration by configurations.creating {
    configurations["implementation"].extendsFrom(this)
}

val shadowRuntime: Configuration by configurations.creating {
    configurations["runtime"].extendsFrom(this)
}

// Fix for Scala library resolution - force correct groupId
configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            substitute(module("org.scala-lang:scala-parser-combinators_2.11"))
                .using(module("org.scala-lang.modules:scala-parser-combinators_2.11:1.0.1"))
            substitute(module("org.scala-lang:scala-swing_2.11"))
                .using(module("org.scala-lang.modules:scala-swing_2.11:1.0.1"))
            substitute(module("org.scala-lang:scala-xml_2.11"))
                .using(module("org.scala-lang.modules:scala-xml_2.11:1.0.2"))
        }
        // Force Maven Central for protobuf dependencies
        eachDependency {
            // Force protobuf-java to use version from gradle.properties
            if (requested.group == "com.google.protobuf" && requested.name == "protobuf-java") {
                useTarget("com.google.protobuf:protobuf-java:${protoBufferVersion}")
            }
            // Force GTNH dependencies versions
            if (requested.group == "com.github.GTNewHorizons" && requested.name == "Railcraft") {
                useTarget("com.github.GTNewHorizons:Railcraft:9.15.15")
            }
        }
    }
}

repositories {
    val repoLocalMaven = File(rootDir, "local-maven").takeIf { it.exists() }
    if (repoLocalMaven != null) {
        maven {
            url = uri(repoLocalMaven)
            name = "NESQL Repo Local Maven"
        }
    }
    val localMavenRepo = System.getenv("NESQL_LOCAL_MAVEN_REPO")?.takeIf { it.isNotBlank() }
    if (localMavenRepo != null) {
        maven {
            url = uri(localMavenRepo)
            name = "NESQL Local Maven"
        }
    }
    mavenCentral()
    maven("https://maven.minecraftforge.net") {
        name = "Forge"
        metadataSources {
            mavenPom()
            artifact()
            // Allow fallback to Maven Central for missing artifacts
        }
    }
    maven("https://nexus.gtnewhorizons.com/repository/public/") {
        name = "GTNH Nexus"
    }
    maven("https://cursemaven.com") {
        name = "Curse Maven"
    }

    maven("http://maven.ic2.player.to") {
        name = "IC2 Maven"
        url = uri(getURL("http://maven.ic2.player.to", "http://maven2.ic2.player.to"))
        metadataSources {
            artifact()
        }
        content {
            includeGroup("net.industrial-craft")
        }
    }
    maven("http://gregtech.overminddl1.com") {
        content {
            includeGroup("thaumcraft")
        }
    }

    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
    }

    maven("https://jcenter.bintray.com") {
        name = "JCenter"
    }
}

dependencies {
    val autoValueVersion: String by project
    compileOnly("com.google.auto.value:auto-value-annotations:$autoValueVersion")
    annotationProcessor("com.google.auto.value:auto-value:$autoValueVersion")

    val protoBufferVersion: String by project
    shadowImplementation("com.google.protobuf:protobuf-java:$protoBufferVersion")

    val lombokVersion: String by project
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    val springDataVersion: String by project
    shadowImplementation("org.springframework.data:spring-data-jpa:$springDataVersion")

    val jakartaPersistenceVersion: String by project
    compileOnly("jakarta.persistence:jakarta.persistence-api:$jakartaPersistenceVersion")

    val hibernateVersion: String by project
    shadowImplementation("org.hibernate:hibernate-core-jakarta:$hibernateVersion")
    annotationProcessor("org.hibernate:hibernate-jpamodelgen-jakarta:$hibernateVersion")

    val hsqldbVersion: String by project
    shadowRuntime("org.hsqldb:hsqldb:$hsqldbVersion:jdk8")

    val msgpackVersion: String by project
    shadowImplementation("org.msgpack:msgpack-core:$msgpackVersion")

    val neiVersion: String by project
    implementation("com.github.GTNewHorizons:NotEnoughItems:$neiVersion:dev")

    val gregTech5Version: String by project
    implementation("com.github.GTNewHorizons:GT5-Unofficial:$gregTech5Version:dev") {
        isTransitive = true
    }
    // The following are compile-time dependencies of GT5.
    val industrialCraft2Version: String by project
    compileOnly("net.industrial-craft:industrialcraft-2:$industrialCraft2Version-experimental:api") {
        isTransitive = true
    }
    val forestryVersion: String by project
    compileOnly("com.github.GTNewHorizons:ForestryMC:$forestryVersion:api") {
        isTransitive = true
    }
    val railcraftVersion: String by project
    compileOnly("com.github.GTNewHorizons:Railcraft:$railcraftVersion:api") {
        isTransitive = true
    }
    val buildCraftVersion: String by project
    compileOnly("com.github.GTNewHorizons:BuildCraft:$buildCraftVersion:api") {
        isTransitive = true
    }
    val enderIoVersion: String by project
    compileOnly("com.github.GTNewHorizons:EnderIO:$enderIoVersion:api") {
        isTransitive = true
    }
    val projectRedVersion: String by project
    compileOnly("com.github.GTNewHorizons:ProjectRed:$projectRedVersion:dev") {
        isTransitive = true
    }
//    compileOnly("com.github.GTNewHorizons:bartworks:0.9.14:dev") {
//        isTransitive = true
//    }
//    compileOnly("com.github.GTNewHorizons:GoodGenerator:0.8.12:dev") {
//        isTransitive = true
//    }
    compileOnly("com.github.GTNewHorizons:GTNH-Intergalactic:1.4.30") {
        isTransitive = true
    }
//    compileOnly("com.github.GTNewHorizons:GTplusplus:1.11.33:dev") {
//        isTransitive = true
//    }
//    compileOnly("com.github.GTNewHorizons:GTNH-Lanthanides:0.12.11:dev") {
//        isTransitive = true
//    }
//    compileOnly("com.github.GTNewHorizons:TecTech:5.3.32:dev") {
//        isTransitive = true
//    }
    compileOnly("com.github.GTNewHorizons:ModularUI:1.2.17") {
        isTransitive = true
    }
    val thaumcraftVersion: String by project
    implementation("thaumcraft:Thaumcraft:$minecraftVersion-$thaumcraftVersion:dev")
    val thaumcraftNeiVersion: String by project
    implementation("curse.maven:thaumcraft-nei-plugin-225095:$thaumcraftNeiVersion")

    // Botania, Blood Magic, and Witchery dependencies are optional
    // These will be loaded from Minecraft mods at runtime
    // We use reflection to access these APIs to avoid compile-time dependencies

    val betterQuestingVersion: String by project
    implementation("com.github.GTNewHorizons:BetterQuesting:$betterQuestingVersion:dev") {
        isTransitive = true
    }

    // Scala dependencies for protobuf compilation
    implementation("org.scala-lang.modules:scala-parser-combinators_2.11:1.0.1")
    implementation("org.scala-lang.modules:scala-swing_2.11:1.0.1")
    implementation("org.scala-lang.modules:scala-xml_2.11:1.0.2")
}

tasks.withType<Jar> {
    // Replace version in mcmod.info
    filesMatching("mcmod.info") {
        expand(
                mapOf(
                        "version" to project.version,
                        "mcversion" to project.minecraft.version
                )
        )
    }
    archiveBaseName.set("NESQL++")
}

fun getURL(main: String, fallback: String): String {
    return if (pingURL(main, 10000)) main else fallback
}

fun pingURL(url: String, timeout: Int): Boolean {
    return try {
        val connection = URL(url.replaceFirst("^https".toRegex(), "http")).openConnection() as HttpURLConnection
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        connection.requestMethod = "HEAD"
        val responseCode = connection.responseCode
        responseCode in 200..399
    } catch (e: IOException) {
        false
    }
}

// Unfortunately, we can neither minimize the shadow jar nor relocate it,
// because Hibernate seems to reference classes indirectly and so we would break it.
//
// We also can't relocate it because it does not include the mod code, so we would need to relocate
// the code separately.
//
// I had to make this a separate deps jar rather than a single shadow jar that contains both the
// deps and the mod code. The reason appears to be some kind of weird bug where trying to include
// the mod code causes org.slf4j, specifically, to not get picked up somehow, resulting in a
// ClassNotFoundException at runtime. Oddly enough, copying that single directory verbatim out of
// the shadow jar and into a separate jar fixes the issue.
//
// I spent way too long trying to figure out what went wrong, so I'm giving up and making this a
// separate jar. This does have the side benefit of speeding up build times, since deps don't change
// very often.
val depsJar by tasks.creating(ShadowJar::class) {
    // Enable zip64 for large archives (>65535 entries)
    isZip64 = true

    // If mod code were to actually be included, we'd need this to use the obfuscated mod code.
    //from(tasks["reobf"].outputs)
    configurations = listOf(shadowImplementation, shadowRuntime)

    /*
     * Doesn't look like we actually need class path.
    manifest {
        val classPath = (shadowImplementation + shadowRuntime).joinToString(" ") { it.name }
        attributes("Class-Path" to classPath)
    }
     */

    // Remove mod code and other junk from jar.
    val excludeFun =
            fun(fileTreeElement: FileTreeElement): Boolean {
                val path = fileTreeElement.path
                val keep = path.endsWith(".jar")
                val remove = !path.contains("/") || path.startsWith("META-INF") || path.startsWith(project.group.toString())
                return remove && !keep
            }
    exclude(excludeFun)

    archiveClassifier.set("deps")
}

val sourcesJar by tasks.creating(Jar::class) {
    from(sourceSets.main.get().allSource)
    from("$projectDir/LICENSE.md")
    archiveClassifier.set("sources")
}

val devJar by tasks.creating(Jar::class) {
    from(sourceSets.main.get().output)
    archiveClassifier.set("dev")
}

// Export SQL Schema for NESQL Server.
val sqlJar by tasks.creating(Jar::class) {
    from(sourceSets.main.get().output)
    exclude("com/github/dcysteine/nesql/exporter")
    exclude("*.proto")
    exclude("META-INF")
    exclude("mcmod.info")
    archiveClassifier.set("sql")
}

artifacts {
    archives(depsJar)
    archives(sourcesJar)
    archives(devJar)
    archives(sqlJar)
}
