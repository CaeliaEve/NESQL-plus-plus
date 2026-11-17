package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Audited two-unit-stack repair branch shared by Avaritia and GalaxySpace.
 * Their pinned native managers use the same Item fields and signed int formula.
 * Never execute an unknown repair/damage callback while inspecting a definition.
 */
final class NativeCraftingRepair {
    final Item item;
    final boolean enabled;
    final int maximum;
    final JsonObject state;

    NativeCraftingRepair(Item item) {
        this.item = item;
        if (ItemCallbacks.method(item, "isRepairable", "isRepairable").getDeclaringClass() != Item.class)
            throw fault("Native repair priority requires an audited repairability predicate");
        boolean canRepair = (Boolean)field(item, "canRepair");
        if (canRepair && ItemCallbacks.method(item, "isDamageable", "func_77645_m").getDeclaringClass() != Item.class)
            throw fault("Native repair priority requires an audited damageability predicate");
        maximum = ((Number)cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Item.class, item, "maxDamage", "field_77699_b")).intValue();
        boolean subtypes = (Boolean)cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Item.class, item, "hasSubtypes", "field_77787_bX");
        enabled = canRepair && maximum > 0 && !subtypes;
        if (enabled && (ItemCallbacks.method(item, "getMaxDamage", "func_77612_l").getDeclaringClass() != Item.class
                || ItemCallbacks.method(item, "getDisplayDamage", "getDisplayDamage", net.minecraft.item.ItemStack.class).getDeclaringClass() != Item.class))
            throw fault("Native repair priority requires audited maximum/display damage getters");
        state = object("canRepair", canRepair, "hasSubtypes", subtypes, "maximum", maximum, "enabled", enabled);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
