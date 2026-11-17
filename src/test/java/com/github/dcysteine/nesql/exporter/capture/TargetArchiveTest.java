package com.github.dcysteine.nesql.exporter.capture;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Actual Sources resolution/hash path, with a declared test-container artifact. */
public final class TargetArchiveTest {
    public static void main(String[] arguments) throws Exception {
        TargetProfile profile = TargetProfile.parse(new String(Files.readAllBytes(Paths.get(arguments[0])), StandardCharsets.UTF_8));
        Path archive = Files.createTempFile("nesql-target-artifact-", ".jar");
        try {
            Files.write(archive, "abc".getBytes(StandardCharsets.UTF_8));
            ModMetadata metadata = new ModMetadata();
            metadata.modId = "Forestry"; metadata.name = "test-only"; metadata.version = "4.11.40";
            ModContainer container = new DummyModContainer(metadata) {
                @Override public File getSource() { return archive.toFile(); }
            };
            Sources sources = new Sources(Collections.singletonList(container), Collections.emptyMap());
            String digest = sources.fingerprint("Forestry");
            if (!"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(digest))
                throw new AssertionError("Actual declared source bytes were not hashed");
            if (!profile.identityProblem("Forestry", "4.11.40", digest).startsWith("target_artifact_mismatch:"))
                throw new AssertionError("Correct version must not admit a different artifact");
            Files.write(archive, "xyz".getBytes(StandardCharsets.UTF_8));
            if (digest.equals(sources.fingerprint("Forestry"))) throw new AssertionError("Changed archive retained stale hash");
            if (TargetAdmission.matches("archive-test", "Forestry", container))
                throw new AssertionError("Unverified family was admitted");
            if (!TargetAdmission.refusals().get("archive-test/Forestry").startsWith("target_family_unverified:"))
                throw new AssertionError("Optional refusal was not recorded");
            System.out.println("TargetArchiveTest: actual Sources bytes, mismatch, changed archive and optional refusal passed");
        } finally { Files.deleteIfExists(archive); }
    }
}
