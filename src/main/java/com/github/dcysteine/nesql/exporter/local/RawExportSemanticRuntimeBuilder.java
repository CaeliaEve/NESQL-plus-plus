package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraftforge.common.ForgeVersion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class RawExportSemanticRuntimeBuilder {
    private final ExportContext exportContext;

    RawExportSemanticRuntimeBuilder(ExportContext exportContext) {
        this.exportContext = exportContext;
    }

    SemanticRulePack.RuntimeMetadata build() {
        SemanticRulePack.RuntimeMetadata metadata = new SemanticRulePack.RuntimeMetadata();
        metadata.repositoryName = exportContext.paths.repositoryName;
        metadata.exportProfile = exportContext.profile.profileId;
        metadata.exportSelection = exportContext.selection.describe();
        metadata.javaVersion = System.getProperty("java.version", "");
        metadata.minecraftVersion = safeMinecraftVersion();
        metadata.forgeVersion = safeForgeVersion();
        for (String modId : semanticFingerprintModIds()) {
            String version = safeModVersion(modId);
            if (version != null && !version.trim().isEmpty()) {
                metadata.modVersions.put(modId, version);
            }
        }
        metadata.gtnhFingerprint = semanticFingerprint(metadata.modVersions);
        return metadata;
    }

    private static List<String> semanticFingerprintModIds() {
        ArrayList<String> ids = new ArrayList<String>();
        Collections.addAll(ids,
                "gregtech",
                "NotEnoughItems",
                "angelica",
                "dreamcraft",
                "Thaumcraft",
                "appliedenergistics2",
                "Avaritia",
                "EnderIO",
                "BuildCraft|Core",
                "Forestry",
                "TConstruct",
                "ExtraUtilities",
                "OpenBlocks",
                "GalacticraftCore");
        return ids;
    }

    private static String safeMinecraftVersion() {
        try {
            ModContainer minecraft = Loader.instance().getMinecraftModContainer();
            return minecraft == null ? "" : minecraft.getVersion();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeForgeVersion() {
        try {
            return ForgeVersion.getVersion();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeModVersion(String modId) {
        try {
            ModContainer container = Loader.instance().getIndexedModList().get(modId);
            if (container == null) {
                return "";
            }
            String version = container.getVersion();
            return version == null ? "" : version;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String semanticFingerprint(Map<String, String> modVersions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (modVersions != null) {
                for (Map.Entry<String, String> entry : modVersions.entrySet()) {
                    String line = entry.getKey() + "=" + entry.getValue() + "\n";
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < Math.min(12, bytes.length); index++) {
                builder.append(String.format("%02x", bytes[index] & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return "";
        }
    }

}
