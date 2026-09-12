import net.minecraftforge.gradle.user.UserExtension

buildscript {
    repositories {
        val local = System.getenv("NESQL_LOCAL_MAVEN_REPO")?.let(::File) ?: File(rootDir, "local-maven")
        if (local.isDirectory) maven { url = uri(local) }
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.msrandom.net/repository/cloche")
        maven("https://maven.minecraftforge.net")
        maven("https://nexus.gtnewhorizons.com/repository/public/")
    }
    dependencies { classpath("com.github.GTNewHorizons:ForgeGradle:1.2.11") }
}

plugins { java }
apply(plugin = "forge")

group = "com.github.dcysteine.nesql.exporter"
version = property("modVersion") as String

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

configure<UserExtension> {
    version = "${project.property("minecraftVersion")}-${project.property("forgeVersion")}-${project.property("minecraftVersion")}"
    replacements["@version@"] = project.version
    runDir = "run"
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

// Forge 1.7.10's original POM names these Scala modules under the wrong group.
configurations.all {
    resolutionStrategy.dependencySubstitution {
        for ((name, version) in listOf("scala-parser-combinators_2.11" to "1.0.1", "scala-swing_2.11" to "1.0.1", "scala-xml_2.11" to "1.0.2")) {
            substitute(module("org.scala-lang:$name")).using(module("org.scala-lang.modules:$name:$version"))
        }
    }
}

repositories {
    val local = System.getenv("NESQL_LOCAL_MAVEN_REPO")?.let(::File) ?: File(rootDir, "local-maven")
    if (local.isDirectory) maven { url = uri(local) }
    mavenCentral()
    maven("https://maven.minecraftforge.net") { metadataSources { mavenPom(); artifact() } }
    maven("https://nexus.gtnewhorizons.com/repository/public/")
    maven("https://gregtech.overminddl1.com") { content { includeGroup("thaumcraft") } }
}

dependencies {
    // The target game supplies every runtime library. Ship one mod jar, with no embedded mod APIs.
    compileOnly("com.github.GTNewHorizons:NotEnoughItems:${property("neiVersion")}:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:GT5-Unofficial:${property("gregTechVersion")}:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:CodeChickenCore:1.4.10:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:GTNHLib:0.7.10:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:ModularUI:1.2.20:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:ModularUI2:2.2.18-1.7.10:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:StructureLib:1.4.23:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:BlockRenderer6343:1.3.17:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:ForestryMC:${property("forestryVersion")}:api") { isTransitive = false }
    compileOnly("thaumcraft:Thaumcraft:${property("minecraftVersion")}-${property("thaumcraftVersion")}:dev") { isTransitive = false }
}

// Two behavior suites cover source identity/publication and the job/API lifecycle.
val gameLibraries = sourceSets.main.get().compileClasspath
sourceSets.test.get().compileClasspath += gameLibraries
val checks = listOf("source" to "source.SourceTest", "jobs" to "task.JobsTest").map { (name, entrypoint) ->
    tasks.register<JavaExec>("${name}Test") {
        group = "verification"
        description = "Checks $name behavior."
        dependsOn("testClasses")
        classpath = sourceSets.test.get().runtimeClasspath + gameLibraries
        minHeapSize = "32m"
        maxHeapSize = "256m"
        main = "com.github.dcysteine.nesql.exporter.$entrypoint"
        val fixtureDirectory = file("$buildDir/tmp/$name")
        doFirst { fixtureDirectory.mkdirs() }
        workingDir = fixtureDirectory
    }
}
tasks.named("check") { dependsOn(checks) }
tasks.named<JavaExec>("sourceTest") {
    project.findProperty("sourceFixture")?.toString()?.let { args(it) }
}

tasks.withType<Jar> {
    archiveBaseName.set("NESQL++")
    filesMatching("mcmod.info") {
        expand(mapOf("version" to project.version, "mcversion" to project.property("minecraftVersion")))
    }
}

// Production Minecraft uses SRG names. Even a direct jar build must reobfuscate.
tasks.named("jar") { finalizedBy("reobf") }
