package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.GendustryGeneticsRecipes.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Native Gendustry source table, including exact blockers and sources absent from NEI previews.
 */
final class GendustrySourceTable implements AutoCloseable {
  final List<Entry> entries = new ArrayList<>();
  private final List<Object> identities = new ArrayList<>();
  private final String signature;
  private Object source;
  private final String expectedOwner;
  private boolean closed;
  private Object tableRoot;
  private int tableCount;

  GendustrySourceTable() {
    this(module("fluids.LiquidDNASources"),"net.bdew.gendustry.fluids.LiquidDNASources$");
  }

  GendustrySourceTable(Object source,String expectedOwner) {
    if(!Arrays.asList("net.bdew.gendustry.fluids.LiquidDNASources$",
        "net.bdew.gendustry.fluids.ProteinSources$","net.bdew.gendustry.fluids.MutagenSources$").contains(expectedOwner))
      throw fault("Unknown native Gendustry source role");
    this.source=source;this.expectedOwner=expectedOwner;
    JsonArray facts = read(entries, identities);
    signature = CanonicalJson.digest(facts);
    tableRoot=field(source,"values");tableCount=((Number)call(tableRoot,"size")).intValue();
    for (Entry entry : entries)
      if (entry.meta == 32767 && entry.amount > 0) {
        entry.variants = new NativeItemVariants(new ItemStack(entry.item, 1, 32767));
        for (ItemStack sample : entry.variants.observedCopies()) {
          boolean blocked = false;
          for (Entry other : entries)
            if (other.item == entry.item
                && other.meta != 32767
                && other.meta == sample.getItemDamage()) {
              blocked = true;
              break;
            }
          if (!blocked) {
            entry.sample = sample;
            entry.sample.stackSize = 1;
            break;
          }
        }
        if (entry.sample == null)
          throw new Jobs.Fault(
              "handler_not_ready", "Gendustry wildcard has no verified native item specimen");
      }
    for (Entry entry : entries) entry.siblings = entries;
  }

  void verify() {
    if(closed)throw new Jobs.Fault("capture_closed","Gendustry source table is closed");
    if(field(source,"values")!=tableRoot||((Number)call(tableRoot,"size")).intValue()!=tableCount)throw changed();
  }
  void finishVerification(){
    verify();
    List<Object> current = new ArrayList<>();
    String actual = CanonicalJson.digest(read(null, current));
    if (!signature.equals(actual) || identities.size() != current.size()) throw changed();
    for (int i = 0; i < current.size(); i++)
      if (identities.get(i) != current.get(i)) throw changed();
    for (Entry entry : entries) if (entry.variants != null) entry.variants.verify();
  }

  private JsonArray read(List<Entry> output, List<Object> identities) {
    identities.add(source);
    if (source==null||source.getClass() != type(expectedOwner))
      throw fault("Unadapted native DNA source owner");
    Object table = field(source, "values");
    identities.add(table);
    if (table == null || !table.getClass().getName().equals("scala.collection.mutable.HashMap"))
      throw fault("Unadapted native DNA table storage");
    List<Entry> entries = new ArrayList<>();
    for (Object pair : NativeScala.values(table,65536)) {
      Object item = NativeScala.pair(pair,1), map = NativeScala.pair(pair,2);
      if (!(item instanceof Item) || Item.itemRegistry.getNameForObject(item) == null)
        throw fault("Unregistered native DNA source item");
      NativeStackAudit.identityOwner(item);
      identities.add(item);
      identities.add(map);
      if (map == null || !map.getClass().getName().equals("scala.collection.mutable.HashMap"))
        throw fault("Unadapted native DNA metadata table");
      if (ItemCallbacks.method((Item) item, "getDamage", "getDamage", ItemStack.class)
              .getDeclaringClass()
          != Item.class) throw fault("Unadapted DNA source metadata callback");
      for (Object raw : NativeScala.values(map,65536)) {
        Object meta = NativeScala.pair(raw,1), amount = NativeScala.pair(raw,2);
        if (!(meta instanceof Integer) || !(amount instanceof Integer))
          throw fault("Invalid native DNA table entry");
        if (entries.size() == 65536) throw fault("Native DNA table exceeds row budget");
        entries.add(new Entry((Item) item, (Integer) meta, (Integer) amount));
      }
    }
    entries.sort(
        Comparator.comparing((Entry e) -> Item.itemRegistry.getNameForObject(e.item))
            .thenComparingInt(e -> e.meta));
    JsonArray facts = array();
    for (Entry e : entries)
      facts.add(
          object(
              "registry",
              Item.itemRegistry.getNameForObject(e.item),
              "meta",
              e.meta,
              "amount",
              e.amount));
    if (output != null) output.addAll(entries);
    return facts;
  }

  static final class Entry {
    final Item item;
    final int meta, amount;
    private NativeItemVariants variants;
    private ItemStack sample;
    private List<Entry> siblings;

    Entry(Item item, int meta, int amount) {
      this.item = item;
      this.meta = meta;
      this.amount = amount;
    }

    ItemStack specimen() {
      if (sample != null) return sample.copy();
      // A rejected wildcard is never rendered; it still has a source disposition.
      ItemStack stack = new ItemStack(item, 1, meta == 32767 ? 0 : meta);
      if (meta != 32767 && net.minecraft.init.Items.feather.getDamage(stack) != meta)
        throw fault("Native DNA metadata cannot be represented by a concrete stack");
      return stack;
    }

    JsonObject rule(Facts facts) {
      JsonObject base = object("kind", "wildcard", "meta", meta == 32767, "nbt", true);
      if (meta != 32767) return base;
      JsonArray exclusions = array();
      for (Entry other : siblings)
        if (other.item == item && other.meta != 32767) {
          if (exclusions.size() == 4096) throw fault("Native DNA wildcard overrides exceed budget");
          exclusions.add(
              object(
                  "id",
                  facts.item(other.specimen()),
                  "rule",
                  object("kind", "wildcard", "meta", false, "nbt", true)));
        }
      return exclusions.size() == 0
          ? base
          : object("kind", "except", "base", base, "exclude", exclusions);
    }
  }

  JsonArray facts(){
    if(closed)throw new Jobs.Fault("capture_closed","Gendustry source table is closed");
    JsonArray rows=array();for(Entry entry:entries)rows.add(object("registry",Item.itemRegistry.getNameForObject(entry.item),"meta",entry.meta,"amount",entry.amount));return rows;
  }

  public void close(){if(closed)return;closed=true;entries.clear();identities.clear();source=null;tableRoot=null;}

  private static Jobs.Fault changed() {
    return new Jobs.Fault("environment_changed", "Native DNA source table or candidates changed");
  }

  private static Jobs.Fault fault(String text) {
    return new Jobs.Fault("recipe_unsupported", text);
  }
}
