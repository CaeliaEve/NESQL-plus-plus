package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;

/** AE2FC's two fixed inventory cells, not its inherited shapeless display list. */
final class WirelessTerminalCrafting {
  static final String RECIPE = "com.glodblock.github.loader.recipe.WirelessTerminalRecipe";
  private static final String TERMINAL = "com.glodblock.github.common.item.ItemBaseWirelessTerminal",
      BOOSTER = "net.p455w0rd.wirelesscraftingterminal.items.ItemInfinityBooster",
      FLAG = "infinityBoosterCard";
  private final ItemStack terminal, booster, installed;
  private final JsonArray terminals, boosters;

  static boolean supports(Object recipe) { return recipe != null && recipe.getClass().getName().equals(RECIPE); }

  WirelessTerminalCrafting(IRecipe recipe) {
    MagicApi.version("WirelessTerminalCrafting","ae2fc"); MagicApi.version("WirelessTerminalCrafting","ae2wct");
    if (recipe.getClass() != type(RECIPE) || recipe.getClass().getSuperclass() != ShapelessRecipes.class
        || !FLAG.equals(field(type(TERMINAL), null, FLAG)))
      throw fault("Wireless upgrade native owner or flag changed");
    List<?> display = ((ShapelessRecipes)recipe).recipeItems;
    if (display == null || !display.getClass().getName().equals("java.util.Arrays$ArrayList") || display.size() != 2
        || !(display.get(0) instanceof ItemStack) || !(display.get(1) instanceof ItemStack))
      throw fault("Wireless upgrade display definition changed");
    terminal = ((ItemStack)display.get(0)).copy(); booster = ((ItemStack)display.get(1)).copy();
    installed = ((ItemStack)field(recipe, "installedTerm")).copy();
    NativeStackAudit.auditRaw(terminal); NativeStackAudit.auditRaw(booster); NativeStackAudit.auditRaw(installed);
    if (!type(TERMINAL).isInstance(terminal.getItem()) || !type(BOOSTER).isInstance(booster.getItem())
        || !ItemStack.areItemStacksEqual(booster, (ItemStack)field(type(RECIPE), null, "infinityBoosterCard"))
        || !ItemStack.areItemStacksEqual(patch(terminal), installed))
      throw fault("Wireless upgrade declaration differs from its native result");
    terminals = members(type(TERMINAL)); boosters = members(type(BOOSTER));
  }

  JsonObject definition() {
    return object("native", RECIPE, "terminal", RecipeRow.stackEvidence(terminal),
        "booster", RecipeRow.stackEvidence(booster), "installed", RecipeRow.stackEvidence(installed),
        "terminals", terminals, "boosters", boosters, "flag", FLAG);
  }

  boolean capture(RecipeRow row) {
    ItemStack input = terminal.copy(); input.stackSize = 1;
    // A witness of the admitted uninstalled state; the process retains the full predicate.
    if (input.hasTagCompound() && input.getTagCompound().getBoolean(FLAG)) input.getTagCompound().removeTag(FLAG);
    ItemStack card = booster.copy(); card.stackSize = 1;
    row.nativeCraftingInput(0, Collections.singletonList(new RecipeRow.Ingredient(input, 1, false,
        object("kind", "wildcard", "meta", true, "nbt", true))));
    row.nativeCraftingInput(1, Collections.singletonList(new RecipeRow.Ingredient(card, 1, false,
        object("kind", "wildcard", "meta", true, "nbt", true))));
    row.record.add("grid", object("width", 3, "height", 3,
        "cells", array(0, 1, null, null, null, null, null, null, null), "mirror", false));
    row.record.add("process", object("kind", "wirelessTerminalUpgrade", "version", "1.4.120-gtnh",
        "wirelessVersion", "1.12.7", "nativeRecipe", RECIPE,
        "terminals", terminals, "boosters", boosters, "flag", FLAG));
    Products product = Products.observe(Collections.singletonList(input),
        object("kind", "patch", "set", object(FLAG, object("type", "byte", "value", "1")), "limits", object()),
        WirelessTerminalCrafting::patch, row.facts);
    row.itemOutput(0, product.output, 10000); product.attach(row);
    row.note("ae2fc:wirelessUpgrade", RECIPE + "#matches/getCraftingResult", row.facts.locale().equals("zh_CN")
        ? "仅检查合成格第 0、1 槽；其他槽不参与匹配。第 0 槽须为未装无限增幅卡的原生无线终端，第 1 槽须为增幅卡。结果复制实际终端的物品、元数据、完整 NBT 和堆叠数，再写入增幅标志；展示的是单件样本。工作台仍按每个已占用格消耗一件并处理容器，包含匹配忽略的额外格。"
        : "Only inventory cells 0 and 1 are matched; other cells are ignored. Cell 0 must be a native wireless terminal without an installed infinity booster and cell 1 a booster card. The result copies the offered terminal's item, metadata, complete NBT and stack count, then writes the booster flag. The preview is one item. The workbench still consumes one item and handles containers in every occupied cell, including ignored extra cells.");
    return true;
  }

  private static ItemStack patch(ItemStack input) {
    ItemStack copy = input.copy();
    if (!copy.hasTagCompound()) copy.setTagCompound(new NBTTagCompound());
    copy.getTagCompound().setBoolean(FLAG, true);
    return copy;
  }

  private static JsonArray members(Class<?> parent) {
    SortedSet<String> names = new TreeSet<>();
    for (Object raw : Item.itemRegistry) if (parent.isInstance(raw)) {
      Jobs.checkpoint();
      String name = Item.itemRegistry.getNameForObject(raw);
      if (name == null || Item.itemRegistry.getObject(name) != raw || names.size() >= 65536)
        throw fault("Wireless native item census is invalid or exceeds budget");
      names.add(name);
    }
    if (names.isEmpty()) throw fault("Wireless native item class has no registered member");
    JsonArray result = array(); for (String name : names) result.add(value(name)); return result;
  }
  private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
