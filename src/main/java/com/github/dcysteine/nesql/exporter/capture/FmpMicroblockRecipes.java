package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;

/** Native procedural branches, not NEI's stone-only synthetic ShapedRecipes. */
final class FmpMicroblockRecipes implements RegistryRecipes {
  private static final String HANDLER = "com.rwtema.extrautils.nei.FMPMicroBlocksHandler";
  private Item micro;
  private FmpRules rules;
  private List<FmpRules.Material> materials;
  private List<FmpSawSnapshot> saws;
  private final List<Example> examples = new ArrayList<>();
  private final Map<String,Pattern> patterns=new HashMap<>();
  private boolean closed;
  private JsonObject context;
  private String program;
  private final Set<Facts> published =
      Collections.newSetFromMap(new IdentityHashMap<Facts, Boolean>());

  static boolean supports(ICraftingHandler h) {
    return h.getClass().getName().equals(HANDLER);
  }

  FmpMicroblockRecipes(TemplateRecipeHandler h) {
    MagicApi.version("FmpMicroblockRecipes","ExtraUtilities");
    MagicApi.version("FmpMicroblockRecipes","ForgeMultipart");
    if (!supports(h)) throw fault("Unknown FMP handler");
    try {
      rules = new FmpRules();
      micro = rules.micro();
      materials = new ArrayList<>(rules.materials());
      saws = new ArrayList<>(rules.saws());
      context = rules.declaration();
      program = Identity.content("program", object("kind", "fmpMicroblock", "rules", context));
      // This is a stored native maximum from its last calculation. Later saw
      // registrations do not recompute it; do not derive it from the current census.
      for (FmpRules.Material m : materials) {
        for (FmpSawSnapshot s : saws) {
          add(m, "thinning", grid(new ItemStack(s.item()), m.block), s);
          for (int cls = 0; cls < 4; cls++)
            for (int size : new int[] {4, 2})
              add(m, "thinning", grid(new ItemStack(s.item()), part(m, cls, size)), s);
          for (int cls : new int[] {0, 1, 3})
            for (int size : new int[] {4, 2, 1}) {
              ItemStack[] cells = new ItemStack[9];
              cells[0] = new ItemStack(s.item());
              cells[1] = part(m, cls, size);
              add(m, "splitting", cells, s);
            }
        }
        for (int size : new int[] {4, 2, 1}) {
          ItemStack[] ring = new ItemStack[9];
          for (int i = 0; i < 9; i++) if (i != 4) ring[i] = part(m, 0, size);
          add(m, "hollow", ring, null);
          ItemStack[] fill = new ItemStack[9];
          fill[0] = part(m, 1, size);
          add(m, "hollowFill", fill, null);
          for (int cls = 0; cls < 4; cls++) {
            ItemStack[] glue = new ItemStack[9];
            glue[0] = part(m, cls, size);
            glue[1] = part(m, cls, size);
            add(m, "gluing", glue, null);
          }
        }
        // Native cover gluing also admits mixed sizes. This is a real callback witness.
        ItemStack[] mixed = new ItemStack[9];
        mixed[0] = part(m, 0, 1);
        mixed[1] = part(m, 0, 1);
        mixed[2] = part(m, 0, 2);
        add(m, "gluing", mixed, null);
        if (m.id == 0) {
          ItemStack[] missing = new ItemStack[9];
          missing[0] = new ItemStack(micro, 1, 260);
          add(m, "hollowFill", missing, null);
        }
      }
      finishVerification();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    check();
    return examples.size();
  }

  public void verify() {
    check();
    rules.verify();
  }

  public void finishVerification() {
    check();
    rules.finishVerification();
  }

  public String program(Facts facts) {
    verify();
    if (published.add(facts)) {
      for (FmpRules.Material material : rules.materials()) facts.item(material.block);
      String[] sections = {"materials", "names", "aliases", "saws", "settings"};
      for (String section : sections) {
        JsonArray source = context.getAsJsonArray(section), rows = array();
        int offset = 0, bytes = 0;
        for (JsonElement value : source) {
          Jobs.checkpoint();
          int size = CanonicalJson.bytes(value).length + 1;
          if (size > 900000) throw fault("FMP rule leaf exceeds source row budget");
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
                "fmp" + Character.toUpperCase(section.charAt(0)) + section.substring(1),
                "rows",
                rows));
    chunk.addProperty("id", program + "." + Identity.content("chunk", chunk));
    facts.row("programs", chunk);
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "FMP native reader is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    examples.clear();
    patterns.clear();
    if (materials != null) materials.clear();
    if (saws != null) saws.clear();
    micro = null;
    if (rules != null) rules.close();
    rules = null;
    context = null;
    program = null;
    published.clear();
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    Example e = examples.get(index);
    Pattern pattern=e.pattern;
    ItemStack[] cells=cells(e);
    rules.guard();
    FmpRules.Outcome actual = rules.evaluate(cells);
    ItemStack result = actual == null ? null : actual.stack;
    if (result == null || !ItemStack.areItemStacksEqual(result, result(e))
        ||!actual.branch.equals(pattern.branch)||actual.materialId!=e.material.id)
      throw new Jobs.Fault("recipe_changed", "FMP callback result changed");
    JsonArray layout = array();
    int slot = 0;
    for (int i = 0; i < 9; i++) {
      ItemStack stack = cells[i];
      if (stack == null) {
        layout.add(JsonNull.INSTANCE);
        continue;
      }
      boolean saw = pattern.saw != null && stack.getItem() == pattern.saw.item();
      JsonObject rule =
          stack.getItem() == micro
              ? object("kind", "fmpMaterial", "program", program, "materialId", e.material.id)
              : saw
                  ? object("kind", "wildcard", "meta", true, "nbt", true)
                  : object("kind", "exact");
      if (stack.getItem() == micro) {
        String nativeText = FmpMaterialIds.text(stack);
        if (nativeText != null) rule.addProperty("nativeText", nativeText);
      }
      row.itemInput(slot,
          Collections.singletonList(new RecipeRow.Ingredient(stack.copy(), 1, false, rule)),
          false);
      if (saw)
        row.inputs
            .get(slot)
            .getAsJsonObject()
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .add("consume", object("kind", "wear"));
      layout.add(value(slot++));
    }
    row.itemOutput(0, result, 10000);
    JsonArray bindings = array();
    for (int i = 0; i < slot; i++) bindings.add(value(0));
    row.outputs
        .get(0)
        .getAsJsonObject()
        .add(
            "change",
            object(
                "input",
                0,
                "action",
                object("kind", "fmpResult"),
                "samples",
                array(
                    object(
                        "id",
                        row.facts.item(result),
                        "amount",
                        Integer.toString(result.stackSize))),
                "bindings",
                array(bindings)));
    row.record.add(
        "process",
        object(
            "kind",
            "fmpMicroblock",
            "program",
            program,
            "branch",
            pattern.branch,
            "materialId",
            e.material.id,
            "layout",
            layout));
    check();
    return true;
  }

  private void add(FmpRules.Material m, String branch, ItemStack[] cells, FmpSawSnapshot saw) {
    FmpRules.Outcome actual = rules.evaluate(cells);
    if (actual == null) return;
    ItemStack out = actual.stack;
    if (!branch.equals(actual.branch)) throw fault("FMP branch priority changed");
    if (out.stackSize <= 0) throw fault("Nonpositive FMP result");
    short[] codes=new short[9];
    for(int i=0;i<9;i++){
      ItemStack cell=cells[i];if(cell==null)continue;
      if(saw!=null&&cell.getItem()==saw.item())codes[i]=1;
      else if(cell.getItem()==micro){
        int meta=net.minecraft.init.Items.feather.getDamage(cell);
        if(meta<0||meta>32764)throw fault("FMP compact micro metadata exceeds budget");
        codes[i]=(short)(cell.hasTagCompound()?meta+3:-meta-1);
      }else codes[i]=2;
    }
    boolean microResult=out.getItem()==micro;
    int metadata=net.minecraft.init.Items.feather.getDamage(out);
    String key=branch+"|"+Arrays.toString(codes)+"|"+(saw==null?"":Item.itemRegistry.getNameForObject(saw.item()))
        +"|"+microResult+"|"+metadata+"|"+out.stackSize;
    Pattern pattern=patterns.get(key);
    if(pattern==null){
      if(patterns.size()>=4096)throw fault("FMP distinct layout budget exceeded");
      pattern=new Pattern(branch,codes,saw,microResult,metadata,out.stackSize);patterns.put(key,pattern);
    }
    Example example=new Example(m,materials.get(actual.materialId),pattern);
    ItemStack[] restored=cells(example);
    for(int i=0;i<9;i++)if(!ItemStack.areItemStacksEqual(cells[i],restored[i]))
      throw fault("FMP compact input lost native metadata, quantity or NBT");
    if(!ItemStack.areItemStacksEqual(out,result(example)))throw fault("FMP compact result lost native identity or NBT");
    examples.add(example);
    if (examples.size() > 1048576) throw fault("FMP branch/material expansion exceeds budget");
  }

  private ItemStack part(FmpRules.Material m, int cls, int size) {
    return rules.create(1, cls, size, m.id);
  }

  private static ItemStack[] grid(ItemStack saw, ItemStack part) {
    ItemStack[] cells = new ItemStack[9];
    cells[0] = saw;
    cells[3] = part;
    return cells;
  }

  private ItemStack[] cells(Example example) {
    ItemStack[] out = new ItemStack[9];
    Pattern p=example.pattern;
    for(int i=0;i<9;i++){
      int code=p.cells[i];
      if(code==1)out[i]=new ItemStack(p.saw.item());
      else if(code==2)out[i]=example.input.block.copy();
      else if(code>=3){int meta=code-3;out[i]=part(example.input,meta>>8,meta&255);}
      else if(code<0)out[i]=new ItemStack(micro,1,-code-1);
    }
    return out;
  }
  private ItemStack result(Example example){
    Pattern p=example.pattern;
    ItemStack out=p.microResult?part(example.material,p.metadata>>8,p.metadata&255):example.material.block.copy();
    out.stackSize=p.amount;return out;
  }

  private static Jobs.Fault fault(String m) {
    return new Jobs.Fault("recipe_unsupported", m);
  }

  private static final class Example {
    final FmpRules.Material input,material;final Pattern pattern;
    Example(FmpRules.Material input,FmpRules.Material material,Pattern pattern){this.input=input;this.material=material;this.pattern=pattern;}
  }
  private static final class Pattern {
    final String branch;final short[] cells;final FmpSawSnapshot saw;
    final boolean microResult;final int metadata,amount;
    Pattern(String branch,short[] cells,FmpSawSnapshot saw,boolean microResult,int metadata,int amount){
      this.branch=branch;this.cells=cells;this.saw=saw;this.microResult=microResult;this.metadata=metadata;this.amount=amount;
    }
  }
}
