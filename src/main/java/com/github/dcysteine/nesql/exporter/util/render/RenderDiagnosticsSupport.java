package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import cpw.mods.fml.client.FMLClientHandler;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.blocks.ItemMachines;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Writes tiny last-known-position markers before dangerous client-thread export work. */
public final class RenderDiagnosticsSupport {
    private RenderDiagnosticsSupport() {}

    public static void writeCurrentRenderJob(File imageDirectory, RenderJob job) {
        File diagnosticsDir = diagnosticsDirForImageDirectory(imageDirectory);
        writeJson(new File(diagnosticsDir, "current-render-job.json"), describeRenderJob(job));
    }

    public static void clearCurrentRenderJob(File imageDirectory) {
        deleteQuietly(new File(diagnosticsDirForImageDirectory(imageDirectory), "current-render-job.json"));
    }

    public static void writeCurrentNeiItem(ItemStack stack, int index, int total) {
        File diagnosticsDir = diagnosticsDirForMinecraft();
        writeJson(new File(diagnosticsDir, "current-nei-item.json"), describeNeiItem(stack, index, total));
    }

    public static void writeCurrentCraftingRecipe(IRecipe recipe, int index, int total) {
        File diagnosticsDir = diagnosticsDirForMinecraft();
        writeJson(new File(diagnosticsDir, "current-crafting-recipe.json"), describeCraftingRecipe(recipe, index, total));
    }

    private static File diagnosticsDirForImageDirectory(File imageDirectory) {
        File repositoryDir = imageDirectory == null ? null : imageDirectory.getParentFile();
        if (repositoryDir == null) {
            return diagnosticsDirForMinecraft();
        }
        return new File(repositoryDir, "diagnostics");
    }

    private static File diagnosticsDirForMinecraft() {
        File root;
        try {
            Minecraft minecraft = FMLClientHandler.instance().getClient();
            root = minecraft == null ? null : minecraft.mcDataDir;
        } catch (Throwable ignored) {
            root = null;
        }
        if (root == null) {
            root = new File(".");
        }
        return new File(root, "nesql" + File.separator + "diagnostics");
    }

    private static String describeRenderJob(RenderJob job) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        field(builder, "timestamp", timestamp(), true);
        field(builder, "javaVersion", safeSystemProperty("java.version"), true);
        field(builder, "javaVendor", safeSystemProperty("java.vendor"), true);
        field(builder, "javaVmName", safeSystemProperty("java.vm.name"), true);
        field(builder, "osName", safeSystemProperty("os.name"), true);
        field(builder, "lwjglVersion", safeLwjglVersion(), true);
        if (job == null) {
            field(builder, "job", "<null>", false);
            builder.append("}\n");
            return builder.toString();
        }
        field(builder, "type", job.getType().name(), true);
        field(builder, "output", job.getOutputFilePath(), true);
        field(builder, "frameIndex", String.valueOf(job.getFrameIndex()), true, false);
        field(builder, "multiFrame", String.valueOf(job.needsMultipleFrames()), true, false);
        field(builder, "animationReason", job.getAnimationDecisionReason(), true);
        field(builder, "renderer", safeRendererClass(job), true);
        if (job.getType() == RenderJob.JobType.ITEM) {
            ItemStack stack = job.getItem();
            field(builder, "itemId", safeItemId(stack), true);
            field(builder, "itemClass", stack == null || stack.getItem() == null ? null : stack.getItem().getClass().getName(), true);
            field(builder, "gregTechMetaName", safeGregTechMetaName(stack), true);
            field(builder, "damage", stack == null ? null : String.valueOf(stack.getItemDamage()), false, false);
        } else if (job.getType() == RenderJob.JobType.FLUID) {
            FluidStack fluid = job.getFluid();
            field(builder, "fluid", fluid == null || fluid.getFluid() == null ? null : fluid.getFluid().getName(), false);
        } else if (job.getType() == RenderJob.JobType.ENTITY && job.getEntity() != null) {
            field(builder, "entity", job.getEntity().getMobName(), false);
        }
        builder.append("}\n");
        return builder.toString();
    }

    private static String describeNeiItem(ItemStack stack, int index, int total) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        field(builder, "timestamp", timestamp(), true);
        field(builder, "index", String.valueOf(index), true, false);
        field(builder, "total", String.valueOf(total), true, false);
        field(builder, "itemId", safeItemId(stack), true);
        field(builder, "itemClass", stack == null || stack.getItem() == null ? null : stack.getItem().getClass().getName(), true);
        field(builder, "damage", stack == null ? null : String.valueOf(stack.getItemDamage()), false, false);
        builder.append("}\n");
        return builder.toString();
    }

    private static String describeCraftingRecipe(IRecipe recipe, int index, int total) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        field(builder, "timestamp", timestamp(), true);
        field(builder, "index", String.valueOf(index), true, false);
        field(builder, "total", String.valueOf(total), true, false);
        field(builder, "recipeClass", recipe == null ? null : recipe.getClass().getName(), true);
        field(builder, "recipeIdentity", recipe == null ? null : recipe.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(recipe)), true);
        field(builder, "outputItemId", safeRecipeOutputId(recipe), false);
        builder.append("}\n");
        return builder.toString();
    }

    private static String safeGregTechMetaName(ItemStack stack) {
        try {
            if (stack == null || !(stack.getItem() instanceof ItemMachines)) {
                return null;
            }
            IMetaTileEntity metaTileEntity = ItemMachines.getMetaTileEntity(stack);
            return metaTileEntity == null ? null : metaTileEntity.getMetaName();
        } catch (Throwable t) {
            return "<gregtech-meta-error:" + t.getClass().getName() + ">";
        }
    }

    private static String safeRecipeOutputId(IRecipe recipe) {
        try {
            return recipe == null ? null : safeItemId(recipe.getRecipeOutput());
        } catch (Throwable t) {
            return "<recipe-output-error:" + t.getClass().getName() + ">";
        }
    }
    private static String safeItemId(ItemStack stack) {
        try {
            return stack == null ? null : IdUtil.itemId(stack);
        } catch (Throwable t) {
            return "<item-id-error:" + t.getClass().getName() + ">";
        }
    }

    private static String safeRendererClass(RenderJob job) {
        try {
            return job == null ? null : job.getInventoryRendererClassName();
        } catch (Throwable t) {
            return "<renderer-error:" + t.getClass().getName() + ">";
        }
    }

    private static String safeSystemProperty(String key) {
        try {
            return System.getProperty(key);
        } catch (Throwable t) {
            return "<system-property-error:" + t.getClass().getName() + ">";
        }
    }

    private static String safeLwjglVersion() {
        try {
            return org.lwjgl.Sys.getVersion();
        } catch (Throwable t) {
            return "<lwjgl-version-error:" + t.getClass().getName() + ">";
        }
    }

    private static void field(StringBuilder builder, String key, String value, boolean comma) {
        field(builder, key, value, comma, true);
    }

    private static void field(StringBuilder builder, String key, String value, boolean comma, boolean quoteValue) {
        builder.append("  \"").append(escape(key)).append("\": ");
        if (value == null) {
            builder.append("null");
        } else if (quoteValue) {
            builder.append("\"").append(escape(value)).append("\"");
        } else {
            builder.append(value);
        }
        if (comma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static void writeJson(File file, String json) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
                writer.print(json);
            }
        } catch (Throwable t) {
            Logger.MOD.warn("Failed to write NESQL diagnostics marker: {}", file, t);
        }
    }

    private static void deleteQuietly(File file) {
        try {
            if (file.exists() && !file.delete()) {
                Logger.MOD.debug("Could not delete diagnostics marker: {}", file);
            }
        } catch (Throwable ignored) {
        }
    }
}

