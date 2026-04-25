package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IConnectable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.common.blocks.ItemMachines;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

final class GregTechAnimationDetector {
    private static final ForgeDirection INVENTORY_FACING = ForgeDirection.WEST;
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

            if (metaTileEntity instanceof MetaPipeEntity) {
                return hasAnimatedPipeTexture((MetaPipeEntity) metaTileEntity, baseMetaTileEntity, stack);
            }

            for (boolean active : new boolean[] { true, false }) {
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
        if (!Logger.intermittentLog(0)) {
            return;
        }
        Logger.MOD.info(
                "Detected animated GregTech machine item: {} / {} (side={}, active={})",
                stack.getItem().getUnlocalizedName(),
                metaTileEntity.getMetaName(),
                side,
                active);
    }
}
