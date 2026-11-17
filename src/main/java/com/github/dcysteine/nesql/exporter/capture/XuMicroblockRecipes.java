package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagString;

/** Shared native matching rules and bounded registry witnesses, without NEI GUI state. */
final class XuMicroblockRecipes implements RegistryRecipes {
  private static final String HANDLER = "com.rwtema.extrautils.nei.MicroBlocksHandler";
  private XuMicroblockSource source;
  private XuMicroblockItem microBinding;
  private MicroblockMaterialTable materials;
  private Item micro;
  private final List<XuMicroblockLogic> rules = new ArrayList<>();
  private final List<Witness> witnesses = new ArrayList<>();
  private final IdentityHashMap<Item, NativeItemVariants> variants = new IdentityHashMap<>();
  private final Set<Facts> published =
      Collections.newSetFromMap(new IdentityHashMap<Facts, Boolean>());
  private JsonObject context;
  private String program;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  XuMicroblockRecipes(TemplateRecipeHandler handler) {
    MagicApi.version("XuMicroblockRecipes","ExtraUtilities");
    MagicApi.version("XuMicroblockRecipes","ForgeMultipart");
    if (!supports(handler)) throw fault("Unknown XU native handler");
    try {
      source = new XuMicroblockSource();
      boolean active = false, lookup = false;
      for (XuMicroblockSource.Entry entry : source.entries()) {
        if (entry.width > 3 || entry.height > 3) continue;
        active = true;
        if (entry.width != 3 || entry.height != 3 || entry.nativeCells.length < 9)
          throw fault("Native XU matcher accesses recipeItems[-1] outside a full 3x3 template");
        for (int cell = 0; cell < 9; cell++)
          if (entry.nativeCells[cell] instanceof Integer) lookup = true;
      }
      if (active) {
        microBinding = new XuMicroblockItem();
        micro = microBinding.item();
        if (lookup) materials = new MicroblockMaterialTable();
        context = declaration();
        if (CanonicalJson.bytes(context).length > 16 * 1024 * 1024)
          throw fault("XU shared material rules exceed budget");
        program = Identity.content("program", object("kind", "xuMicroblock", "rules", context));
      }
      for (XuMicroblockSource.Entry entry : source.entries()) {
        if (entry.width > 3 || entry.height > 3) {
          excluded(entry, "native width or height exceeds the three-cell matching loop");
          continue;
        }
        XuMicroblockLogic logic = new XuMicroblockLogic(entry, micro, materials);
        rules.add(logic);
        boolean full = false, parts = false;
        for (int cell = 0; cell < 9; cell++)
          if (entry.nativeCells[cell] instanceof Integer) {
            if ((Integer) entry.nativeCells[cell] == 0) full = true;
            else parts = true;
          }
        if (!full && !parts) {
          add(entry, logic, new Candidate(null, null, null));
          continue;
        }
        TreeMap<Integer, Candidate> candidates = new TreeMap<>();
        if (full) {
          for (MicroblockMaterialTable.Material material : materials.materials()) {
            int id = materials.find(material.block);
            if (id <= 0) continue;
            JsonArray names = materials.knownNames(id);
            if (parts && names.size() == 0) continue;
            candidates.putIfAbsent(
                id,
                new Candidate(
                    id, material.block.copy(), parts ? names.get(0).getAsString() : null));
          }
        } else
          for (int id : materials.knownIds())
            if (id != 0) {
              JsonArray names = materials.knownNames(id);
              if (names.size() > 0)
                candidates.put(id, new Candidate(id, null, names.get(0).getAsString()));
            }
        if (candidates.isEmpty())
          excluded(
              entry,
              "no registered native material witness satisfies the full-block/part correlation");
        else for (Candidate candidate : candidates.values()) add(entry, logic, candidate);
      }
      finishVerification();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  private JsonObject declaration() {
    JsonArray rows = array(), names = array(), aliases = array();
    if (materials != null) {
      JsonObject source = materials.declaration();
      names = source.getAsJsonArray("names");
      aliases = source.getAsJsonArray("aliases");
      for (MicroblockMaterialTable.Material material : materials.materials())
        rows.add(
            object(
                "id",
                material.id,
                "name",
                material.name,
                "block",
                Identity.item(
                    Item.itemRegistry.getNameForObject(material.block.getItem()),
                    material.block.getItemDamage(),
                    TypedNbt.encode(material.block.getTagCompound()))));
    }
    return object(
        "materials",
        rows,
        "names",
        names,
        "aliases",
        aliases,
        "settings",
        array(
            object(
                "nativeRecipe",
                XuMicroblockSource.RECIPE,
                "version",
                "1.2.12",
                "fmpVersion",
                "1.7.2",
                "microRegistry",
                Item.itemRegistry.getNameForObject(micro),
                "lookup",
                materials == null ? "unused" : "ready",
                "aliasPolicy",
                "singleHop",
                "missingPolicy",
                "idZero")));
  }

  private void excluded(XuMicroblockSource.Entry entry, String reason) {
    witnesses.add(
        new Witness(
            entry,
            null,
            null,
            null,
            object(
                "kind",
                "xu-native-registry-witness-empty",
                "reason",
                reason,
                "nativeIndex",
                entry.index,
                "width",
                entry.width,
                "height",
                entry.height,
                "definition",
                entry.definition(),
                "scope",
                "nativeRegistryWitnesses")));
    budget();
  }

  private void add(XuMicroblockSource.Entry entry, XuMicroblockLogic logic, Candidate candidate) {
    ItemStack[] cells = new ItemStack[9];
    for (int cell = 0; cell < 9; cell++) {
      Object raw = entry.nativeCells[cell];
      if (raw instanceof ItemStack) {
        ItemStack template = (ItemStack) raw;
        if (template.getItemDamage() == 32767) {
          NativeItemVariants choices = variants.get(template.getItem());
          if (choices == null) {
            choices = new NativeItemVariants(template);
            variants.put(template.getItem(), choices);
          }
          cells[cell] = choices.firstObserved();
          if (cells[cell] == null)
            throw fault("XU wildcard literal has no observed concrete native visual specimen");
        } else cells[cell] = template.copy();
      } else if (raw instanceof Integer) {
        if ((Integer) raw == 0) cells[cell] = candidate.block.copy();
        else {
          cells[cell] = new ItemStack(micro, 1, (Integer) raw);
          cells[cell].setTagInfo("mat", new NBTTagString(candidate.name));
        }
      }
      if (cells[cell] != null) cells[cell].stackSize = 1;
    }
    XuMicroblockLogic.Outcome base = logic.evaluate(cells), mirror = logic.evaluate(mirror(cells));
    if (base == null || mirror == null)
      throw fault("Constructed XU registry witness is not admitted by its native rule");
    Integer actual = base.materialId == 0 ? null : base.materialId;
    if (!Objects.equals(candidate.id, actual) || base.materialId != mirror.materialId)
      throw fault("XU witness material correlation changed");
    witnesses.add(new Witness(entry, logic, cells, candidate, null));
    budget();
  }

  private void budget() {
    if (witnesses.size() > 1048576) throw fault("XU native witness expansion exceeds budget");
  }

  private static ItemStack[] mirror(ItemStack[] source) {
    ItemStack[] result = new ItemStack[9];
    for (int cell = 0; cell < 9; cell++) result[cell / 3 * 3 + 2 - cell % 3] = source[cell];
    return result;
  }

  public int size() {
    check();
    return witnesses.size();
  }

  public void verify() {
    check();
    source.verify();
    if (microBinding != null) microBinding.verify();
    if (materials != null) materials.verify();
    for (NativeItemVariants item : variants.values()) item.verify();
  }

  public void finishVerification() {
    verify();
    source.finishVerification();
    if (materials != null) materials.finishVerification();
  }

  public String program(Facts facts) {
    verify();
    if (program == null) return null;
    if (!published.contains(facts)) {
      if (materials != null)
        for (MicroblockMaterialTable.Material material : materials.materials())
          facts.item(material.block);
      for (String section : new String[] {"materials", "names", "aliases", "settings"}) {
        JsonArray source = context.getAsJsonArray(section), rows = array();
        int offset = 0, bytes = 0;
        for (JsonElement value : source) {
          Jobs.checkpoint();
          int size = CanonicalJson.bytes(value).length + 1;
          if (size > 900000) throw fault("XU shared rule leaf exceeds budget");
          if (rows.size() == 128 || bytes + size > 900000) {
            chunk(facts, section, offset, rows);
            offset += rows.size();
            rows = array();
            bytes = 0;
          }
          rows.add(value);
          bytes += size;
        }
        if (rows.size() > 0 || source.size() == 0) chunk(facts, section, offset, rows);
      }
      published.add(facts);
    }
    return program;
  }

  private void chunk(Facts facts, String section, int offset, JsonArray rows) {
    JsonObject chunk =
        object(
            "program",
            program,
            "offset",
            offset,
            "data",
            object(
                "kind",
                "xu" + Character.toUpperCase(section.charAt(0)) + section.substring(1),
                "rows",
                rows));
    chunk.addProperty("id", program + "." + Identity.content("chunk", chunk));
    facts.row("programs", chunk);
  }

  public JsonObject exclusion(int index) {
    check();
    return witnesses.get(index).proof;
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    Witness witness = witnesses.get(index);
    source.verify(witness.entry);
    if (witness.proof != null) return false;
    microBinding.verify();
    JsonArray layout = array(), template = array(), texts = array();
    int slot = 0;
    for (int cell = 0; cell < 9; cell++) {
      Object raw = witness.entry.nativeCells[cell];
      ItemStack stack = witness.cells[cell];
      template.add(
          raw instanceof ItemStack
              ? object("stack", RecipeRow.stackEvidence((ItemStack) raw))
              : value(raw));
      if (stack == null) {
        layout.add(JsonNull.INSTANCE);
        continue;
      }
      JsonObject rule;
      if (raw instanceof Integer && (Integer) raw != 0) {
        rule =
            object("kind", "fmpMaterial", "program", program, "materialId", witness.candidate.id);
        String text = FmpMaterialIds.text(stack);
        if (text != null) rule.addProperty("nativeText", text);
      } else if (raw instanceof Integer) rule = object("kind", "exact");
      else
        rule =
            object(
                "kind",
                "wildcard",
                "meta",
                ((ItemStack) raw).getItemDamage() == 32767,
                "nbt",
                true);
      row.nativeCraftingInput(
          slot, Collections.singletonList(new RecipeRow.Ingredient(stack.copy(), 1, false, rule)));
      texts.add(value(stack.getItem() == micro ? FmpMaterialIds.text(stack) : null));
      layout.add(value(slot++));
    }
    JsonArray states = array(), samples = array(), bindings = array();
    for (boolean mirrored : new boolean[] {false, true}) {
      XuMicroblockLogic.Outcome actual =
          witness.logic.evaluate(mirrored ? mirror(witness.cells) : witness.cells);
      if (actual == null
          || !Objects.equals(
              witness.candidate.id,
              actual.materialId == 0 ? null : Integer.valueOf(actual.materialId)))
        throw new Jobs.Fault("recipe_changed", "XU native witness no longer matches");
      if (actual.output.getItem() == null || actual.output.stackSize <= 0)
        throw fault("Native XU output is nonpositive or unavailable");
      JsonObject output =
          object(
              "id",
              row.facts.item(actual.output),
              "amount",
              Integer.toString(actual.output.stackSize));
      samples.add(output);
      Integer first = null;
      if (actual.firstTaggedCell >= 0) {
        int physical = actual.firstTaggedCell;
        int original = mirrored ? physical / 3 * 3 + 2 - physical % 3 : physical;
        first = layout.get(original).getAsInt();
      }
      states.add(
          object(
              "offeredMirrored",
              mirrored,
              "matchedMirrored",
              actual.mirrored,
              "firstPart",
              first,
              "output",
              output));
      JsonArray binding = array();
      for (int input = 0; input < slot; input++) binding.add(value(0));
      bindings.add(binding);
      if (!mirrored) row.itemOutput(0, actual.output, 10000);
    }
    ItemStack prototype = witness.logic.prototype();
    if (slot > 0)
      row.outputs
          .get(0)
          .getAsJsonObject()
          .add(
              "change",
              object(
                  "input",
                  0,
                  "action",
                  object("kind", "xuResult"),
                  "samples",
                  samples,
                  "bindings",
                  bindings));
    row.record.add(
        "process",
        object(
            "kind",
            "xuMicroblock",
            "nativeRecipe",
            XuMicroblockSource.RECIPE,
            "nativeIndex",
            witness.entry.index,
            "program",
            program,
            "scope",
            "nativeRegistryWitnesses",
            "materialId",
            witness.candidate.id,
            "layout",
            layout,
            "cells",
            template,
            "materialTexts",
            texts,
            "prototype",
            object(
                "id", row.facts.item(prototype), "amount", Integer.toString(prototype.stackSize)),
            "states",
            states));
    source.verify(witness.entry);
    microBinding.verify();
    return true;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "XU native reader is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    for (XuMicroblockLogic rule : rules) rule.close();
    rules.clear();
    witnesses.clear();
    variants.clear();
    published.clear();
    if (materials != null) materials.close();
    materials = null;
    if (microBinding != null) microBinding.close();
    microBinding = null;
    if (source != null) source.close();
    source = null;
    micro = null;
    context = null;
    program = null;
  }

  private static final class Candidate {
    final Integer id;
    final ItemStack block;
    final String name;

    Candidate(Integer id, ItemStack block, String name) {
      this.id = id;
      this.block = block;
      this.name = name;
    }
  }

  private static final class Witness {
    final XuMicroblockSource.Entry entry;
    final XuMicroblockLogic logic;
    final ItemStack[] cells;
    final Candidate candidate;
    final JsonObject proof;

    Witness(
        XuMicroblockSource.Entry entry,
        XuMicroblockLogic logic,
        ItemStack[] cells,
        Candidate candidate,
        JsonObject proof) {
      this.entry = entry;
      this.logic = logic;
      this.cells = cells;
      this.candidate = candidate;
      this.proof = proof;
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
