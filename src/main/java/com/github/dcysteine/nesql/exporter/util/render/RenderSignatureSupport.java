package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class RenderSignatureSupport {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String CACHE_DIRECTORY = ".cache/render-assets";

    private RenderSignatureSupport() {}

    static boolean matches(File imageDirectory, RenderJob job) {
        try {
            File file = signatureFile(imageDirectory, job);
            if (file == null || !file.exists()) {
                return false;
            }
            try (InputStreamReader reader =
                         new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                SignaturePayload payload = GSON.fromJson(reader, SignaturePayload.class);
                return payload != null
                        && "nesqlpp/render-signature/v1".equals(payload.schemaVersion)
                        && sha256(job.getRenderSignature()).equals(payload.sha256);
            }
        } catch (Throwable e) {
            Logger.MOD.debug(
                    "Failed to compare render signature for {}",
                    job == null ? null : job.getOutputFilePath(),
                    e);
            return false;
        }
    }

    static void write(File imageDirectory, RenderJob job) {
        try {
            File file = signatureFile(imageDirectory, job);
            if (file == null) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            SignaturePayload payload = new SignaturePayload();
            payload.schemaVersion = "nesqlpp/render-signature/v1";
            payload.outputPath = job.getOutputFilePath();
            payload.signature = job.getRenderSignature();
            payload.sha256 = sha256(payload.signature);
            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
                GSON.toJson(payload, writer);
            }
            storeCache(imageDirectory, job, payload.sha256);
        } catch (Throwable e) {
            Logger.MOD.warn(
                    "Failed to write render signature for {}",
                    job == null ? null : job.getOutputFilePath(),
                    e);
        }
    }

    static boolean restoreFromCache(File imageDirectory, RenderJob job) {
        try {
            if (imageDirectory == null || job == null || job.getType() == RenderJob.JobType.ENTITY) {
                return false;
            }
            String signatureHash = sha256(job.getRenderSignature());
            File cacheDir = cacheEntryDirectory(imageDirectory, signatureHash);
            if (cacheDir == null || !cacheDir.isDirectory()) {
                return false;
            }
            File cachedSignature = new File(cacheDir, "render-signature.json");
            if (!cachedSignature.isFile()) {
                return false;
            }
            try (InputStreamReader reader =
                         new InputStreamReader(new FileInputStream(cachedSignature), StandardCharsets.UTF_8)) {
                SignaturePayload payload = GSON.fromJson(reader, SignaturePayload.class);
                if (payload == null
                        || !"nesqlpp/render-signature/v1".equals(payload.schemaVersion)
                        || !signatureHash.equals(payload.sha256)) {
                    return false;
                }
            }

            if (!restoreRequiredFile(imageDirectory, cacheDir, job.getOutputFilePath(), "output")) {
                return false;
            }
            restoreOptionalFile(imageDirectory, cacheDir, job.getRenderContractFilePath(), "render");
            restoreOptionalFile(imageDirectory, cacheDir, job.getSpriteMetadataFilePath(), "sprite");
            restoreOptionalFile(imageDirectory, cacheDir, job.getNativeSpriteAtlasFilePath(), "native-atlas");
            restoreRequiredFile(imageDirectory, cacheDir, job.getRenderSignatureFilePath(), "signature");
            return matches(imageDirectory, job);
        } catch (Throwable e) {
            Logger.MOD.debug(
                    "Failed to restore render cache for {}",
                    job == null ? null : job.getOutputFilePath(),
                    e);
            return false;
        }
    }

    private static void storeCache(File imageDirectory, RenderJob job, String signatureHash) {
        try {
            if (imageDirectory == null || job == null || job.getType() == RenderJob.JobType.ENTITY) {
                return;
            }
            File cacheDir = cacheEntryDirectory(imageDirectory, signatureHash);
            if (cacheDir == null) {
                return;
            }
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                return;
            }
            copyIfPresent(new File(imageDirectory, job.getOutputFilePath()), new File(cacheDir, "output"));
            copyIfPresent(new File(imageDirectory, job.getRenderContractFilePath()), new File(cacheDir, "render"));
            copyIfPresent(new File(imageDirectory, job.getSpriteMetadataFilePath()), new File(cacheDir, "sprite"));
            copyIfPresent(new File(imageDirectory, job.getNativeSpriteAtlasFilePath()), new File(cacheDir, "native-atlas"));
            copyIfPresent(new File(imageDirectory, job.getRenderSignatureFilePath()), new File(cacheDir, "signature"));
            SignaturePayload payload = new SignaturePayload();
            payload.schemaVersion = "nesqlpp/render-signature/v1";
            payload.outputPath = job.getOutputFilePath();
            payload.signature = job.getRenderSignature();
            payload.sha256 = signatureHash;
            try (OutputStreamWriter writer =
                         new OutputStreamWriter(new FileOutputStream(new File(cacheDir, "render-signature.json"), false), StandardCharsets.UTF_8)) {
                GSON.toJson(payload, writer);
            }
        } catch (Throwable e) {
            Logger.MOD.debug(
                    "Failed to store render cache for {}",
                    job == null ? null : job.getOutputFilePath(),
                    e);
        }
    }

    private static File cacheEntryDirectory(File imageDirectory, String signatureHash) {
        File exportDirectory = imageDirectory == null ? null : imageDirectory.getParentFile();
        File nesqlDirectory = exportDirectory == null ? null : exportDirectory.getParentFile();
        if (nesqlDirectory == null || signatureHash == null || signatureHash.length() < 4) {
            return null;
        }
        return new File(new File(nesqlDirectory, CACHE_DIRECTORY), signatureHash.substring(0, 2) + File.separator + signatureHash);
    }

    private static boolean restoreRequiredFile(File imageDirectory, File cacheDir, String relativePath, String cacheName) throws Exception {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        File cached = new File(cacheDir, cacheName);
        if (!cached.isFile() || cached.length() <= 0L) {
            return false;
        }
        copyFile(cached, new File(imageDirectory, relativePath));
        return true;
    }

    private static void restoreOptionalFile(File imageDirectory, File cacheDir, String relativePath, String cacheName) throws Exception {
        if (relativePath == null || relativePath.isEmpty()) {
            return;
        }
        File cached = new File(cacheDir, cacheName);
        if (cached.isFile() && cached.length() > 0L) {
            copyFile(cached, new File(imageDirectory, relativePath));
        }
    }

    private static void copyIfPresent(File source, File target) throws Exception {
        if (source != null && source.isFile() && source.length() > 0L) {
            copyFile(source, target);
        }
    }

    private static void copyFile(File source, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        byte[] buffer = new byte[1024 * 1024];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private static File signatureFile(File imageDirectory, RenderJob job) {
        if (imageDirectory == null || job == null || job.getRenderSignatureFilePath() == null) {
            return null;
        }
        return new File(imageDirectory, job.getRenderSignatureFilePath());
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        byte[] bytes = digest.digest();
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }

    private static final class SignaturePayload {
        String schemaVersion;
        String outputPath;
        String signature;
        String sha256;
    }
}
