package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Writes the render backend fact document for the native UI export stream.
 *
 * <p>This keeps backend detection, shader runtime probing, and GL evidence isolated from the
 * render fact coordinator so additional backend probes can evolve without bloating the export
 * writer.</p>
 */
final class AngelicaRenderBackendFactsWriter {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private final String schemaRoot;

    AngelicaRenderBackendFactsWriter(String schemaRoot) {
        this.schemaRoot = schemaRoot;
    }

    String write(File out) throws IOException {
        boolean angelicaPresent = detectAngelica();
        String backend = angelicaPresent ? "angelica" : "minecraft-legacy";
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", schemaRoot + "/backend");
        root.addProperty("generatedAt", utcNow());
        root.addProperty("backend", backend);
        root.addProperty("angelicaPresent", angelicaPresent);
        root.addProperty("irisPresent", classPresent("net.irisshaders.iris.api.v0.IrisApi"));
        root.addProperty("shaderPackInUse", detectShaderPackInUse());
        root.addProperty("shadersEnabled", detectShadersEnabled());
        root.addProperty("optifinePresent", detectOptifine());
        root.addProperty("glVendor", glString(GL11.GL_VENDOR));
        root.addProperty("glRenderer", glString(GL11.GL_RENDERER));
        root.addProperty("glVersion", glString(GL11.GL_VERSION));
        JsonObject evidence = new JsonObject();
        evidence.addProperty("angelicaAccessTransformer", classPresent("com.gtnewhorizons.angelica.loading.AngelicaTweaker")
                || classPresent("com.gtnewhorizons.angelica.Tags")
                || classPresent("com.gtnewhorizons.angelica.glsm.GLStateManager"));
        evidence.addProperty("irisApiClass", "net.irisshaders.iris.api.v0.IrisApi");
        evidence.addProperty("glsmClass", "com.gtnewhorizons.angelica.glsm.GLStateManager");
        root.add("evidence", evidence);
        writeJson(out, root);
        return backend;
    }

    private static boolean detectAngelica() {
        return classPresent("com.gtnewhorizons.angelica.glsm.GLStateManager")
                || classPresent("com.gtnewhorizons.angelica.Tags")
                || classPresent("com.gtnewhorizons.angelica.loading.AngelicaTweaker");
    }

    private static boolean detectOptifine() {
        return classPresent("optifine.OptiFineForgeTweaker")
                || classPresent("Config")
                || classPresent("net.optifine.Config");
    }

    private static boolean detectShaderPackInUse() {
        try {
            Object api = irisApi();
            if (api == null) {
                return false;
            }
            Method method = api.getClass().getMethod("isShaderPackInUse");
            Object value = method.invoke(api);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean detectShadersEnabled() {
        try {
            Object api = irisApi();
            if (api == null) {
                return false;
            }
            Method getConfig = api.getClass().getMethod("getConfig");
            Object config = getConfig.invoke(api);
            if (config == null) {
                return false;
            }
            Method enabled = config.getClass().getMethod("areShadersEnabled");
            Object value = enabled.invoke(config);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object irisApi() {
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method method = type.getMethod("getInstance");
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String glString(int name) {
        try {
            return GL11.glGetString(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeJson(File out, Object value) throws IOException {
        ensureDirectory(out.getParentFile());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out, false), StandardCharsets.UTF_8)) {
            PRETTY_GSON.toJson(value, writer);
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
