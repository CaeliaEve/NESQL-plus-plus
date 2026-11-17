package com.github.dcysteine.nesql.exporter.capture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Pure profile identity checks: no Forge client or native callback is initialized. */
public final class TargetProfileTest {
    public static void main(String[] arguments) throws Exception {
        TargetProfile profile = TargetProfile.parse(new String(Files.readAllBytes(Paths.get(arguments[0])), StandardCharsets.UTF_8));
        check("5.09.54.265".equals(profile.version("gregtech_nh")), "release mapping");
        check("2.2.828-experimental".equals(profile.version("IC2")), "explicit exception");
        check(profile.identityProblem("absent", null, null).startsWith("target_mod_unknown"), "unknown");
        check(profile.identityProblem("Forestry", null, null).startsWith("target_mod_missing"), "missing");
        check(profile.identityProblem("Forestry", "old", null).startsWith("target_version_mismatch"), "version");
        check(profile.identityProblem("Forestry", "4.11.40", "wrong").startsWith("target_artifact_mismatch"), "archive");
        check(profile.identityProblem("Forestry", "4.11.40",
            "5d2fc33046bdce188f281892c429ac4b1cd3b3833b66cbf013600e0d66c47058") == null, "identity only");
        check(profile.identityProblem("gregtech", "5.09.54.265",
            "b969e11625a0298416a75b6e39703da795233edb71cb66f25fb9bbf654d75528").startsWith("target_binding_unverified"), "ambiguous Loader");
        check(profile.familyProblem("not-reviewed").startsWith("target_family_unverified"), "identity cannot approve semantics");
        System.out.println("TargetProfileTest: mapping/exception, unknown/missing/mismatch, archive and unverified semantic gates passed");
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
