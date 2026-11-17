package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.*;

/**
 * Pure callback-result semantics. Crafting pickup/container dispatch is a separate native stage.
 */
final class ProjectBlueLogic {
  static final String ROOT = "gcewing.projectblue.ProjectBlue",
      RULE = "gcewing.projectblue.ControlPanelRecipes$";
  static final String[] CORE = {
    "controlPanelItem",
    "miniatureLever",
    "miniatureButton",
    "miniatureLamp",
    "miniatureCover",
    "sprayCan",
    "emptySprayCan",
    "itemMicroPart",
    "itemPartFixture"
  };
  private final Map<String, Item> core;
  private final ProjectBlueMaterials materials;

  ProjectBlueLogic(Map<String, Item> core, ProjectBlueMaterials materials) {
    this.core = core;
    this.materials = materials;
  }

  static String mode(Object recipe) {
    String name = recipe == null ? "" : recipe.getClass().getName();
    if (name.equals(RULE + "CraftControlPanel")) return "panel";
    if (name.equals(RULE + "CraftMiniatureItem")) return "miniature";
    if (name.equals(RULE + "PaintControl")) return "paint";
    throw fault("Unadapted native ProjectBlue rule: " + name);
  }

  Outcome evaluate(String mode, ItemStack[] cells) {
    if (cells.length != 9) throw fault("ProjectBlue semantic examples require nine logical cells");
    ItemStack first = cells[0], second = mode.equals("paint") ? cells[1] : cells[4];
    if (mode.equals("paint")) {
      if (!paintable(first)
          || second == null
          || !type("gcewing.projectblue.SprayCanItem").isInstance(second.getItem()))
        return Outcome.unmatched();
      audit(first);
      audit(second);
      auditPaint(second.getItem());
      int color = second.getTagCompound() == null ? 0 : second.getTagCompound().getInteger("color");
      return Outcome.output(new ItemStack(first.getItem(), 1, color));
    }
    if (!mode.equals("panel") && !mode.equals("miniature"))
      throw fault("Unknown native ProjectBlue branch");
    if (first == null
        || !type("codechicken.microblock.ItemSaw").isInstance(first.getItem())
        || cells[1] != null
        || cells[3] != null) return Outcome.unmatched();
    if (mode.equals("panel")) {
      if (second == null || second.getItem() != core.get("itemMicroPart") || meta(second) != 257)
        return Outcome.unmatched();
      audit(second);
      String material = materialKey(mode, cells);
      return Outcome.output(materials.resolve(material).stack(core.get("controlPanelItem"), 1));
    }
    if (second == null) return Outcome.unmatched();
    Item item = second.getItem();
    ItemStack output = null;
    if (item instanceof ItemBlock) {
      Block block = Block.getBlockFromItem(item);
      if (block == Blocks.lever) output = new ItemStack(core.get("miniatureLever"), 8, 16);
      else if (block == Blocks.stone_button)
        output = new ItemStack(core.get("miniatureButton"), 8, 16);
      else if (block == Blocks.wooden_button)
        output = new ItemStack(core.get("miniatureButton"), 8, 17);
    }
    if (output != null) { audit(second); return Outcome.output(output); }
    if (item == core.get("itemPartFixture")) {
      audit(second);
      return Outcome.output(new ItemStack(core.get("miniatureLamp"), 8, meta(second)));
    }
    if (item != core.get("itemMicroPart")) return Outcome.unmatched();
    audit(second);
    if (meta(second) == 1) {
      if (!second.hasTagCompound() || !second.getTagCompound().hasKey("mat"))
        return Outcome.error("match", "materialTagAbsent");
      return Outcome.output(
          materials.resolve(materialKey(mode, cells)).stack(core.get("miniatureCover"), 8));
    }
    return Outcome.unmatched();
  }

  static void auditPaint(Item item) {
    Class<?> owner = type("gcewing.projectblue.SprayCanItem");
    try {
      for (String method : new String[] {"getColor", "newStack"}) {
        Class<?>[] args =
            method.equals("getColor")
                ? new Class<?>[] {ItemStack.class}
                : new Class<?>[] {int.class, int.class};
        if (item.getClass().getMethod(method, args).getDeclaringClass() != owner)
          throw fault("Unadapted native paint callback: " + item.getClass().getName());
      }
    } catch (NoSuchMethodException error) {
      throw fault("Missing native paint callback");
    }
  }

  static boolean paintable(ItemStack stack) {
    if (stack == null || !type("gcewing.projectblue.ControlItem").isInstance(stack.getItem()))
      return false;
    Object kind = field(stack.getItem(), "type");
    return kind instanceof Enum
        && (((Enum<?>) kind).name().equals("LEVER") || ((Enum<?>) kind).name().equals("BUTTON"));
  }

  static int meta(ItemStack stack) {
    return Items.feather.getDamage(stack);
  }

  static void audit(ItemStack stack) {
    NativeStackAudit.audit(stack);
  }

  /**
   * NBTBase's string conversion is not a cast to NBTTagString. Retain the actual native projection,
   * including numeric suffixes and compound order.
   */
  static String materialKey(String mode, ItemStack[] cells) {
    if (mode.equals("paint") || cells[4] == null) return null;
    ItemStack input = cells[4];
    if (mode.equals("miniature") && input.getItem() != field(type(ROOT), null, "itemMicroPart"))
      return null;
    audit(input);
    if (mode.equals("miniature")
        && meta(input) != 1)
      return null;
    if (!input.hasTagCompound() || !input.getTagCompound().hasKey("mat"))
      return mode.equals("panel") ? "tile.wood" : null;
    return input.getTagCompound().getString("mat");
  }

  static JsonObject raw(ItemStack stack) {
    audit(stack);
    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem())
      throw fault("Unregistered native ProjectBlue item");
    return object(
        "registry",
        registry,
        "meta",
        meta(stack),
        "amount",
        stack.stackSize,
        "nbt",
        TypedNbt.encode(stack.getTagCompound()));
  }

  static final class Outcome {
    final String status, phase, reason;
    final ItemStack output;

    private Outcome(String status, String phase, String reason, ItemStack output) {
      this.status = status;
      this.phase = phase;
      this.reason = reason;
      this.output = output;
    }

    static Outcome unmatched() {
      return new Outcome("unmatched", null, null, null);
    }

    static Outcome output(ItemStack output) {
      return new Outcome("output", null, null, output);
    }

    static Outcome error(String phase, String reason) {
      return new Outcome("error", phase, reason, null);
    }

    JsonObject json() {
      if (output != null) return object("status", status, "stack", raw(output));
      return status.equals("unmatched")
          ? object("status", status)
          : object("status", status, "phase", phase, "reason", reason);
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
