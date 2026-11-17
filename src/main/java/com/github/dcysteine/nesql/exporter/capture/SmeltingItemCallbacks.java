package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.*;

/** Audited item values used by native furnace selection, without unrelated callbacks. */
final class SmeltingItemCallbacks {
  private SmeltingItemCallbacks() {}

  static int healing(ItemStack stack) {
    Item item = stack.getItem();
    Class<?> owner = ItemCallbacks.method(item, ItemFood.class, "func_150905_g", "func_150905_g", ItemStack.class).getDeclaringClass();
    if (owner == ItemFood.class || owner == ItemFishFood.class
        && ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() == Item.class)
      return ((ItemFood)item).func_150905_g(stack.copy());
    if (owner.getName().equals("mods.natura.items.NSpecialFood")
        && item.getClass().getName().equals("mods.natura.items.ImpMeat")
        && ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() == Item.class) {
      MagicApi.version("SmeltingItemCallbacks","Natura");
      Object raw = field(owner, item, "hunger");
      int metadata = Items.feather.getDamage(stack);
      if (!(raw instanceof int[]) || metadata < 0 || metadata >= ((int[])raw).length)
        throw unsupported("Natura food metadata is outside its native hunger table");
      return ((int[])raw)[metadata];
    }
    if (owner.getName().equals("gtPlusPlus.core.item.food.BaseItemMetaFood")
        && item.getClass() == owner
        && ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() == Item.class) {
      MagicApi.version("SmeltingItemCallbacks","gregtech_nh");
      Object raw = field(owner, null, "mHealAmountMap");
      if (raw == null || raw.getClass() != java.util.HashMap.class)
        throw unsupported("GT++ food healing table has an unadapted owner");
      Object healing = ((java.util.Map<?, ?>)raw).get(Items.feather.getDamage(stack));
      if (!(healing instanceof Integer))
        throw unsupported("GT++ food metadata has no native healing value");
      return (Integer)healing;
    }
    throw unsupported("Et Futurum food healing callback requires an adapter: " + item.getClass().getName());
  }

  /** -1 is Forge's sentinel for looking up the native experience tables. */
  static float experience(ItemStack stack) {
    Item item = stack.getItem();
    Class<?> owner = ItemCallbacks.method(item, "getSmeltingExperience", "getSmeltingExperience", ItemStack.class).getDeclaringClass();
    if (owner == Item.class) return -1f;
    if (owner.getName().equals("mods.railcraft.common.items.ItemRailcraft")) {
      MagicApi.version("SmeltingItemCallbacks","Railcraft");
      Object raw = field(owner, item, "smeltingExperience");
      if (raw instanceof Float) return (Float)raw;
    }
    throw unsupported("Native furnace output needs a dedicated experience rule: " + item.getClass().getName());
  }

  private static Jobs.Fault unsupported(String text) { return new Jobs.Fault("recipe_unsupported", text); }
}
