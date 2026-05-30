package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IConnectable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.common.blocks.ItemMachines;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Locale;

final class GregTechAnimationDetector {
    private static final ForgeDirection INVENTORY_FACING = ForgeDirection.WEST;
    private static int animatedMachineLogCount = 0;
    private static final ForgeDirection[] INVENTORY_SIDES = {
            ForgeDirection.DOWN,
            ForgeDirection.UP,
            ForgeDirection.NORTH,
            ForgeDirection.SOUTH,
            ForgeDirection.WEST,
            ForgeDirection.EAST
    };

    private GregTechAnimationDetector() {}

    static boolean hasAnimatedMachineTexture(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMachines)) {
            return false;
        }

        try {
            IMetaTileEntity metaTileEntity = ItemMachines.getMetaTileEntity(stack);
            if (metaTileEntity == null) {
                return false;
            }

            IGregTechTileEntity baseMetaTileEntity = metaTileEntity.getBaseMetaTileEntity();
            if (baseMetaTileEntity == null) {
                return false;
            }

            String metaName = safeMetaName(metaTileEntity);
            if (!shouldInspectMachineFramebufferAnimation(stack, metaName, metaTileEntity)) {
                return false;
            }

            if (metaTileEntity instanceof MetaPipeEntity) {
                return hasAnimatedPipeTexture((MetaPipeEntity) metaTileEntity, baseMetaTileEntity, stack);
            }

            for (boolean active : new boolean[] { false, true }) {
                for (ForgeDirection side : INVENTORY_SIDES) {
                    ITexture[] textures =
                            metaTileEntity.getTexture(
                                    baseMetaTileEntity,
                                    side,
                                    INVENTORY_FACING,
                                    -1,
                                    active,
                                    false);
                    if (TextureAnimationInspector.containsAnimatedIcon(textures)) {
                        logAnimatedMachine(stack, metaTileEntity, side, active);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            Logger.MOD.debug(
                    "Failed to inspect GregTech machine animation for {}",
                    stack,
                    t);
        }

        return false;
    }

    private static boolean shouldInspectMachineFramebufferAnimation(
            ItemStack stack,
            String metaName,
            IMetaTileEntity metaTileEntity) {
        if (metaName.isEmpty()) {
            return false;
        }

        // Inventory rendering of ordinary GT single-block machines uses a static side icon. Their
        // active world overlay often references animated textures, and reflecting into every side of
        // every tier makes thousands of static machines look animated. On Java 25 + LWJGL3ify this
        // floods framebuffer GIF capture and can hard-exit the client. Keep framebuffer capture for
        // explicit high-energy / cosmic machines where the item form is expected to be animated.
        if (isDefinitelyStaticMachineFamily(metaName)) {
            return false;
        }

        if (metaTileEntity instanceof MetaPipeEntity) {
            return isExplicitAnimatedMachineFamily(metaName);
        }

        if (hasInventoryRenderer(stack)) {
            return isExplicitAnimatedMachineFamily(metaName);
        }

        return isExplicitAnimatedMachineFamily(metaName);
    }

    private static boolean isDefinitelyStaticMachineFamily(String metaName) {
        return metaName.startsWith("basicmachine.")
                || metaName.startsWith("simplemachine.")
                || metaName.startsWith("hatch.")
                || metaName.startsWith("bus.")
                || metaName.startsWith("pipe.")
                || metaName.startsWith("cable.")
                || metaName.startsWith("transformer.")
                || metaName.startsWith("charger.")
                || metaName.startsWith("batterybuffer.")
                || metaName.startsWith("cover.")
                || metaName.startsWith("component.");
    }

    private static boolean isExplicitAnimatedMachineFamily(String metaName) {
        return containsAny(metaName,
                "eye_of_harmony",
                "spacetime",
                "space_time",
                "black_hole",
                "wormhole",
                "stellar",
                "cosmic",
                "plasma",
                "fusion",
                "laser",
                "quantum",
                "dimensional",
                "antimatter",
                "dyson",
                "naquadah",
                "reactor",
                "active_animation",
                "animated");
    }

    private static boolean hasInventoryRenderer(ItemStack stack) {
        try {
            return stack != null
                    && MinecraftForgeClient.getItemRenderer(
                            stack,
                            net.minecraftforge.client.IItemRenderer.ItemRenderType.INVENTORY) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String safeMetaName(IMetaTileEntity metaTileEntity) {
        try {
            String metaName = metaTileEntity == null ? null : metaTileEntity.getMetaName();
            return metaName == null ? "" : metaName.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnimatedPipeTexture(
            MetaPipeEntity metaPipeEntity,
            IGregTechTileEntity baseMetaTileEntity,
            ItemStack stack) {
        int inventoryConnections = IConnectable.CONNECTED_WEST | IConnectable.CONNECTED_EAST;
        ITexture[] sideTexture =
                metaPipeEntity.getTexture(
                        baseMetaTileEntity,
                        ForgeDirection.DOWN,
                        inventoryConnections,
                        -1,
                        false,
                        false);
        if (TextureAnimationInspector.containsAnimatedIcon(sideTexture)) {
            logAnimatedMachine(stack, metaPipeEntity, ForgeDirection.DOWN, false);
            return true;
        }

        ITexture[] endTexture =
                metaPipeEntity.getTexture(
                        baseMetaTileEntity,
                        ForgeDirection.WEST,
                        inventoryConnections,
                        -1,
                        true,
                        false);
        if (TextureAnimationInspector.containsAnimatedIcon(endTexture)) {
            logAnimatedMachine(stack, metaPipeEntity, ForgeDirection.WEST, true);
            return true;
        }

        return false;
    }

    private static void logAnimatedMachine(
            ItemStack stack,
            IMetaTileEntity metaTileEntity,
            ForgeDirection side,
            boolean active) {
        animatedMachineLogCount++;
        if (!Logger.intermittentLog(animatedMachineLogCount)) {
            return;
        }
        Logger.MOD.info(
                "Detected {} animated GregTech machine candidates so far; latest: {} / {} (side={}, active={})",
                animatedMachineLogCount,
                stack.getItem().getUnlocalizedName(),
                metaTileEntity.getMetaName(),
                side,
                active);
    }
}
