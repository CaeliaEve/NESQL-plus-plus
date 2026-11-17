package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import java.io.IOException;
import java.util.*;

/** Explicit required/optional admission, retaining reported optional refusals. */
final class TargetAdmission {
    private static final Map<String, String> REFUSALS = new TreeMap<>();
    private TargetAdmission() {}
    static void require(String family, String id) {
        String reason = problem(family, id, Loader.instance().getIndexedModList().get(id));
        if (reason != null) throw new Jobs.Fault(reason.substring(0, reason.indexOf(':')), reason);
    }
    static boolean matches(String family, String id, ModContainer mod) {
        String reason = problem(family, id, mod);
        if (reason == null) return true;
        boolean changed;
        synchronized (REFUSALS) { changed = !reason.equals(REFUSALS.put(family + "/" + id, reason)); }
        if (changed) java.util.logging.Logger.getLogger("NESQL.target").warning(reason);
        return false;
    }
    static Map<String, String> refusals() {
        synchronized (REFUSALS) { return Collections.unmodifiableMap(new TreeMap<>(REFUSALS)); }
    }
    private static String problem(String family, String id, ModContainer mod) {
        TargetProfile profile = TargetProfile.selected();
        String version = mod == null ? null : mod.getVersion();
        String identity = profile.identityProblem(id, version, null);
        if (identity != null && !identity.startsWith("target_artifact_mismatch:")) return identity;
        // Refuse unaudited callbacks before archive I/O. No family in this target is approved.
        String semantic = profile.familyProblem(family);
        if (semantic != null) return semantic;
        // Sources resolves actual containers/core archives; mcmod.info is never a loaded-owner proof.
        try {
            identity = profile.identityProblem(id, version, Sources.capture().fingerprint(id));
        } catch (IOException | Jobs.Fault error) {
            return "target_artifact_mismatch: " + id + "; actual source unavailable: " + error.getMessage();
        }
        return identity;
    }
}
