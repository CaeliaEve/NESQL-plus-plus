package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagString;

/** Owned native MicroRecipe rules. No GUI caches, live material getters or crafting callbacks. */
final class FmpRules implements AutoCloseable {
  private FmpNativeSource source;
  private FmpMaterialIds lookup;
  private Item micro;
  private final List<Material> materials = new ArrayList<>();
  private final Map<Item,Map<Integer,List<Material>>> blockIndex=new IdentityHashMap<>();
  private final List<FmpSawSnapshot> saws = new ArrayList<>();
  private int maximum;

  FmpRules() {
    try {
      source = new FmpNativeSource();
      lookup = new FmpMaterialIds();
      Object registry =
          field(type("codechicken.microblock.MicroMaterialRegistry$"), null, "MODULE$");
      Object proxy =
          field(type("codechicken.microblock.handler.MicroblockProxy$"), null, "MODULE$");
      micro = (Item) field(proxy, "itemMicro");
      if (micro.getClass() != type("codechicken.microblock.ItemMicroPart"))
        throw fault("Unadapted FMP micro item");
      maximum = (Integer) field(registry, "maxCuttingStrength");
      Object[] ids =
          (Object[]) field(registry, "codechicken$microblock$MicroMaterialRegistry$$idMap");
      for (int id = 0; id < ids.length; id++) {
        Jobs.checkpoint();
        Object tuple = ids[id];
        FmpMaterialSnapshot snapshot = new FmpMaterialSnapshot(field(tuple, "_2"));
        materials.add(new Material(id, (String) field(tuple, "_1"), snapshot));
      }
      for(Material material:materials)
        blockIndex.computeIfAbsent(material.block.getItem(),ignored->new HashMap<>())
            .computeIfAbsent(net.minecraft.init.Items.feather.getDamage(material.block),ignored->new ArrayList<>()).add(material);
      Class<?> saw = type("codechicken.microblock.Saw");
      for (Object raw : Item.itemRegistry) {
        Jobs.checkpoint();
        if (saw.isInstance(raw)) saws.add(new FmpSawSnapshot((Item) raw));
      }
      if (saws.size() > 4096) throw fault("FMP saw census exceeds budget");
      saws.sort(Comparator.comparing(s -> Item.itemRegistry.getNameForObject(s.item())));
      finishVerification();
    } catch (RuntimeException | Error error) {
      close();
      throw error;
    }
  }

  Item micro() {
    check();
    return micro;
  }

  int maximum() {
    check();
    return maximum;
  }

  List<Material> materials() {
    check();
    return Collections.unmodifiableList(materials);
  }

  List<FmpSawSnapshot> saws() {
    check();
    return Collections.unmodifiableList(saws);
  }

  JsonObject lookup() {
    check();
    return lookup.records();
  }

  JsonObject declaration() {
    check();
    JsonObject ids = lookup.records();
    JsonArray materialRows = array(), tools = array();
    for (Material material : materials)
      materialRows.add(
          object(
              "id",
              material.id,
              "name",
              material.name,
              "block",
              Identity.item(
                  Item.itemRegistry.getNameForObject(material.block.getItem()),
                  material.block.getItemDamage(),
                  TypedNbt.encode(material.block.getTagCompound())),
              "cutterStrength",
              material.cutter));
    for (FmpSawSnapshot saw : saws) tools.add(saw.declaration());
    return object(
        "materials",
        materialRows,
        "names",
        ids.get("names"),
        "aliases",
        ids.get("aliases"),
        "saws",
        tools,
        "settings",
        array(
            object(
                "nativeRecipe",
                "codechicken.microblock.MicroRecipe",
                "version",
                "1.7.2",
                "microRegistry",
                Item.itemRegistry.getNameForObject(micro),
                "maxSawStrength",
                maximum,
                "aliasPolicy",
                "singleHop",
                "missingPolicy",
                "idZero",
                "missingCompoundPolicy",
                "readOnlyIdZero")));
  }

  int resolveName(String name) {
    check();
    return lookup.resolveName(name);
  }

  void guard() {
    check();
    source.verify();
  }

  int findMaterial(ItemStack offered) {
    check();
    NativeStackAudit.audit(offered);
    try {
      if (offered.getItem().getClass().getMethod("equals", Object.class).getDeclaringClass()
          != Object.class) throw fault("Unadapted FMP item equality callback");
    } catch (NoSuchMethodException error) {
      throw fault("Missing item equality");
    }
    Map<Integer,List<Material>> byMeta=blockIndex.get(offered.getItem());
    List<Material> candidates=byMeta==null?Collections.emptyList():byMeta.getOrDefault(offered.getItemDamage(),Collections.emptyList());
    for (Material material : candidates) {
      ItemStack block = material.block;
      if (offered.getItem() == block.getItem()
          && offered.getItemDamage() == block.getItemDamage()
          && ItemStack.areItemStackTagsEqual(offered, block))
        return lookup.resolveName(material.name);
    }
    return -1;
  }

  int material(ItemStack stack) {
    return stack.getItem() == micro ? lookup.resolve(stack) : findMaterial(stack);
  }

  private int cls(ItemStack stack) {
    return stack.getItem() == micro ? stack.getItemDamage() >> 8 : 0;
  }

  private int size(ItemStack stack) {
    return stack.getItem() == micro ? stack.getItemDamage() & 255 : 8;
  }

  ItemStack create(int amount, int cls, int size, int material) {
    check();
    Material definition = definition(material);
    ItemStack result;
    if (size == 8) result = definition.block.copy();
    else {
      result = new ItemStack(micro, amount, (cls << 8) | size);
      result.setTagInfo("mat", new NBTTagString(definition.name));
    }
    result.stackSize = amount;
    return result;
  }

  private Material definition(int id) {
    if (id < 0 || id >= materials.size())
      throw fault("Native FMP material ID is outside its map: " + id);
    return materials.get(id);
  }

  Outcome evaluate(ItemStack[] grid) {
    check();
    if (grid.length != 9) throw fault("FMP requires nine physical cells");
    for (ItemStack stack : grid) if (stack != null) NativeStackAudit.audit(stack);
    ItemStack first = grid[0];
    if (grid[4] == null && first != null && first.getItem() == micro && cls(first) == 0) {
      int id = material(first);
      boolean ring = true;
      for (int i = 1; i < 9; i++)
        if (i != 4) {
          ItemStack stack = grid[i];
          if (stack == null
              || stack.getItem() != micro
              || material(stack) != id
              || stack.getItemDamage() != first.getItemDamage()) {
            ring = false;
            break;
          }
        }
      if (ring) return outcome("hollow", 8, 1, size(first), id);
    }
    int sum = 0, count = 0, smallest = 0, kind = 0, id = 0;
    boolean glue = true;
    for (ItemStack stack : grid)
      if (stack != null) {
        if (stack.getItem() != micro) {
          glue = false;
          break;
        }
        if (count == 0) {
          sum = size(stack);
          kind = cls(stack);
          id = material(stack);
          count = 1;
          smallest = sum;
        } else if (cls(stack) != kind
            || material(stack) != id
            || (kind >= 2 && size(stack) != smallest)) {
          glue = false;
          break;
        } else {
          smallest = Math.min(smallest, size(stack));
          count++;
          sum += size(stack);
        }
      }
    if (glue && count > 1) {
      if (kind == 3 && count == 2) return outcome("gluing", 1, 0, smallest, id);
      if (kind == 2 && (count == 2 || count == 4))
        return outcome("gluing", 1, count == 2 ? 3 : 0, smallest, id);
      if (kind == 0 || kind == 1) {
        int base = 0;
        for (int bit : new int[] {1, 2, 4})
          if ((bit & sum) != 0) {
            base = bit;
            break;
          }
        if (base == 0) return outcome("gluing", sum / 8, 0, 8, id);
        if (base > smallest) return outcome("gluing", sum / base, kind, base, id);
      }
    }
    int tool = -1;
    FmpSawSnapshot saw = null;
    for (int i = 0; i < 9 && saw == null; i++)
      if (grid[i] != null)
        for (FmpSawSnapshot candidate : saws)
          if (grid[i].getItem() == candidate.item()) {
            saw = candidate;
            tool = i;
            break;
          }
    if (saw != null) {
      if (tool / 3 < 2 && grid[tool + 3] != null) {
        ItemStack part = grid[tool + 3];
        int mat = material(part);
        if (size(part) != 1 && mat >= 0 && can(saw, mat) && only(grid, tool, tool + 3))
          return outcome("thinning", 2, cls(part), size(part) / 2, mat);
      }
      if (tool % 3 < 2 && grid[tool + 1] != null && grid[tool + 1].getItem() == micro) {
        ItemStack part = grid[tool + 1];
        int mat = material(part), kindPart = cls(part);
        if (can(saw, mat)
            && (kindPart == 0 || kindPart == 1 || kindPart == 3)
            && only(grid, tool, tool + 1))
          return outcome("splitting", 2, kindPart == 3 ? 2 : 3, size(part), mat);
      }
    }
    ItemStack cover = null;
    for (ItemStack stack : grid)
      if (stack != null) {
        if (stack.getItem() != micro || cover != null || cls(stack) != 1) return null;
        cover = stack;
      }
    return cover == null ? null : outcome("hollowFill", 1, 0, size(cover), material(cover));
  }

  private static boolean only(ItemStack[] grid, int a, int b) {
    for (int i = 0; i < 9; i++) if (i != a && i != b && grid[i] != null) return false;
    return true;
  }

  private boolean can(FmpSawSnapshot saw, int id) {
    return saw.strength >= definition(id).cutter || saw.strength == maximum;
  }

  private Outcome outcome(String branch, int amount, int cls, int size, int id) {
    return new Outcome(branch, create(amount, cls, size, id), id);
  }

  void verify() {
    check();
    source.verify();
    for (Material material : materials) material.snapshot.verify();
    for (FmpSawSnapshot saw : saws) saw.verify();
  }

  void finishVerification() {
    verify();
    source.finishVerification();
    lookup.verify();
  }

  private void check() {
    Jobs.checkpoint();
    if (micro == null && source == null)
      throw new Jobs.Fault("capture_closed", "FMP rules are closed");
  }

  public void close() {
    blockIndex.clear();
    for (Material material : materials) material.snapshot.close();
    materials.clear();
    for (FmpSawSnapshot saw : saws) saw.close();
    saws.clear();
    if (lookup != null) lookup.close();
    lookup = null;
    if (source != null) source.close();
    source = null;
    micro = null;
  }

  static final class Material {
    final int id, cutter;
    final String name;
    final FmpMaterialSnapshot snapshot;
    final ItemStack block;

    Material(int id, String name, FmpMaterialSnapshot snapshot) {
      this.id = id;
      this.name = name;
      this.snapshot = snapshot;
      block = snapshot.stack();
      cutter = snapshot.cutter();
    }
  }

  static final class Outcome {
    final String branch;
    final ItemStack stack;
    final int materialId;

    Outcome(String branch, ItemStack stack, int materialId) {
      this.branch = branch;
      this.stack = stack;
      this.materialId = materialId;
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
