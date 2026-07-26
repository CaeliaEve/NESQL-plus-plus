package com.github.dcysteine.nesql.exporter.util;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;

/** Utility class containing methods for generating unique row IDs. */
public final class IdUtil {
    // Static class.
    private IdUtil() {}

    /** This string needs to be URL parameter-safe, as well as file system-safe. */
    public static final String ID_SEPARATOR = "~";

    public static String itemId(ItemStack itemStack) {
        ItemStack identityStack = stableIdentityCopy(itemStack);
        String id = itemId(identityStack.getItem());
        id += ID_SEPARATOR + identityStack.getItemDamage();

        NBTTagCompound nbt = identityStack.getTagCompound();
        if (nbt != null) {
            id += ID_SEPARATOR + StringUtil.encodeNbt(nbt);
        }

        return id;
    }

    public static ItemStack stableIdentityCopy(ItemStack source) {
        if (source == null || source.getItem() == null) {
            throw new IllegalArgumentException("ItemStack identity source is empty");
        }
        ItemStack normalized = source.copy();
        if (normalized == null) {
            throw new IllegalStateException("ItemStack.copy() returned null");
        }
        normalized.stackSize = 1;
        if (!normalized.isItemStackDamageable()) {
            return normalized;
        }
        normalized.setItemDamage(0);
        NBTTagCompound nbt = normalized.getTagCompound();
        if (nbt == null) {
            return normalized;
        }
        nbt.removeTag("ench");
        nbt.removeTag("RepairCost");
        if (nbt.hasNoTags()) {
            normalized.setTagCompound(null);
        }
        return normalized;
    }

    public static String itemId(Item item) {
        GameRegistry.UniqueIdentifier uniqueId = findUniqueIdentifierSafely(item);
        if (uniqueId == null) {
            String fallbackName =
                    item == null ? "null" : item.getClass().getName() + ID_SEPARATOR + Item.getIdFromItem(item);
            return sanitize("unregistered" + ID_SEPARATOR + fallbackName);
        }
        return sanitize(uniqueId.modId + ID_SEPARATOR + uniqueId.name);
    }

    public static GameRegistry.UniqueIdentifier findUniqueIdentifierSafely(Item item) {
        if (item == null) {
            return null;
        }
        try {
            return GameRegistry.findUniqueIdentifierFor(item);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String imageFilePath(ItemStack itemStack) {
        // Replace the first occurrence of ID_SEPARATOR to get the mod name as its own separate
        // folder.
        String itemId = itemId(itemStack);
        int firstIndex = itemId.indexOf(ID_SEPARATOR);
        return "item" + File.separator + itemId.substring(0, firstIndex) + File.separator
                + itemId.substring(firstIndex + ID_SEPARATOR.length())
                + ".png";
    }

    public static String fluidId(FluidStack fluidStack) {
        String id = fluidId(fluidStack.getFluid());

        NBTTagCompound nbt = fluidStack.tag;
        if (nbt != null) {
            id += ID_SEPARATOR + StringUtil.encodeNbt(nbt);
        }

        return id;
    }

    public static String fluidId(Fluid fluid) {
        String uniqueName = FluidRegistry.getDefaultFluidName(fluid);
        if (uniqueName == null || !uniqueName.contains(":")) {
            String fallbackName =
                    fluid == null ? "null" : fluid.getClass().getName() + ID_SEPARATOR + fluid.getID();
            return sanitize("unregistered" + ID_SEPARATOR + fallbackName);
        }
        int separator = uniqueName.indexOf(':');
        return sanitize(
                uniqueName.substring(0, separator)
                        + ID_SEPARATOR + uniqueName.substring(separator + 1));
    }

    public static String imageFilePath(FluidStack fluidStack) {
        // Replace the first occurrence of ID_SEPARATOR to get the mod name as its own separate
        // folder.
        String fluidId = fluidId(fluidStack.getFluid());
        int firstIndex = fluidId.indexOf(ID_SEPARATOR);
        return "fluid" + File.separator + fluidId.substring(0, firstIndex) + File.separator
                + fluidId.substring(firstIndex + ID_SEPARATOR.length())
                + ".png";
    }

    /**
     * Strips out URL- and file system-unsafe characters.
     *
     * <p>Windows in particular is a bit finicky. We may need to expand this method in the future.
     * See <a href="https://stackoverflow.com/a/48962674">here</a>.
     */
    public static String sanitize(String string) {
        // Note: four backslashes are needed to escape to a single backslash in the target string.
        return string.replaceAll("[<>:\"/\\\\|?*]", "");
    }
}
