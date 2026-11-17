import net.minecraftforge.gradle.user.UserExtension
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest

fun targetCanonical(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.entries.associate { it.key.toString() to targetCanonical(it.value) }.toSortedMap()
    is List<*> -> value.map(::targetCanonical)
    else -> value
}
fun targetJson(value: Any): String = JsonOutput.toJson(targetCanonical(value)) + "\n"
fun targetDigest(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
fun resolveTargetProfile(manifest: ByteArray, policy: String): Map<String, Any> {
    fun obj(value: Any?) = value as? Map<*, *> ?: error("Target profile object missing")
    fun text(value: Any?) = value as? String ?: error("Target profile string missing")
    val input = obj(JsonSlurper().parseText(policy))
    require(input.keys == setOf("format", "id", "release", "manifestSha256", "artifacts", "families", "historicalInventories", "evidence")) { "Unknown target profile fields" }
    require(input["format"] == "nesql.target-profile" && input["id"] == "gtnh-2.9.0")
    val digest = targetDigest(manifest)
    require(digest == input["manifestSha256"] && digest == "e2c7396c7121c86668ce3f93216f2bde71984a7ecd34ed5d231f8888ce3d13fa") { "Target manifest digest differs" }
    val release = obj(JsonSlurper().parseText(String(manifest, Charsets.UTF_8)))
    require(release["version"] == input["release"] && input["release"] == "2.9.0")
    val containers = sortedMapOf<String, Any>()
    val coordinates = sortedMapOf<String, String>()
    for (raw in input["artifacts"] as List<*>) {
        val row = obj(raw)
        require(row.keys.all { it in setOf("key", "section", "containers", "ambiguous", "exceptions", "build", "file", "sha256") })
        val section = text(row["section"] ?: "github_mods")
        require(section == "github_mods" || section == "external_mods")
        val key = text(row["key"])
        val selected = obj(release[section])[key] ?: error("Target release selector missing: $section.$key")
        val version = text(obj(selected)["version"])
        val exceptions = row["exceptions"]?.let(::obj) ?: emptyMap<Any, Any>()
        val ambiguous = row["ambiguous"] as? List<*> ?: emptyList<Any>()
        val ids = row["containers"] as List<*>
        require(ids.isNotEmpty() && exceptions.keys.all { it in ids } && ambiguous.all { it in ids })
        require((row["sha256"] ?: "").toString().matches(Regex("([0-9a-f]{64})?")))
        require((row["file"] ?: "").toString().let { !it.contains('/') && !it.contains('\\') && !it.contains("..") })
        for (id in ids) {
            val name = text(id)
            require(!containers.containsKey(name)) { "Duplicate target container: $name" }
            containers[name] = linkedMapOf("artifact" to key, "version" to text(exceptions[name] ?: version),
                "binding" to if (name in ambiguous) "unverified" else "metadataOnly",
                "file" to (row["file"] ?: ""), "sha256" to (row["sha256"] ?: ""))
        }
        row["build"]?.let { rawBuild ->
            val build = obj(rawBuild)
            require(build.keys.all { it in setOf("id", "group", "artifact", "classifier", "minecraftPrefix") })
            val buildVersion = if (build["minecraftPrefix"] == true) "1.7.10-$version" else version
            val id = text(build["id"])
            require(!coordinates.containsKey(id)) { "Duplicate build coordinate: $id" }
            coordinates[id] = "${text(build["group"])}:${text(build["artifact"])}:$buildVersion:${text(build["classifier"])}"
        }
    }
    val families = obj(input["families"])
    // This migration cannot approve semantic/callback evidence by changing a status flag.
    require(families.values.all { it == "unverified" }) { "Reviewed families require a separate semantic audit implementation" }
    require(obj(input["historicalInventories"]) == mapOf(
        "native-usage-admission.json" to "historical-native-usage-admission.json",
        "squeezer-callback-classes.json" to "historical-squeezer-callback-classes.json"))
    return linkedMapOf("format" to "nesql.resolved-target-profile", "id" to text(input["id"]),
        "manifestSha256" to digest, "build" to coordinates, "containers" to containers, "families" to families)
}

val targetManifest = file("targets/gtnh-2.9.0/manifest.json")
val targetPolicy = file("targets/gtnh-2.9.0/profile.json")
val targetProfile = resolveTargetProfile(targetManifest.readBytes(), targetPolicy.readText()).toMutableMap()
targetProfile["historicalInventories"] = listOf("native-usage-admission.json", "squeezer-callback-classes.json").associateWith {
    JsonSlurper().parse(file("targets/gtnh-2.9.0/historical-$it"))
}
targetProfile["profileSha256"] = targetDigest(targetJson(targetProfile).toByteArray(Charsets.UTF_8))
val targetBuild = targetProfile["build"] as Map<*, *>
fun targetDependency(id: String) = targetBuild[id] as? String ?: error("Unknown target dependency: $id")

val generatedTarget = file("$buildDir/generated/target-profile")
val generateTargetProfile = tasks.register("generateTargetProfile") {
    inputs.files(targetManifest, targetPolicy)
    val inventories = listOf("native-usage-admission.json", "squeezer-callback-classes.json")
    inventories.forEach { inputs.file(file("targets/gtnh-2.9.0/historical-$it")) }
    outputs.dir(generatedTarget)
    doLast {
        generatedTarget.mkdirs()
        file("$generatedTarget/target-profile.json").writeText(targetJson(targetProfile), Charsets.UTF_8)
        for (name in inventories)
            file("targets/gtnh-2.9.0/historical-$name").copyTo(file("$generatedTarget/$name"), overwrite = true)
    }
}

tasks.register("targetProfileTest") {
    group = "verification"
    doLast {
        val manifest = file("targets/gtnh-2.9.0/manifest.json").readBytes()
        val policy = file("targets/gtnh-2.9.0/profile.json").readText()
        val resolved = resolveTargetProfile(manifest, policy)
        check((resolved["build"] as Map<*, *>)["gregtech"] ==
            "com.github.GTNewHorizons:GT5-Unofficial:5.09.54.265:dev")
        val containers = resolved["containers"] as Map<*, *>
        check((containers["IC2"] as Map<*, *>)["version"] == "2.2.828-experimental")
        check((containers["gregtech"] as Map<*, *>)["binding"] == "unverified")
        check(targetJson(resolved) == targetJson(resolveTargetProfile(manifest, policy)))
        fun refuses(bytes: ByteArray, text: String) {
            check(runCatching { resolveTargetProfile(bytes, text) }.isFailure)
        }
        refuses(manifest + byteArrayOf(10), policy)
        refuses(manifest, policy.replace("\"GT5-Unofficial\"", "\"missing-release-key\""))
        refuses(manifest, policy.replace("\"gtnhlib\"", "\"Forestry\""))
        refuses(manifest, policy.replaceFirst("\"unverified\"", "\"reviewed\""))
        refuses(manifest, policy.replace("\"format\":", "\"unknown\":0,\"format\":"))
        println("Target profile: mapping, exception, ambiguity, digest, unknown selector and deterministic generation checks passed")
    }
}

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
    for (id in listOf("nei", "gregtech", "ccc", "gtnhlib", "modularui", "modularui2", "structurelib", "blockrenderer", "forestry", "thaumcraft"))
        compileOnly(targetDependency(id)) { isTransitive = false }
    // BlockRenderer's public construction metadata exposes FastUtil collections.
    compileOnly("it.unimi.dsi:fastutil:8.5.18") { isTransitive = false }
    // GT's public tile interfaces expose Waila types; needed only for lifecycle test doubles.
    testRuntimeOnly(targetDependency("waila")) { isTransitive = false }
}

sourceSets.main.get().resources.srcDir(generatedTarget)
tasks.named("processResources") { dependsOn(generateTargetProfile) }
tasks.register("resolveTargetGt") {
    doLast {
        val gt = configurations.detachedConfiguration(dependencies.create(targetDependency("gregtech")))
        gt.isTransitive = false
        gt.files.forEach { println("Target GT dependency: ${it.absolutePath}") }
    }
}

// Two behavior suites cover source identity/publication and the job/API lifecycle.
val gameLibraries = sourceSets.main.get().compileClasspath
val targetAdmissionClasses = tasks.register<JavaCompile>("compileTargetAdmissionTests") {
    source(files("TargetProfile.java", "SqueezerCallbackClasses.java").map {
        file("src/main/java/com/github/dcysteine/nesql/exporter/capture/${it.name}")
    })
    source(files("TargetProfileTest.java", "TargetClassLoaderTest.java").map {
        file("src/test/java/com/github/dcysteine/nesql/exporter/capture/${it.name}")
    })
    classpath = gameLibraries
    destinationDir = file("$buildDir/target-admission-tests")
}
tasks.register<JavaExec>("targetAdmissionTest") {
    group = "verification"
    dependsOn(targetAdmissionClasses, generateTargetProfile)
    classpath = files(targetAdmissionClasses.get().destinationDir) + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.TargetProfileTest"
    args(file("$generatedTarget/target-profile.json").absolutePath)
    maxHeapSize = "64m"
}
tasks.register<JavaExec>("targetClassLoaderTest") {
    group = "verification"
    dependsOn(targetAdmissionClasses)
    classpath = files(targetAdmissionClasses.get().destinationDir, "src/test/target-profile") + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.TargetClassLoaderTest"
    maxHeapSize = "64m"
}
tasks.register<JavaExec>("targetArchiveTest") {
    group = "verification"
    dependsOn("testClasses", generateTargetProfile)
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.TargetArchiveTest"
    args(file("$generatedTarget/target-profile.json").absolutePath)
    maxHeapSize = "128m"
}
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
tasks.register<JavaExec>("visualThroughputTest") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.VisualThroughputTest"
    maxHeapSize = "128m"
    project.findProperty("animationMods")?.toString()?.let { systemProperty("nesql.animationMods", it) }
}
tasks.register<JavaExec>("oreGroupsBudgetTest") {
    group = "verification"
    description = "Checks the bounded native ore-group registry budget."
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.OreGroupsBudgetTest"
    maxHeapSize = "128m"
}
tasks.register<JavaExec>("captureProfileTest") {
    group = "verification"
    description = "Checks profile-specific capture stages."
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.CaptureProfileTest"
    maxHeapSize = "128m"
}
tasks.register<JavaExec>("captureInspectionTest") {
    group = "verification"
    description = "Checks non-fatal handler diagnostics during game inspection."
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.CaptureInspectionTest"
    maxHeapSize = "128m"
}
tasks.named<JavaExec>("sourceTest") {
    project.findProperty("sourceFixture")?.toString()?.let { args(it) }
}

// Explicit offline native-mod suite. The caller supplies a derived SRG-to-MCP jar;
// no installed mod or running client is changed by this task.
tasks.register<JavaExec>("nativeFilterTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries + files(project.findProperty("nativeTestJar"))
    main = "com.github.dcysteine.nesql.exporter.capture.NativeFilterTest"
    maxHeapSize = "256m"
    systemProperty("nesql.nativeFilterTests", "true")
    systemProperty("nesql.nativeFilterFixture", file("$buildDir/native-tests/filter.json").absolutePath)
}

tasks.register<JavaExec>("nativeInfusionTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries + files(project.findProperty("nativeTestJar"))
    main = "com.github.dcysteine.nesql.exporter.capture.NativeInfusionTest"
    maxHeapSize = "256m"
    systemProperty("nesql.nativeInfusionTests", "true")
}

tasks.register<JavaExec>("nativeAeTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries + files(project.findProperty("nativeTestJar"))
    main = "com.github.dcysteine.nesql.exporter.capture.NativeAeTest"
    maxHeapSize = "256m"
    systemProperty("nesql.nativeAeTests", "true")
}

// Requires a real compatibility OpenGL context (Pbuffer); run explicitly or under Xvfb in CI.
tasks.register<JavaExec>("nativeMachinesTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + files(listOfNotNull(project.findProperty("nativeTestJar"), project.findProperty("nativeExtraTestJar"), project.findProperty("nativeCraftingTestJar"))) +
        files(project.findProperty("nativeAdditionalJars")?.toString()?.split(',') ?: emptyList<String>()) + gameLibraries
    // Runtime-law tests must not silently prefer Gradle's Guava over the target
    // game's library. The oracle validates the supplied artifact's exact digest.
    project.findProperty("nativeGuavaJar")?.toString()?.let { path ->
        val nativeGuava = file(path)
        require(nativeGuava.isFile) { "nativeGuavaJar must name an existing game library" }
        classpath = files(nativeGuava) + classpath.filter {
            !it.name.matches(Regex("guava-[0-9].*\\.jar"))
        }
    }
    if (project.findProperty("nativeFamily") == "usage-queries") classpath = files("build/native-tests/usage-profile") + classpath
    main = "com.github.dcysteine.nesql.exporter.capture.NativeMachinesTest"
    maxHeapSize = "256m"
    systemProperty("nesql.nativeMachineTests", "true")
    systemProperty("nesql.nativeFamily", project.findProperty("nativeFamily")?.toString() ?: "machines")
    systemProperty("nesql.nativeOreOptimized", project.findProperty("nativeOreOptimized")?.toString() ?: "false")
    project.findProperty("nativeUsageFixture")?.toString()?.let { systemProperty("nesql.nativeUsageFixture", it) }
    project.findProperty("nativeFixtureDirectory")?.toString()?.let { systemProperty("nesql.nativeFixtureDirectory", it) }
}

tasks.register<JavaExec>("nativeGendustryFluidsTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries +
        files("build/native-tests/gendustry.jar", "build/native-tests/bdlib.jar")
    main = "com.github.dcysteine.nesql.exporter.capture.NativeGendustryFluidsTest"
    maxHeapSize = "256m"
}

tasks.register<JavaExec>("nativeBloodOrbTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries + files("build/native-tests/bloodmagic.jar", "build/native-tests/railcraft.jar", "build/native-tests/ic2.jar", "build/native-tests/applecore.jar")
    main = "com.github.dcysteine.nesql.exporter.capture.NativeBloodOrbTest"
    maxHeapSize = "256m"
}

tasks.register<JavaExec>("nativeCreativeCoreTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries + files("build/native-tests/littletiles.jar")
    main = "com.github.dcysteine.nesql.exporter.capture.NativeCreativeCoreTest"
    maxHeapSize = "256m"
}

tasks.register<JavaExec>("nativeDecayableTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries + files("build/native-tests/railcraft.jar", "build/native-tests/ic2.jar", "build/native-tests/applecore.jar", "build/native-tests/unimixins.jar")
    main = "com.github.dcysteine.nesql.exporter.capture.NativeDecayableTest"
    maxHeapSize = "256m"
}

tasks.register<JavaExec>("nativeBloodOrbShapelessTest") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries + files("build/native-tests/bloodmagic.jar", "build/native-tests/railcraft.jar", "build/native-tests/ic2.jar", "build/native-tests/applecore.jar")
    main = "com.github.dcysteine.nesql.exporter.capture.NativeBloodOrbShapelessTest"
    maxHeapSize = "256m"
}

val glNatives by configurations.creating
val nativePlatform = if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux"
dependencies { glNatives("org.lwjgl.lwjgl:lwjgl-platform:2.9.1:natives-$nativePlatform") }
val unpackGlNatives = tasks.register<Copy>("unpackGlNatives") {
    from({ glNatives.map { zipTree(it) } })
    into("$buildDir/gl-natives")
}
tasks.register<JavaExec>("glTest") {
    group = "verification"
    description = "Checks capture state on a real offscreen OpenGL context."
    dependsOn("testClasses", unpackGlNatives)
    classpath = sourceSets.test.get().runtimeClasspath + gameLibraries
    main = "com.github.dcysteine.nesql.exporter.capture.GlStateTest"
    systemProperty("org.lwjgl.librarypath", file("$buildDir/gl-natives").absolutePath)
}

tasks.withType<Jar> {
    archiveBaseName.set("NESQL++")
    manifest.attributes(mapOf("FMLCorePlugin" to "com.github.dcysteine.nesql.exporter.core.RendererPlugin", "FMLCorePluginContainsFMLMod" to "true"))
    filesMatching("mcmod.info") {
        expand(mapOf("version" to project.version, "mcversion" to project.property("minecraftVersion")))
    }
}

// Production Minecraft uses SRG names. Even a direct jar build must reobfuscate.
tasks.named("jar") { finalizedBy("reobf") }
