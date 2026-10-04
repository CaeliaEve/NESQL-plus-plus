package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

/** Real Galacticraft role fields, Forge fluid identities and the production animation selection; no game or GL. */
final class NativeFluidIconsTest {
    static void run() throws Exception {
        NativeCoreFixesTest.version("GalacticraftCore", "3.3.13-GTNH");
        ReflectionHelper.setPrivateValue(cpw.mods.fml.common.ModAPIManager.class, cpw.mods.fml.common.ModAPIManager.INSTANCE,
                Collections.emptyMap(), "apiContainers");
        Class<?> core = Class.forName("micdoodle8.mods.galacticraft.core.GalacticraftCore");
        TextureAtlasSprite nativeIcon = sprite("native:oil", 2, 3), ownIcon = sprite("native:override", 5, 7);
        boolean legacy = System.getProperty("nesql.nativeFamily").endsWith("legacy");
        for (String kind : new String[]{"oil", "fuel"}) {
            Fluid active = new Fluid(kind + (legacy ? "gc" : "")) {
                @Override public int getColor(FluidStack stack) { return stack.tag.getInteger("tint"); }
            }.setIcons(nativeIcon);
            Fluid alias = new Fluid(kind + (legacy ? "" : "gc"));
            require(FluidRegistry.registerFluid(active) && FluidRegistry.registerFluid(alias), "Isolated fluid names collided");
            String field = kind.equals("oil") ? "fluidOil" : "fluidFuel";
            core.getField(field).set(null, active);
            FluidStack original = new FluidStack(alias, 17); original.tag = new NBTTagCompound(); original.tag.setInteger("tint", 0x123456);
            Object plan = plan(original);
            require(plan != null, "Textureless Galacticraft compatibility fluid lost its native animation: " + kind + "gc");
            require(MagicApi.field(plan, "sprite") == nativeIcon && (Integer) MagicApi.field(plan, "tint") == 0x123456,
                    "Compatibility icon lost the active native sprite or stack-dependent tint");
            java.util.List<?> timeline = (java.util.List<?>) MagicApi.field(plan, "timeline");
            require(timeline.size() == 2 && ((int[]) timeline.get(0))[1] == 2 && ((int[]) timeline.get(1))[1] == 3,
                    "Compatibility icon lost the native animation timing");
            require(original.getFluid() == alias && original.amount == 17 && original.tag.getInteger("tint") == 0x123456
                    && alias.getIcon(original) == null && alias.getBlock() == null, "Visual selection mutated the source/registry");

            alias.setIcons(ownIcon);
            require(MagicApi.field(plan(original), "sprite") == ownIcon, "An existing alias icon was replaced");
            alias.setIcons(null); active.setIcons(null);
            rejects(original, "Missing primary texture was hidden");
            active.setIcons(nativeIcon); core.getField(field).set(null, active);
        }
        Fluid other = new Fluid("oil_unrelated"); FluidRegistry.registerFluid(other);
        rejects(new FluidStack(other, 1), "Unknown textureless fluid was silently substituted");
        NativeCoreFixesTest.version("GalacticraftCore", "unreviewed");
        rejects(new FluidStack(FluidRegistry.getFluid(legacy ? "oil" : "oilgc"), 1), "Unaudited Galacticraft version was accepted");
        System.out.println("Fluid icons: native oil/fuel aliases, both ID configurations, animation/tint, identity preservation and strict missing-texture boundaries passed");
    }
    private static Object plan(FluidStack stack) throws Exception {
        Method method = Images.class.getDeclaredMethod("plan", Facts.Icon.class); method.setAccessible(true);
        try { return method.invoke(new Images(), new Facts.Icon("fluids", new com.google.gson.JsonObject(), null, stack, stack.getFluid().getName())); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException) throw (RuntimeException) error.getCause();
            throw error;
        }
    }
    private static void rejects(FluidStack stack, String message) throws Exception {
        try { plan(stack); throw new AssertionError(message); }
        catch (Jobs.Fault expected) { require(expected.code.equals("texture_missing"), "Wrong missing-texture diagnostic: " + expected.code); }
    }
    private static TextureAtlasSprite sprite(String name, int first, int second) {
        TextureAtlasSprite sprite = new TextureAtlasSprite(name) {};
        sprite.setIconWidth(1); sprite.setIconHeight(1);
        sprite.setFramesTextureData(Arrays.asList(new int[][]{new int[]{0xff112233}}, new int[][]{new int[]{0xff445566}}));
        ReflectionHelper.setPrivateValue(TextureAtlasSprite.class, sprite, new AnimationMetadataSection(
                Arrays.asList(new AnimationFrame(0, first), new AnimationFrame(1, second)), 1, 1, 1), "animationMetadata");
        return sprite;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
