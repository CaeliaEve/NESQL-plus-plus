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
        if (System.getProperty("nesql.nativeFamily").endsWith("sludge")) { sludge(); return; }
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
    private static void sludge() throws Exception {
        NativeCoreFixesTest.version("gregtech_nh", "5.09.51.482");
        Fluid fluid = new toxiceverglades.block.BlockDarkWorldSludgeFluid("sludge", 0x1e821e);
        require(FluidRegistry.registerFluid(fluid), "Sludge registry collision");
        // Avoid constructor-side global block/item registration; execute the actual native icon callbacks.
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        gtPlusPlus.core.block.base.BlockBaseFluid block = (gtPlusPlus.core.block.base.BlockBaseFluid)
                ((sun.misc.Unsafe) access.get(null)).allocateInstance(gtPlusPlus.core.block.base.BlockBaseFluid.class);
        ReflectionHelper.setPrivateValue(net.minecraftforge.fluids.BlockFluidBase.class, block, "sludge", "fluidName");
        ReflectionHelper.setPrivateValue(net.minecraftforge.fluids.BlockFluidBase.class, block, fluid, "definedFluid");
        ReflectionHelper.setPrivateValue(gtPlusPlus.core.block.base.BlockBaseFluid.class, block, "Sludge", "name");
        ReflectionHelper.setPrivateValue(gtPlusPlus.core.block.base.BlockBaseFluid.class, block, new net.minecraft.util.IIcon[6], "textureArray");
        java.lang.reflect.Field delegate = net.minecraft.block.Block.class.getField("delegate"); delegate.setAccessible(true);
        delegate.set(block, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(block, net.minecraft.block.Block.class));
        net.minecraft.block.Block.blockRegistry.addObject(3900, "ToxicEverglades:fluidSludge", block);
        fluid.setBlock(block);
        TextureAtlasSprite still = sprite("miscutils:fluid/Fluid_Sludge_Still", 4, 4);
        TextureAtlasSprite flow = sprite("miscutils:fluid/Fluid_Sludge_Flow", 2, 2);
        block.registerBlockIcons(name -> {
            require(name.equals(still.getIconName()) || name.equals(flow.getIconName()), "Unexpected native sludge texture " + name);
            return name.equals(still.getIconName()) ? still : flow;
        });
        FluidStack original = new FluidStack(fluid, 1000); original.tag = new NBTTagCompound(); original.tag.setString("proof", "unchanged");
        require(fluid.getIcon(original) == null && block.getIcon(1, 0) == still, "Native missing-fluid-icon setup changed");
        Object plan = plan(original);
        require(MagicApi.field(plan, "sprite") == still && (Integer) MagicApi.field(plan, "tint") == 0x1e821e,
                "Native sludge still texture/color lost");
        require(((java.util.List<?>) MagicApi.field(plan, "timeline")).size() == 2, "Sludge animation lost");
        require(fluid.getIcon(original) == null && original.getFluid() == fluid && original.amount == 1000
                && original.tag.getString("proof").equals("unchanged"), "Sludge facts or registry were mutated");
        freeze(original); // Same client-thread snapshot boundary as production recipe diagnostics, with no GL.
        fluid.setIcons(flow);
        require(MagicApi.field(plan(original), "sprite") == flow, "Existing fluid icon lost precedence");
        fluid.setIcons(null);
        Fluid unknown = new Fluid("unknown_preflight"); FluidRegistry.registerFluid(unknown);
        try { freeze(new FluidStack(unknown, 1)); throw new AssertionError("Data diagnostic missed a textureless fluid"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("texture_missing"), "Wrong preflight fault"); }
        ReflectionHelper.setPrivateValue(net.minecraftforge.fluids.BlockFluidBase.class, block, "unknown_preflight", "fluidName");
        rejects(original, "Unrelated block fluid accepted");
        ReflectionHelper.setPrivateValue(net.minecraftforge.fluids.BlockFluidBase.class, block, "sludge", "fluidName");
        ReflectionHelper.setPrivateValue(gtPlusPlus.core.block.base.BlockBaseFluid.class, block, new net.minecraft.util.IIcon[6], "textureArray");
        rejects(original, "Missing native block icon hidden");
        block.registerBlockIcons(name -> still);
        NativeCoreFixesTest.version("gregtech_nh", "unreviewed");
        rejects(original, "Unreviewed sludge implementation accepted");
        System.out.println("Sludge: actual native block icon registration, still/tint/animation, immutable facts, diagnostic preflight and strict boundaries passed");
    }
    private static void freeze(FluidStack fluid) throws Exception {
        Class<?> type = Class.forName(Audit.class.getName() + "$Attempt");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(int.class); constructor.setAccessible(true);
        Method method = type.getDeclaredMethod("freeze", Facts.Batch.class); method.setAccessible(true);
        Facts.Batch batch = new Facts.Batch();
        batch.icons.add(new Facts.Icon("fluids", new com.google.gson.JsonObject(), null, fluid, fluid.getFluid().getName()));
        try { method.invoke(constructor.newInstance(0), batch); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException) throw (RuntimeException) error.getCause();
            throw error;
        }
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
