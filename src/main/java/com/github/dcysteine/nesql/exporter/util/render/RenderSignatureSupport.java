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
        } catch (Throwable e) {
            Logger.MOD.warn(
                    "Failed to write render signature for {}",
                    job == null ? null : job.getOutputFilePath(),
                    e);
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
