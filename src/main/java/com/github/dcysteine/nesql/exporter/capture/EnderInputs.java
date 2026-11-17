package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Finite native RecipeInput predicates shared by the audited EnderIO machines. */
final class EnderInputs {
  static final String INPUT = "crazypants.enderio.machine.recipe.RecipeInput",
      ORE = "crazypants.enderio.machine.recipe.OreDictionaryRecipeInput";

  static void check(Object input) {
    if (input == null
        || !(input.getClass().getName().equals(INPUT) || input.getClass().getName().equals(ORE)))
      throw fault("Unknown EnderIO input predicate");
  }

  static List<Pattern> patterns(Object input) {
    check(input);
    List<Pattern> result = new ArrayList<>();
    ItemStack item = (ItemStack) field(type(INPUT), input, "input");
    if (item == null) return result;
    if (input.getClass().getName().equals(ORE)) {
      int ore = (Integer) field(input, "oreId");
      if (ore >= 0)
        for (ItemStack candidate : OreDictionary.getOres(ore))
          result.add(new Pattern(candidate, candidate.getItemDamage() == 32767));
    } else result.add(new Pattern(item, !(Boolean) field(type(INPUT), input, "useMeta")));
    if (result.size() > 65536) throw fault("EnderIO input alternatives exceed budget");
    return result;
  }

  static final class Pattern {
    final ItemStack item;
    final boolean anyMeta;
    final Integer oreCode;

    Pattern(ItemStack source, boolean anyMeta) {
      this(source, anyMeta, null);
    }

    Pattern(ItemStack source, boolean anyMeta, Integer oreCode) {
      if (source == null || source.getItem() == null) throw fault("Invalid EnderIO input example");
      NativeStackAudit.audit(source);
      NativeStackAudit.identityOwner(source.getItem());
      if (oreCode != null && (oreCode <= 0 || oreCode > 65535 || anyMeta))
        throw fault("Invalid baked native metadata key");
      try {
        if (source.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass()
            != Item.class) throw fault("EnderIO input uses an unknown metadata getter");
      } catch (NoSuchMethodException error) {
        throw fault("Missing native metadata getter");
      }
      item = source.copy();
      item.stackSize = 1;
      item.setTagCompound(null);
      this.anyMeta = anyMeta;
      this.oreCode = oreCode;
      if (anyMeta) item.setItemDamage(0);
    }

    boolean covers(Pattern other) {
      return item.getItem() == other.item.getItem()
          && (anyMeta
              || !other.anyMeta
                  && (oreCode != null
                      ? other.oreCode != null ? oreCode.equals(other.oreCode) : matches(other.item)
                      : other.oreCode == null
                          && item.getItemDamage() == other.item.getItemDamage()));
    }

    boolean overlaps(Pattern other) {
      return item.getItem() == other.item.getItem()
          && (anyMeta
              || other.anyMeta
              || oreCode != null && other.oreCode != null && oreCode.equals(other.oreCode)
              || oreCode == null && other.matches(item)
              || other.oreCode == null && matches(other.item));
    }

    boolean matches(ItemStack other) {
      return item.getItem() == other.getItem()
          && (anyMeta
              || (oreCode != null
                  ? oreCode == ((other.getItemDamage() + 1) & 65535)
                  : item.getItemDamage() == other.getItemDamage()));
    }

    Pattern intersect(Pattern other) {
      if (!overlaps(other)) return null;
      Pattern p = anyMeta || oreCode != null && other.oreCode == null ? other : this;
      return new Pattern(p.item, p.anyMeta, p.oreCode);
    }

    JsonObject rule() {
      return oreCode == null
          ? object("kind", "wildcard", "meta", anyMeta, "nbt", true)
          : object("kind", "nativeMetadataKey", "code", oreCode);
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
