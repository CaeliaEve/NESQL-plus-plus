package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Reject foreign stack, metadata and NBT callbacks before copying or reading native facts. */
final class NativeStackAudit {
  private NativeStackAudit() {}

  static void audit(ItemStack stack) {
    auditRaw(stack);
    Class<?> owner = ItemCallbacks.method(stack.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass();
    if (owner != Item.class && !fixedMetadata(stack, owner))
      throw fault("Unadapted native metadata callback: " + stack.getItem().getClass().getName());
  }

  /** Only readers publishing a nativeItemMetadata rule can admit computed getters. */
  static void auditLogical(ItemStack stack) {
    auditRaw(stack);
    if (!NativeItemMetadata.computed(stack)) audit(stack);
  }

  /** Admit only fixed points of pinned getters. Normalizing callbacks never touch borrowed stacks. */
  private static boolean fixedMetadata(ItemStack stack, Class<?> owner) {
    if (stack.getItem().getClass() != owner) return false;
    int raw = net.minecraft.init.Items.feather.getDamage(stack);
    switch (owner.getName()) {
      case "wealthyturtle.uiesingularities.UniversalSingularityItem": {
        MagicApi.version("NativeStackAudit","universalsingularities");
        Object definitions = field(stack.getItem(), "universalSingularities");
        if (definitions == null || (definitions.getClass() != ArrayList.class
            && !definitions.getClass().getName().equals("java.util.Arrays$ArrayList")))
          throw fault("Unadapted universal singularity definition storage");
        int size = ((List<?>)definitions).size();
        return size > 0 && size <= 65536 && raw >= 0 && raw < size;
      }
      case "magicbees.item.ItemComb":
        MagicApi.version("NativeStackAudit","MagicBees");
        // The native guard uses 'length < damage', not '<='.
        return raw >= 0 && raw <= MagicApi.type("magicbees.item.types.CombType").getEnumConstants().length;
      case "vazkii.botania.common.item.ItemManaTablet":
        MagicApi.version("NativeStackAudit","Botania");
        return raw == 0;
      case "net.p455w0rd.wirelesscraftingterminal.items.ItemWirelessCraftingTerminal":
        MagicApi.version("NativeStackAudit","ae2wct");
        return raw == 0;
      default: return false;
    }
  }

  /** Content copies read physical metadata with Items.feather, not item.getDamage.
   * This does not admit a callback for native recipe predicates. */
  static void auditRaw(ItemStack stack) {
    if (stack == null
        || stack.getClass() != ItemStack.class
        || stack.getItem() == null) throw fault("Unadapted native stack owner or missing item");
    nbt(stack.getTagCompound());
  }

  static void nbt(net.minecraft.nbt.NBTBase tag) {
    auditNbt(tag, 0, new int[1]);
  }

  static void identityOwner(Object value) {
    if (value == null) return;
    try {
      if (value.getClass().getMethod("hashCode").getDeclaringClass() != Object.class
          || value.getClass().getMethod("equals", Object.class).getDeclaringClass() != Object.class)
        throw fault(
            "Unadapted native registry hash/equality callback: " + value.getClass().getName());
    } catch (NoSuchMethodException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static void auditNbt(net.minecraft.nbt.NBTBase tag, int depth, int[] nodes) {
    if (tag == null) return;
    if (depth > 48 || ++nodes[0] > 65536) throw fault("Native stack NBT exceeds budget");
    Class<?> owner = tag.getClass();
    if (owner == net.minecraft.nbt.NBTTagCompound.class) {
      net.minecraft.nbt.NBTTagCompound compound = (net.minecraft.nbt.NBTTagCompound) tag;
      Object backing = field(compound, "tagMap");
      if (!compoundStorage(backing))
        throw fault("Unadapted native compound NBT storage");
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) backing).entrySet()) {
        if (!(entry.getKey() instanceof String)
            || !(entry.getValue() instanceof net.minecraft.nbt.NBTBase))
          throw fault("Invalid native compound NBT entry");
        auditNbt((net.minecraft.nbt.NBTBase) entry.getValue(), depth + 1, nodes);
      }
      return;
    }
    if (owner == net.minecraft.nbt.NBTTagList.class) {
      Object list = field(tag, "tagList");
      if (list == null || list.getClass() != ArrayList.class)
        throw fault("Unadapted Native stack NBT list storage");
      for (Object child : (List<?>) list) {
        if (!(child instanceof net.minecraft.nbt.NBTBase))
          throw fault("Invalid Native stack NBT child");
        auditNbt((net.minecraft.nbt.NBTBase) child, depth + 1, nodes);
      }
      return;
    }
    for (Class<?> allowed :
        new Class<?>[] {
          net.minecraft.nbt.NBTTagByte.class,
          net.minecraft.nbt.NBTTagShort.class,
          net.minecraft.nbt.NBTTagInt.class,
          net.minecraft.nbt.NBTTagLong.class,
          net.minecraft.nbt.NBTTagFloat.class,
          net.minecraft.nbt.NBTTagDouble.class,
          net.minecraft.nbt.NBTTagByteArray.class,
          net.minecraft.nbt.NBTTagString.class,
          net.minecraft.nbt.NBTTagIntArray.class
        }) if (owner == allowed) return;
    throw fault("Unadapted Native stack NBT callback: " + owner.getName());
  }

  private static boolean compoundStorage(Object backing) {
    if (backing == null) return false;
    if (backing.getClass() == HashMap.class) return true;
    String owner = "it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap";
    if (!backing.getClass().getName().equals(owner) || backing.getClass() != MagicApi.type(owner)) return false;
    MagicApi.version("NativeStackAudit","hodgepodge");
    return field(backing, "defRetValue") == null;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
