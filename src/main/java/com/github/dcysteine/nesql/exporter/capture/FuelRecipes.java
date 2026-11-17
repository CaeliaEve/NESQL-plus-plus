package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native NEI fuel samples and furnace consumption; no cycled smelting examples or GUI. */
final class FuelRecipes implements NativeReferences {
  static final String HANDLER = "codechicken.nei.recipe.FuelRecipeHandler";
  private final JsonObject source;
  private final List<FurnaceRecipeHandler.FuelPair> table;
  private final BooleanSupplier ready;
  private Snapshot snapshot;
  private final String signature;
  private final NativeDefinitions definitions;
  private final Map<Object,Object[]> nativeAnchors=new IdentityHashMap<>();
  private boolean closed;

  static boolean supports(ICraftingHandler value) {
    return value != null && supportsClass(value.getClass().getName());
  }

  static boolean supportsClass(String name) {
    return HANDLER.equals(name);
  }

  FuelRecipes(ICraftingHandler handler, JsonObject source) {
    this(source, nativeInputs(handler));
  }

  private FuelRecipes(JsonObject source, Inputs input) {
    this(source, input.table, input.ready);
  }

  FuelRecipes(JsonObject source, List<FurnaceRecipeHandler.FuelPair> table, BooleanSupplier ready) {
    this.source = copy(source);
    this.table = table;
    this.ready = ready;
    if (!ready.getAsBoolean() || table == null)
      throw new Jobs.Fault("handler_not_ready", "Native NEI fuel table is unavailable");
    if(table.size()>262144)throw fault("Native fuel table exceeds budget");
    for(FurnaceRecipeHandler.FuelPair fuel:table)nativeAnchors.put(fuel,anchors(fuel));
    definitions=new NativeDefinitions("Native fuel",table,this::current,this::definition,"environment_changed");
    snapshot = snapshot(this.source, table);
    signature = CanonicalJson.digest(snapshot.records);
    definitions.finishVerification();
  }

  static Object[] registrationSource(IRecipeHandler handler){
    if(!(handler instanceof ICraftingHandler))throw fault("Unverified native fuel registration");
    Inputs input=nativeInputs((ICraftingHandler)handler);return new Object[]{handler.getClass(),ItemList.items,input.table};
  }

  private static Inputs nativeInputs(ICraftingHandler handler) {
    if (!supports(handler)) throw fault("Unverified native fuel handler");
    if (!ItemList.loadFinished || ItemList.items == null)
      throw new Jobs.Fault("handler_not_ready", "NEI fuel item catalogue is unavailable");
    List<ItemStack> panel = ItemList.items;
    List<FurnaceRecipeHandler.FuelPair> fuels = FurnaceRecipeHandler.afuels;
    // The native builder executes arbitrary fuel callbacks and initializes GUI config.
    // Observe only a table that the native lifecycle already made available.
    if(fuels==null)throw new Jobs.Fault("handler_not_ready","Native NEI fuel table has not been initialized");
    if(fuels.getClass()!=ArrayList.class)throw fault("Unadapted native fuel table");
    int panelSize=panel.size();
    return new Inputs(
        fuels,
        () ->
            ItemList.loadFinished
                && ItemList.items == panel
                && panel.size()==panelSize
                && FurnaceRecipeHandler.afuels == fuels);
  }

  public int size() {
    verify();
    return snapshot.records.size();
  }

  public String domain() {
    verify();
    return snapshot.records.get(0).getAsJsonObject().get("domain").getAsString();
  }

  public JsonObject counts() {
    return object(
        "fuels", size() - 1, "candidates", snapshot.candidates, "items", snapshot.specimens.size());
  }

  public void verify() {
    if(closed)throw new Jobs.Fault("capture_closed","Native fuel reader is closed");
    definitions.verify();
    for(GtItemListBinding binding:snapshot.containerBindings)binding.verify();
    for(FuelContainerRules binding:snapshot.dynamicContainers)binding.verify();
  }
  private List<?> current(){
    if (!ready.getAsBoolean())
      throw new Jobs.Fault("environment_changed", "Native fuel table or item catalogue changed");
    return table;
  }

  public void finishVerification() {
    verify();
    definitions.finishVerification();
    Snapshot current=snapshot(source,table);
    if (!signature.equals(CanonicalJson.digest(current.records)))
      throw new Jobs.Fault(
          "environment_changed", "Native fuel stacks, burn duration or container callback changed");
    for(Map.Entry<String,ItemStack> entry:snapshot.specimens.entrySet()){
      ItemStack actual=current.specimens.get(entry.getKey());
      if(actual==null||actual.getItem()!=entry.getValue().getItem())throw new Jobs.Fault("environment_changed","Native fuel or container item instance changed");
    }
    definitions.finishVerification();
  }

  public void close(){if(closed)return;closed=true;definitions.close();nativeAnchors.clear();snapshot=null;}

  public void capture(int index, Facts facts) {
    verify();
    if(index>0){definitions.checked(index-1,()->{captureRow(index,facts);return null;});}
    else {captureRow(index,facts);verify();}
  }
  private void captureRow(int index,Facts facts){
    JsonObject row = snapshot.records.get(index).getAsJsonObject();
    if (index > 0)
      for (JsonElement raw : row.getAsJsonObject("data").getAsJsonArray("candidates")) {
        JsonObject candidate = raw.getAsJsonObject();
        captureItem(candidate.get("item").getAsString(), facts);
        if (!candidate.get("container").isJsonNull())
          captureItem(candidate.getAsJsonObject("container").get("item").getAsString(), facts);
        if(candidate.has("containerRule")){
          JsonObject rule=candidate.getAsJsonObject("containerRule");
          if(rule.has("degradedBase"))captureItem(rule.get("degradedBase").getAsString(),facts);
          for(String field:new String[]{"normalReturn","degradedReturn","returned"})
            if(rule.has(field)&&!rule.get(field).isJsonNull())captureItem(rule.getAsJsonObject(field).get("item").getAsString(),facts);
        }
      }
    facts.row("references", copy(row));
  }

  private static Object[] anchors(FurnaceRecipeHandler.FuelPair fuel){
    if(fuel==null||fuel.getClass()!=FurnaceRecipeHandler.FuelPair.class||fuel.stack==null||fuel.stack.items==null||fuel.stack.items.length>4096)throw fault("Invalid native fuel source member");
    List<Object> anchors=new ArrayList<>();anchors.add(fuel.stack);anchors.add(fuel.stack.items);
    for(ItemStack stack:fuel.stack.items){if(stack==null||stack.getClass()!=ItemStack.class||stack.getItem()==null)throw fault("Unadapted native fuel stack");anchors.add(stack);anchors.add(stack.getItem());}
    return anchors.toArray();
  }
  private JsonObject definition(Object raw){
    FurnaceRecipeHandler.FuelPair fuel=(FurnaceRecipeHandler.FuelPair)raw;
    Object[] expected=nativeAnchors.get(raw),current=anchors(fuel);
    if(expected==null||expected.length!=current.length)throw new Jobs.Fault("environment_changed","Native fuel member changed");
    for(int i=0;i<current.length;i++)if(current[i]!=expected[i])throw new Jobs.Fault("environment_changed","Native fuel member instance changed");
    JsonArray choices=array();for(ItemStack stack:fuel.stack.items)choices.add(RecipeRow.stackEvidence(stack));
    return object("burnTicks",fuel.burnTime,"choices",choices);
  }

  private void captureItem(String id, Facts facts) {
    if (!id.equals(facts.item(snapshot.specimens.get(id).copy())))
      throw fault("Fuel specimen identity changed");
  }

  static Snapshot snapshot(JsonObject source, List<FurnaceRecipeHandler.FuelPair> table) {
    if (!source.has("handler")
        || !HANDLER.equals(source.get("handler").getAsString())
        || table == null
        || table.size() > 262144) throw fault("Invalid native fuel source or table budget");
    Snapshot result = new Snapshot();
    JsonArray data = array();
    data.add(
        object(
            "kind",
            "furnaceFuelDefinition",
            "source",
            source,
            "ready",
            true,
            "consuming",
            false,
            "fuels",
            table.size(),
            "scope",
            "nativePanelFuelSamples",
            "unitsPerBurn",
            1,
            "referenceSmeltTicks",
            200,
            "containerPolicy",
            "exhaustedFuelStack"));
    for (FurnaceRecipeHandler.FuelPair fuel : table) {
      Jobs.checkpoint();
      if (fuel == null
          || fuel.stack == null
          || fuel.stack.items == null
          || fuel.stack.items.length == 0
          || fuel.stack.items.length > 4096
          || fuel.burnTime <= 0) throw fault("Malformed native fuel sample");
      JsonArray candidates = array();
      for (ItemStack original : fuel.stack.items) {
        if (++result.candidates > 262144)
          throw fault("Native fuel candidate traversal budget exceeded");
        if (original == null || original.getItem() == null || original.stackSize <= 0)
          throw fault("Empty native fuel sample");
        ItemStack stack = original.copy();
        String id = specimen(stack, result.specimens);
        // burnTime is the observed value in NEI's ready sample table. Re-running
        // arbitrary handlers can consume RNG/resources and is not an equivalent
        // verification of that earlier observation (the domain is explicitly sampled).
        // Furnace decrements one fuel, then calls getContainerItem only when exhausted.
        ItemStack exhausted = stack.copy();
        exhausted.stackSize = 0;
        FuelContainerRules dynamic=FuelContainerRules.open(exhausted);
        ItemStack returned = dynamic==null?FuelContainers.fixed(exhausted,result.containerBindings):null;
        JsonObject container = null;
        if (returned != null) {
          if (returned.getItem() == null || returned.stackSize <= 0)
            throw fault("Invalid native exhausted-fuel container");
          container =
              object(
                  "item",
                  specimen(returned.copy(), result.specimens),
                  "amount",
                  Integer.toString(returned.stackSize));
        }
        JsonObject candidate=object(
                "item",
                id,
                "templateAmount",
                Integer.toString(stack.stackSize),
                "container",
                container);
        if(dynamic!=null){
          result.dynamicContainers.add(dynamic);
          candidate.add("containerRule",dynamic.declaration(value->container(value,result.specimens),value->specimen(value,result.specimens)));
        }
        candidates.add(candidate);
      }
      data.add(object("kind", "furnaceFuel", "burnTicks", fuel.burnTime, "candidates", candidates));
    }
    String domain = Identity.content("reference", object("kind", "furnaceFuels", "records", data));
    long bytes = 0;
    for (int order = 0; order < data.size(); order++) {
      JsonObject row = object("domain", domain, "order", order, "data", data.get(order));
      row.addProperty("id", Identity.content("refrow", row));
      int length = CanonicalJson.bytes(row).length;
      bytes += length;
      if (length > 1048576 || bytes > 32L * 1024 * 1024)
        throw fault("Native fuel references exceed byte budget");
      result.records.add(row);
    }
    return result;
  }

  private static String specimen(ItemStack stack, Map<String, ItemStack> specimens) {
    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem())
      throw fault("Unregistered native fuel or container");
    String id =
        Identity.item(registry, net.minecraft.init.Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound()));
    ItemStack display = stack.copy();
    display.stackSize = 1;
    specimens.putIfAbsent(id, display);
    return id;
  }

  private static JsonObject container(ItemStack stack,Map<String,ItemStack> specimens){
    if(stack==null)return null;
    if(stack.getItem()==null||stack.stackSize<=0)throw fault("Invalid native dynamic fuel container");
    return object("item",specimen(stack,specimens),"amount",Integer.toString(stack.stackSize));
  }

  static final class Snapshot {
    final JsonArray records = array();
    final Map<String, ItemStack> specimens = new TreeMap<>();
    final List<GtItemListBinding> containerBindings = new ArrayList<>();
    final List<FuelContainerRules> dynamicContainers = new ArrayList<>();
    int candidates;
  }

  private static final class Inputs {
    final List<FurnaceRecipeHandler.FuelPair> table;
    final BooleanSupplier ready;

    Inputs(List<FurnaceRecipeHandler.FuelPair> table, BooleanSupplier ready) {
      this.table = table;
      this.ready = ready;
    }
  }

  private static JsonObject copy(JsonObject row) {
    return new JsonParser().parse(row.toString()).getAsJsonObject();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("reference_invalid", message);
  }
}
