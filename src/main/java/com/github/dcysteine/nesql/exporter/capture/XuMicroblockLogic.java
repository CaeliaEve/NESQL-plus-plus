package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Installed XU matcher and result on owned values; no live recipe/material callback. */
final class XuMicroblockLogic implements AutoCloseable {
  private Item micro;
  private MicroblockMaterialTable materials;
  private final int width, height;
  private Object[] template;
  private ItemStack prototype;

  XuMicroblockLogic(
      XuMicroblockSource.Entry source, Item micro, MicroblockMaterialTable materials) {
    this.micro = micro;
    this.materials = materials;
    width = source.width;
    height = source.height;
    if (width <= 3 && height <= 3 && (width != 3 || height != 3 || source.nativeCells.length < 9))
      throw fault("Native XU matcher accesses recipeItems[-1] outside a full 3x3 template");
    template = new Object[source.nativeCells.length];
    for (int i = 0; i < template.length; i++) {
      Object value = source.nativeCells[i];
      template[i] = value instanceof ItemStack ? ((ItemStack) value).copy() : value;
    }
    prototype = source.nativeOutput.copy();
  }

  Outcome evaluate(ItemStack[] grid) {
    check();
    if (grid.length != 9) throw fault("XU native witness requires nine physical cells");
    for (ItemStack input : grid) if (input != null) NativeStackAudit.audit(input);
    if (width > 3 || height > 3) return null;
    for (boolean mirror : new boolean[] {true, false}) {
      Integer material = match(grid, mirror);
      if (material != null) {
        int first = -1;
        ItemStack output = prototype.copy();
        for (int cell = 0; cell < 9; cell++)
          if (grid[cell] != null
              && grid[cell].getItem() == micro
              && grid[cell].getTagCompound() != null) {
            first = cell;
            output.setTagCompound((NBTTagCompound) grid[cell].getTagCompound().copy());
            break;
          }
        return new Outcome(output, mirror, material, first);
      }
    }
    return null;
  }

  private Integer match(ItemStack[] grid, boolean mirror) {
    int material = 0;
    // Native checkMatch visits columns first. The result, separately, scans rows first.
    for (int x = 0; x < 3; x++)
      for (int y = 0; y < 3; y++) {
        Object expected = template[(mirror ? 2 - x : x) + y * 3];
        ItemStack offered = grid[x + y * 3];
        if (expected == null || expected instanceof ItemStack) {
          ItemStack literal = (ItemStack) expected;
          if (offered == null && literal == null) continue;
          if (offered == null
              || literal == null
              || offered.getItem() != literal.getItem()
              || literal.getItemDamage() != 32767
                  && literal.getItemDamage() != offered.getItemDamage()) return null;
        } else if (expected instanceof Integer) {
          if (offered == null) return null;
          int id;
          if ((Integer) expected == 0) {
            if (offered.getItem() == micro) return null;
            id = materials.find(offered);
            if (id <= 0) return null;
          } else {
            if (offered.getItem() != micro || offered.getTagCompound() == null) return null;
            id = materials.resolve(offered);
            if (id == 0) return null;
          }
          if (material == 0) material = id;
          else if (material != id) return null;
        } else throw fault("Unadapted native XU template cell");
      }
    return material;
  }

  ItemStack prototype() {
    check();
    return prototype.copy();
  }

  private void check() {
    Jobs.checkpoint();
    if (micro == null) throw new Jobs.Fault("capture_closed", "XU native rule is closed");
  }

  public void close() {
    micro = null;
    materials = null;
    template = null;
    prototype = null;
  }

  static final class Outcome {
    final ItemStack output;
    final boolean mirrored;
    final int materialId, firstTaggedCell;

    Outcome(ItemStack output, boolean mirrored, int materialId, int firstTaggedCell) {
      this.output = output;
      this.mirrored = mirrored;
      this.materialId = materialId;
      this.firstTaggedCell = firstTaggedCell;
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
