package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Pinned material lookup, without ControlPanelMaterial.forName's global cache writes. */
final class ProjectBlueMaterials implements AutoCloseable {
  static final String MATERIAL = "gcewing.projectblue.ControlPanelMaterial";
  private final List<Object> anchors = new ArrayList<>();
  private final Map<String, Resolved> cached = new TreeMap<>(), registered = new TreeMap<>();
  private final Map<String, String> aliases = new TreeMap<>();
  private final Map<String, CacheBinding> observedCache = new TreeMap<>();
  private JsonObject records;
  private String signature;
  private boolean closed;

  ProjectBlueMaterials() {
    records = read(anchors, cached, registered, aliases);
    signature = lookupSignature(records);
    rememberCache();
    verify();
  }

  Resolved resolve(String name) {
    check();
    if (name == null) throw fault("Null native ProjectBlue material key");
    Resolved present = cached.get(name);
    if (present != null) return present;
    Resolved material = registered.get(aliases.getOrDefault(name, name));
    return material == null
        ? new Resolved(name, Blocks.planks, 0)
        : new Resolved(name, material.block, material.metadata);
  }

  JsonObject records() {
    check();
    return new JsonParser().parse(records.toString()).getAsJsonObject();
  }

  Object[] bindings() {
    check();
    return anchors.toArray();
  }

  void verify() {
    check();
    List<Object> current = new ArrayList<>();
    JsonObject facts = read(current, null, null, null);
    if (!signature.equals(lookupSignature(facts)) || anchors.size() != current.size())
      throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (anchors.get(index) != current.get(index)) throw changed();
    rememberCache();
  }

  private static String lookupSignature(JsonObject facts) {
    return CanonicalJson.digest(
        object("registry", facts.get("registry"), "aliases", facts.get("aliases")));
  }

  private void rememberCache() {
    Map<?, ?> live = (Map<?, ?>) anchors.get(0);
    for (Map.Entry<String, CacheBinding> entry : observedCache.entrySet())
      if (live.get(entry.getKey()) != entry.getValue().owner
          || !entry.getValue().same(live.get(entry.getKey()))) throw changed();
    for (Map.Entry<?, ?> entry : live.entrySet()) {
      if (entry.getValue() == null || observedCache.containsKey(entry.getKey())) continue;
      String key = (String) entry.getKey();
      CacheBinding binding = new CacheBinding(entry.getValue());
      if (records != null && !cached.containsKey(key)) {
        Resolved expected = resolve(key);
        if (!expected.name.equals(binding.name)
            || expected.block != binding.block
            || expected.metadata != binding.metadata) throw changed();
      }
      observedCache.put(key, binding);
    }
  }

  private JsonObject read(
      List<Object> bindings,
      Map<String, Resolved> cacheOut,
      Map<String, Resolved> registryOut,
      Map<String, String> aliasOut) {
    Jobs.checkpoint();
    Object cache = field(type(MATERIAL), null, "cache");
    Object provider = field(type("codechicken.microblock.MicroMaterialRegistry$"), null, "MODULE$");
    if (cache == null || provider == null)
      throw new Jobs.Fault(
          "handler_not_ready", "Native ProjectBlue material source is unavailable");
    Object types = field(provider, "codechicken$microblock$MicroMaterialRegistry$$typeMap"),
        remap = field(provider, "remap");
    if (types == null || remap == null)
      throw new Jobs.Fault("handler_not_ready", "Native micro material lookup is unavailable");
    if (cache.getClass() != HashMap.class
        || !types.getClass().getName().equals("scala.collection.mutable.HashMap")
        || remap.getClass() != types.getClass())
      throw fault("Unadapted native material lookup storage");
    Collections.addAll(bindings, cache, provider, types, remap, Blocks.planks);
    if (((Map<?, ?>) cache).size() > 262144)
      throw fault("ProjectBlue material cache exceeds budget");
    Map<String, Object> caches = new TreeMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) cache).entrySet()) {
      if (!(entry.getKey() instanceof String)) throw fault("Non-string native material cache key");
      caches.put((String) entry.getKey(), entry.getValue());
    }
    Map<String, Object> materials = scalaMap(types), remaps = scalaMap(remap);
    if (caches.size() > 262144 || materials.size() > 262144 || remaps.size() > 262144)
      throw fault("ProjectBlue material lookup exceeds budget");
    JsonArray cacheRows = array(), materialRows = array(), aliasRows = array();
    for (Map.Entry<String, Object> entry : caches.entrySet()) {
      Jobs.checkpoint();
      Object value = entry.getValue();
      // A null memoized value has the same native lookup behavior as a miss.
      if (value == null) {
        cacheRows.add(object("key", entry.getKey(), "name", null, "block", null, "metadata", null));
        continue;
      }
      if (value.getClass() != type(MATERIAL)) throw fault("Unadapted cached material definition");
      String name = (String) field(value, "name");
      Block block = (Block) field(value, "block");
      int meta = (Integer) field(value, "metadata");
      if (name == null || block == null) throw fault("Invalid cached native material fields");
      cacheRows.add(
          object("key", entry.getKey(), "name", name, "block", blockName(block), "metadata", meta));
      if (cacheOut != null) cacheOut.put(entry.getKey(), new Resolved(name, block, meta));
    }
    Class<?> blockMaterial = type("codechicken.microblock.BlockMicroMaterial");
    for (Map.Entry<String, Object> entry : materials.entrySet()) {
      Jobs.checkpoint();
      Object material = entry.getValue();
      bindings.add(material);
      Block block = Blocks.planks;
      int meta = 0;
      String owner = null;
      Block sourceBlock = null;
      int sourceMeta = 0;
      boolean itemBlock = false;
      if (material != null) {
        owner = material.getClass().getName();
        try {
          if (material.getClass().getMethod("getItem").getDeclaringClass() != blockMaterial
              || material.getClass().getMethod("block").getDeclaringClass() != blockMaterial
              || material.getClass().getMethod("meta").getDeclaringClass() != blockMaterial)
            throw fault("Unadapted micro material item callback: " + owner);
        } catch (NoSuchMethodException error) {
          throw fault("Unadapted micro material lookup: " + owner);
        }
        sourceBlock = (Block) field(material, "block");
        sourceMeta = (Integer) field(material, "meta");
        if (sourceBlock == null) throw fault("Native micro material block is null");
        Item item = Item.getItemFromBlock(sourceBlock);
        Collections.addAll(bindings, sourceBlock, item);
        // Native BlockMicroMaterial.getItem allocates this ordinary stack, then
        // ControlPanelMaterial keeps only an ItemBlock's block and raw metadata.
        ItemStack stack = new ItemStack(sourceBlock, 1, sourceMeta);
        if (item instanceof ItemBlock) {
          if (ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class)
                  .getDeclaringClass()
              != Item.class)
            throw fault("Material ItemBlock metadata callback requires an adapter");
          itemBlock = true;
          block = Block.getBlockFromItem(item);
          meta = Items.feather.getDamage(stack);
          bindings.add(block);
        }
      }
      materialRows.add(
          object(
              "key",
              entry.getKey(),
              "owner",
              owner,
              "sourceBlock",
              blockName(sourceBlock),
              "sourceMetadata",
              sourceMeta,
              "itemBlock",
              itemBlock,
              "block",
              blockName(block),
              "metadata",
              meta));
      if (registryOut != null)
        registryOut.put(entry.getKey(), new Resolved(entry.getKey(), block, meta));
    }
    for (Map.Entry<String, Object> entry : remaps.entrySet()) {
      if (!(entry.getValue() instanceof String)) throw fault("Non-string native material alias");
      aliasRows.add(object("key", entry.getKey(), "target", entry.getValue()));
      if (aliasOut != null) aliasOut.put(entry.getKey(), (String) entry.getValue());
    }
    return object("cache", cacheRows, "registry", materialRows, "aliases", aliasRows);
  }

  private static Map<String, Object> scalaMap(Object map) {
    Map<String, Object> result = new TreeMap<>();
    Object iterator = invoke(map.getClass(), map, "iterator", new Class<?>[0]);
    while ((Boolean) invoke(iterator.getClass(), iterator, "hasNext", new Class<?>[0])) {
      Jobs.checkpoint();
      Object pair = invoke(iterator.getClass(), iterator, "next", new Class<?>[0]);
      if (pair == null || pair.getClass() != type("scala.Tuple2"))
        throw fault("Unadapted native material tuple");
      Object key = field(pair, "_1"), value = field(pair, "_2");
      if (!(key instanceof String)) throw fault("Non-string native material key");
      result.put((String) key, value);
      if (result.size() > 262144) throw fault("Native material lookup exceeds budget");
    }
    return result;
  }

  private static String blockName(Block block) {
    if (block == null) return null;
    String registry = Block.blockRegistry.getNameForObject(block);
    if (registry == null || Block.blockRegistry.getObject(registry) != block)
      throw fault("Unregistered native material block");
    return registry;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "ProjectBlue material lookup is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    anchors.clear();
    cached.clear();
    registered.clear();
    aliases.clear();
    observedCache.clear();
    records = null;
    signature = null;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "Native ProjectBlue material source, member or facts changed");
  }

  private static final class CacheBinding {
    final Object owner;
    final String name;
    final Block block;
    final int metadata;

    CacheBinding(Object owner) {
      this.owner = owner;
      name = (String) field(owner, "name");
      block = (Block) field(owner, "block");
      metadata = (Integer) field(owner, "metadata");
    }

    boolean same(Object value) {
      return value == owner
          && Objects.equals(name, field(value, "name"))
          && block == field(value, "block")
          && metadata == (Integer) field(value, "metadata");
    }
  }

  static final class Resolved {
    final String name;
    final Block block;
    final int metadata;

    Resolved(String name, Block block, int metadata) {
      this.name = name;
      this.block = block;
      this.metadata = metadata;
    }

    ItemStack stack(Item item, int amount) {
      ItemStack result = new ItemStack(item, amount, metadata);
      net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
      tag.setString("mat", name);
      result.setTagCompound(tag);
      return result;
    }
  }
}
