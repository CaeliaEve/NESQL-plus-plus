package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Registry for items that are known to have animated textures in GTNH.
 * This is used to force multi-frame capture for specific items.
 */
public enum AnimatedItemRegistry {
    INSTANCE;

    // Set of item IDs that are known to have animations
    private final Set<String> animatedItemIds = new HashSet<>();
    private final Set<String> framebufferAnimatedModIds = new HashSet<>();
    private final Set<String> singularityModIds = new HashSet<>();
    private int animatedDetectionLogCount = 0;

    private AnimatedItemRegistry() {
        initializeAnimatedItems();
    }

    /**
     * Initialize the list of known animated items.
     * These items will always be captured as multi-frame animations.
     */
    private void initializeAnimatedItems() {
        // GregTech 5 Animated Materials
        addGregTechAnimatedItems();

        // Thaumcraft Animated Items
        addThaumcraftAnimatedItems();

        // Botania Animated Items
        addBotaniaAnimatedItems();

        // Other Animated Items
        addOtherAnimatedItems();

        Logger.MOD.info("Initialized AnimatedItemRegistry with {} animated items",
                       animatedItemIds.size());
    }

    private void addGregTechAnimatedItems() {
        // GT5 glitch/phase materials
        animatedItemIds.add("gregtech:gt.metaitem.01"); // SixPhasedCopper, etc.

        // GT5 machines with animations
        // Most GT5 machine blocks have animated textures when active
        // We'll capture the item forms as static, but the blocks could be animated
    }

    private void addThaumcraftAnimatedItems() {
        // Do not force Thaumcraft aspect/wisp items through framebuffer GIF capture.
        // GTNH exports many NBT-distinct variants during crafting scans, and repeatedly
        // capturing them as GIFs can hard-exit the 2.8.4 client. Plain Thaumcraft animated
        // sprites are handled by native sprite metadata instead.
    }

    private void addBotaniaAnimatedItems() {
        animatedItemIds.add("Botania:manaTablet");
        animatedItemIds.add("Botania:poolMinecart");
        animatedItemIds.add("Botania:manaBottle");
        animatedItemIds.add("Botania:manaPowder");

        // Botania flowers with animation
        animatedItemIds.add("Botania:flower");
    }

    private void addOtherAnimatedItems() {
        // Avaritia - ALL Avaritia items have animated textures
        addAvaritiaAnimatedItems();

        // Railcraft
        animatedItemIds.add("Railcraft:cube");
        animatedItemIds.add("Railcraft:firestone");

        // Extra Utilities
        animatedItemIds.add("extrautils:decorativeBlock1");
        animatedItemIds.add("extrautils:decorativeBlock2");

        // Ender IO
        animatedItemIds.add("enderio:itemBasicCapacitor");
        animatedItemIds.add("enderio:itemMaterial");

        // Fluid items that have animation
        // These are captured as fluid type, not item type
    }

    private void addAvaritiaAnimatedItems() {
        // Avaritia items all have custom animated renders
        // These include rainbow effects, pulsing glows, rotation animations, etc.

        // Infinity Tools (rainbow animation)
        animatedItemIds.add("Avaritia:Infinity_Sword");
        animatedItemIds.add("Avaritia:Infinity_Pickaxe");
        animatedItemIds.add("Avaritia:Infinity_Axe");
        animatedItemIds.add("Avaritia:Infinity_Shovel");
        animatedItemIds.add("Avaritia:Infinity_Bow");
        animatedItemIds.add("Avaritia:Infinity_Helm");
        animatedItemIds.add("Avaritia:Infinity_Chest");
        animatedItemIds.add("Avaritia:Infinity_Pants");
        animatedItemIds.add("Avaritia:Infinity_Shoes");

        // Singularity (black hole rotation animation)
        animatedItemIds.add("Avaritia:Singularity");
        animatedItemIds.add("Avaritia:Resource"); // Same as Singularity

        // Other animated items
        animatedItemIds.add("Avaritia:Akashic_Record");
        animatedItemIds.add("Avaritia:Crystal_Matrix");
        animatedItemIds.add("Avaritia:Matter_Cluster");
        animatedItemIds.add("Avaritia:Neutronium_Compressor");
        animatedItemIds.add("Avaritia:Neutron_Collector");
        animatedItemIds.add("Avaritia:Orb_Armok");
        animatedItemIds.add("Avaritia:Big_Pearl");
        animatedItemIds.add("Avaritia:Endest_Pearl");
        animatedItemIds.add("Avaritia:Cosmic_Meatballs");
        animatedItemIds.add("Avaritia:Ultimate_Stew");
        animatedItemIds.add("Avaritia:Skull_Sword");
        animatedItemIds.add("Avaritia:infinitato");

        // Avaritia crafting items
        animatedItemIds.add("Avaritia:Double_Craft");
        animatedItemIds.add("Avaritia:Triple_Craft");
        animatedItemIds.add("Avaritia:Dire_Craft");

        // Avaritia blocks
        animatedItemIds.add("Avaritia:Resource_Block");
        animatedItemIds.add("Avaritia:Neutronium_Compressor");

        // Add all Avaritia items by mod ID prefix
        animatedItemIds.add("Avaritia");

        // GTNH singularity add-ons commonly render through custom item renderers,
        // mask textures, or shader-like overlays. Treat these as framebuffer
        // animation sources instead of trusting native sprite metadata alone.
        framebufferAnimatedModIds.add("Avaritia");
        framebufferAnimatedModIds.add("avaritia");
        framebufferAnimatedModIds.add("eternalsingularity");
        framebufferAnimatedModIds.add("universalsingularities");
        framebufferAnimatedModIds.add("universal_singularities");
        framebufferAnimatedModIds.add("avaritiaddons");
        framebufferAnimatedModIds.add("avaritiaaddons");
        framebufferAnimatedModIds.add("dreamcraft");

        singularityModIds.addAll(framebufferAnimatedModIds);
    }

    /**
     * Check if an item is known to have animation.
     * @param stack The item stack to check
     * @return true if the item is in the animated registry
     */
    public boolean isAnimatedItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        Item item = stack.getItem();
        String itemId = getItemId(item);

        // Check exact match
        if (animatedItemIds.contains(itemId)) {
            logAnimatedItem(itemId, stack.getItemDamage());
            return true;
        }

        // Check mod ID prefix (e.g., "Avaritia" matches "Avaritia:Infinity_Sword")
        String modId = itemId.split(":")[0];
        if (animatedItemIds.contains(modId)) {
            logAnimatedItem(itemId, stack.getItemDamage());
            return true;
        }

        // Special check for Avaritia items (check if itemId starts with "Avaritia")
        if (itemId.startsWith("Avaritia") || itemId.contains("Avaritia")) {
            logAnimatedItem(itemId, stack.getItemDamage());
            return true;
        }

        if (isSingularityLikeAnimatedItem(stack)) {
            logAnimatedItem(itemId, stack.getItemDamage());
            return true;
        }

        return false;
    }

    /**
     * Whether this item should be captured from the real inventory framebuffer even if native
     * sprite metadata exists.
     *
     * <p>Native atlas playback is excellent for plain animated sprites, but singularity-style
     * GTNH items frequently combine a base sprite with masks, halos, custom renderers, or
     * time-based transforms. For those items the rendered GIF is the authoritative export.</p>
     */
    public boolean requiresFramebufferAnimationCapture(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        String itemId = getItemId(stack.getItem());
        String modId = getModId(itemId);
        if (containsIgnoreCase(framebufferAnimatedModIds, modId)) {
            return true;
        }
        return isSingularityLikeAnimatedItem(stack);
    }

    /**
     * Broad but explicit classifier for singularity / infinity / cosmic material items.
     */
    public boolean isSingularityLikeAnimatedItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        String itemId = getItemId(stack.getItem());
        String modId = getModId(itemId);
        String haystack = joinLower(
                itemId,
                modId,
                safeUnlocalizedName(stack),
                safeDisplayName(stack));

        if (containsIgnoreCase(singularityModIds, modId)) {
            return containsAny(haystack,
                    "singular",
                    "infinity",
                    "infinite",
                    "cosmic",
                    "neutronium",
                    "eternal",
                    "universium",
                    "transcendent");
        }

        return containsAny(haystack,
                "singularity",
                "singularitie",
                "eternalsingularity",
                "universal_singularity",
                "universalsingularity",
                "infinity_",
                "infinity ",
                "cosmicneutronium",
                "transcendentmetal",
                "universium");
    }

    private void logAnimatedItem(String itemId, int damage) {
        animatedDetectionLogCount++;
        if (Logger.intermittentLog(animatedDetectionLogCount)) {
            Logger.MOD.info("Detected {} animated item candidates so far; latest: {} (damage: {})",
                    animatedDetectionLogCount, itemId, damage);
        }
    }

    /**
     * Get the item ID in mod:item format.
     */
    private String getItemId(Item item) {
        try {
            String registryName = item.getUnlocalizedName();
            // Convert from tile.name format to mod:item format
            if (registryName.startsWith("tile.")) {
                registryName = registryName.substring(5);
            } else if (registryName.startsWith("item.")) {
                registryName = registryName.substring(5);
            }

            // Try to get the real registry name
            Object registryNameObj = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
            if (registryNameObj != null) {
                return registryNameObj.toString();
            }

            return registryName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getModId(String itemId) {
        if (itemId == null) {
            return "";
        }
        int colon = itemId.indexOf(':');
        if (colon <= 0) {
            return itemId;
        }
        return itemId.substring(0, colon);
    }

    private boolean containsIgnoreCase(Set<String> values, String candidate) {
        if (candidate == null) {
            return false;
        }
        for (String value : values) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String safeUnlocalizedName(ItemStack stack) {
        try {
            return stack.getUnlocalizedName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String safeDisplayName(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String joinLower(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null) {
                builder.append(' ').append(value);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && haystack.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add an item ID to the animated registry dynamically.
     * @param itemId The item ID (mod:item format)
     */
    public void addAnimatedItem(String itemId) {
        animatedItemIds.add(itemId);
        Logger.MOD.info("Added animated item to registry: {}", itemId);
    }

    /**
     * Get the total number of registered animated items.
     */
    public int getAnimatedItemCount() {
        return animatedItemIds.size();
    }
}
