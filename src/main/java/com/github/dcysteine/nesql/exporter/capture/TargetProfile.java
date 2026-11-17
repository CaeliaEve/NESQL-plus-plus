package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One immutable resolved target. Identity evidence never approves adapter semantics. */
final class TargetProfile {
    private static final String MANIFEST = "e2c7396c7121c86668ce3f93216f2bde71984a7ecd34ed5d231f8888ce3d13fa";
    private final Map<String, Binding> containers;
    private final Set<String> families;
    final String id, digest, manifestSha256;
    private TargetProfile(Map<String, Binding> containers, Set<String> families, String digest) {
        this.containers = Collections.unmodifiableMap(containers);
        this.families = Collections.unmodifiableSet(families);
        this.id = "gtnh-2.9.0"; this.digest = digest; this.manifestSha256 = MANIFEST;
    }
    private static final class Selected {
        static final TargetProfile VALUE = load();
    }
    static TargetProfile selected() { return Selected.VALUE; }
    private static TargetProfile load() {
        try (InputStream input = TargetProfile.class.getResourceAsStream("/target-profile.json")) {
            if (input == null) throw new IllegalStateException("target_profile_invalid: missing generated target-profile.json");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int size;
            while ((size = input.read(buffer)) != -1) {
                if (bytes.size() + size > 4 * 1024 * 1024) throw new IOException("Oversized target profile");
                bytes.write(buffer, 0, size);
            }
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException error) { throw new IllegalStateException("target_profile_invalid", error); }
    }
    static TargetProfile parse(String text) {
        JsonObject data = new JsonParser().parse(text).getAsJsonObject();
        if (!"nesql.resolved-target-profile".equals(data.get("format").getAsString())
            || !"gtnh-2.9.0".equals(data.get("id").getAsString())
            || !MANIFEST.equals(data.get("manifestSha256").getAsString()))
            throw new IllegalArgumentException("target_profile_invalid: target or manifest differs");
        String digest = data.get("profileSha256").getAsString();
        if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("target_profile_invalid: malformed profile digest");
        Map<String, Binding> containers = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("containers").entrySet())
            containers.put(entry.getKey(), new Binding(entry.getValue().getAsJsonObject()));
        Set<String> families = new TreeSet<>();
        for (Map.Entry<String, JsonElement> family : data.getAsJsonObject("families").entrySet()) {
            if (!"unverified".equals(family.getValue().getAsString()))
                throw new IllegalArgumentException("target_profile_invalid: semantic audit cannot be approved by a status flag");
            families.add(family.getKey());
        }
        return new TargetProfile(containers, families, digest);
    }
    String version(String id) {
        Binding binding = containers.get(id);
        if (binding == null) throw new IllegalArgumentException("target_mod_unknown: " + id + "; explicit release/container binding required");
        return binding.version;
    }
    String familyProblem(String family) {
        return "target_family_unverified: profile=gtnh-2.9.0 family=" + family
            + (families.contains(family) ? "; target adapter audit remains pending" : "; no target adapter audit is registered");
    }
    String identityProblem(String id, String version, String archiveSha256) {
        Binding binding = containers.get(id);
        if (binding == null) return "target_mod_unknown: " + id + "; removed or unmapped target; explicit release/container binding required";
        if (version == null) return "target_mod_missing: " + id + " observed=absent expected=" + binding.version;
        if (!binding.version.equals(version))
            return "target_version_mismatch: " + id + " observed=" + version + " expected=" + binding.version;
        if ("unverified".equals(binding.binding))
            return "target_binding_unverified: " + id + "; ambiguous Loader binding requires runtime container evidence";
        if (binding.sha256.isEmpty()) return "target_artifact_unverified: " + id + "; no actual target archive hash is recorded";
        if (!binding.sha256.equals(archiveSha256))
            return "target_artifact_mismatch: " + id + " observed=" + archiveSha256 + " expected=" + binding.sha256;
        return null;
    }
    private static final class Binding {
        final String version, sha256, binding;
        Binding(JsonObject row) {
            version = row.get("version").getAsString(); sha256 = row.get("sha256").getAsString(); binding = row.get("binding").getAsString();
            if (version.isEmpty() || !(sha256.isEmpty() || sha256.matches("[0-9a-f]{64}"))
                || !("metadataOnly".equals(binding) || "unverified".equals(binding)))
                throw new IllegalArgumentException("target_profile_invalid: malformed binding");
        }
    }
}
