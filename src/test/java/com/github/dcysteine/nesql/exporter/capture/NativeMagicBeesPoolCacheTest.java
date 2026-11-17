package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import com.google.common.cache.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Real Guava/Forestry operations supply the expectations, including queue state.
 * Catches lost read writes, wrong eviction/expiry, rehashed mutable keys, and
 * loader-failure rollback that incorrectly discards native NBT repairs. */
final class NativeMagicBeesPoolCacheTest {
  @SuppressWarnings({"unchecked", "rawtypes"})
  static void run(ItemStack offered) throws Exception {
    String location = CacheBuilder.class.getProtectionDomain().getCodeSource().getLocation().toString();
    if (!Sources.hash(java.nio.file.Paths.get(CacheBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI()))
        .equals("8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc"))
      throw new AssertionError("Cache oracle must load the installed Guava 17: " + location);
    Class<?> machine;
    try { machine = Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolCacheMachine"); }
    catch (ClassNotFoundException missing) {
      throw new AssertionError("Missing mutable-key native cache transition machine", missing);
    }
    Method state;
    try { state = MagicBeesPoolCache.class.getDeclaredMethod("state"); }
    catch (NoSuchMethodException missing) {
      throw new AssertionError("Cache observation lacks queues, weights and key identities", missing);
    }
    state.setAccessible(true);
    Constructor<?> create = machine.getDeclaredConstructor(JsonObject.class);
    Method get = machine.getDeclaredMethod("get", int.class, LongSupplier.class, IntFunction.class);
    Method key = machine.getDeclaredMethod("key", int.class);
    Method snapshot = machine.getDeclaredMethod("snapshot");
    for (AccessibleObject member : new AccessibleObject[]{create, get, key, snapshot}) member.setAccessible(true);
    Constructor<?> loaderConstructor = type("forestry.apiculture.genetics.BeeGenome$1").getDeclaredConstructor();
    loaderConstructor.setAccessible(true);
    CacheLoader loader = (CacheLoader)loaderConstructor.newInstance();
    JsonArray observations = array();
    try (MagicBeesPoolSource source = new MagicBeesPoolSource()) {
      runtimeBinding(source, loader, offered);
      for (String name : new String[]{"hit", "expiry", "capacity", "read-cleanup",
          "mutable-key", "load-error", "nan-identity", "nano-wrap", "collisions", "segments"}) {
        Clock clock = new Clock();
        LoadingCache cache = CacheBuilder.newBuilder().initialCapacity(1)
            .concurrencyLevel(name.equals("segments") ? 4 : 1)
            .maximumSize(128).expireAfterAccess(1, TimeUnit.MINUTES).ticker(clock).build(loader);
        List<NBTTagCompound> keys = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        NBTTagCompound seed = (NBTTagCompound)offered.getTagCompound().getCompoundTag("Genome").copy();
        if (name.equals("capacity") || name.equals("segments")) {
          int count = name.equals("capacity") ? 132 : 150;
          for (int i = 0; i < count; i++) {
            NBTTagCompound sample = (NBTTagCompound)seed.copy(); sample.setInteger("fixture-id", i);
            keys.add(sample); actions.add(new Action(i, 1000 + i * 100));
          }
          actions.add(new Action(count - 1, 30000)); actions.add(new Action(0, 30100));
        } else if (name.equals("collisions")) {
          for (String text : new String[]{"AaAa", "BBBB", "AaBB", "BBAa"}) {
            NBTTagCompound sample = (NBTTagCompound)seed.copy(); sample.setString("collision", text); keys.add(sample);
            actions.add(new Action(keys.size() - 1, 1000 + keys.size()));
          }
          actions.add(new Action(0, 2000)); actions.add(new Action(2, 60000002002L));
        } else {
          keys.add(seed);
          if (name.equals("mutable-key") || name.equals("load-error")) {
            seed.getTagList("Chromosomes", 10).getCompoundTagAt(1).setString("UID0", "fixture.invalid.speed");
            if (name.equals("load-error")) {
              NBTTagCompound invalid = new NBTTagCompound();
              invalid.setByte("Slot", (byte)forestry.api.apiculture.EnumBeeChromosome.HUMIDITY.ordinal());
              invalid.setString("UID0", "fixture.invalid.humidity"); invalid.setString("UID1", "fixture.invalid.humidity");
              seed.getTagList("Chromosomes", 10).appendTag(invalid);
            }
          }
          if (name.equals("nan-identity")) {
            seed.setFloat("nan", Float.NaN); keys.add((NBTTagCompound)seed.copy());
          }
          long start = name.equals("nano-wrap") ? Long.MAX_VALUE - 1000 : 1000;
          actions.add(new Action(0, start));
          if (name.equals("read-cleanup")) {
            for (int i = 1; i <= 64; i++) actions.add(new Action(0, start + i));
          } else {
            long next = name.equals("expiry") || name.equals("nano-wrap") ? start + 60000000000L : start + 100;
            actions.add(new Action(0, next)); actions.add(new Action(name.equals("nan-identity") ? 1 : 0, next + 100));
          }
        }
        JsonObject initial;
        try (MagicBeesPoolCache capture = new MagicBeesPoolCache(cache, source)) {
          initial = (JsonObject)state.invoke(capture); capture.finishVerification();
        }
        JsonArray initialKeys = initial.getAsJsonArray("keys");
        for (NBTTagCompound query : keys) initialKeys.add(TypedNbt.encode(query));
        Object engine = create.newInstance(initial);
        IdentityHashMap<Object, Boolean> loaded = new IdentityHashMap<>();
        JsonArray steps = array();
        boolean sawError = false;
        for (Action action : actions) {
          clock.now = action.now; clock.reads = array();
          NBTTagCompound query = keys.get(action.key);
          Object genome = null; Throwable failure = null;
          try { genome = cache.get(query); } catch (RuntimeException error) { failure = error; }
          String kind = failure != null ? "nativeError" : loaded.put(genome, true) == null ? "loaded" : "hit";
          sawError |= failure != null;
          JsonObject expected = object("kind", kind);
          if (failure == null) expected.add("chromosome", chromosome(genome, source));
          JsonObject after = TypedNbt.encode(query).getAsJsonObject();
          JsonArray ticks = clock.reads;
          final int[] tickAt = {0}, loads = {0};
          LongSupplier ticker = () -> {
            if (tickAt[0] >= ticks.size()) throw new AssertionError("Transition over-read its clock: " + name);
            return Long.parseLong(ticks.get(tickAt[0]++).getAsString());
          };
          IntFunction<JsonObject> load = (id) -> {
            loads[0]++;
            try {
              NBTTagCompound engineKey = (NBTTagCompound)key.invoke(engine, id);
              // The loader is real Forestry code, called on the engine's owned key.
              Object value = loader.load(engineKey);
              return object("chromosome", chromosome(value, source));
            } catch (InvocationTargetException | RuntimeException error) {
              return object("nativeError", true);
            } catch (Exception error) { throw new AssertionError(error); }
          };
          JsonObject actual = (JsonObject)get.invoke(engine, action.key, ticker, load);
          if (!expected.equals(actual) || tickAt[0] != ticks.size()
              || loads[0] != (kind.equals("hit") ? 0 : 1)
              || !after.equals(TypedNbt.encode((NBTTagCompound)key.invoke(engine, action.key))))
            throw new AssertionError("Native cache transition differs: " + name + " / " + steps.size()
                + " / expected " + expected + " / actual " + actual);
          steps.add(object("key", action.key, "ticks", ticks, "result", expected, "keyAfter", after));
        }
        if (name.equals("load-error") && !sawError)
          throw new AssertionError("Missing the real Forestry loader-failure branch");
        JsonObject expectedState;
        try (MagicBeesPoolCache capture = new MagicBeesPoolCache(cache, source)) {
          expectedState = (JsonObject)state.invoke(capture); capture.finishVerification();
        }
        JsonObject actualState = (JsonObject)snapshot.invoke(engine);
        if (!expectedState.equals(actualState))
          throw new AssertionError("Native cache final queue/bucket/alias state differs: " + name
              + "\nexpected " + expectedState + "\nactual " + actualState);
        Object restored = create.newInstance(expectedState);
        if (!expectedState.equals(snapshot.invoke(restored)))
          throw new AssertionError("Populated state lost aliases, stale recency entries or bucket order: " + name);
        observations.add(object("case", name, "initial", initial, "steps", steps, "state", expectedState));
      }
      source.finishVerification();
    }
    java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),
        "magicbees-pool-cache-transitions.json"), CanonicalJson.bytes(object(
            "scope", "owned-serial-guava17-transitions-with-explicit-ticker-reads",
            "guava", location, "observations", observations)));
    System.out.println("MagicBees Guava 17 transitions: 10 native sequences, hit writes/expiry/eviction/cleanup,"
        + " expansion/collisions/mutable-key aliases/errors/NaN identity/nano wrap passed");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void runtimeBinding(MagicBeesPoolSource source, CacheLoader loader, ItemStack offered) throws Exception {
    Method bound, binding;
    try {
      bound = MagicBeesPoolCache.class.getDeclaredMethod("bound", Object.class, MagicBeesPoolSource.class);
      binding = MagicBeesPoolCache.class.getDeclaredMethod("binding");
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("Production cache lacks reviewed Guava/runtime and live-clock binding", missing);
    }
    bound.setAccessible(true); binding.setAccessible(true);
    LoadingCache cache = CacheBuilder.newBuilder().maximumSize(128).expireAfterAccess(1, TimeUnit.MINUTES).build(loader);
    try (MagicBeesPoolCache capture = (MagicBeesPoolCache)bound.invoke(null, cache, source)) {
      JsonObject value = (JsonObject)binding.invoke(capture);
      if (!value.get("runtime").getAsString().equals("guava17StrongAccess")
          || !value.get("clock").getAsString().equals("javaSystemNanoTime")
          || Long.parseLong(value.get("afterNanos").getAsString()) - Long.parseLong(value.get("beforeNanos").getAsString()) < 0)
        throw new AssertionError("Missing bounded live native clock interval");
      capture.finishVerification();
      JsonObject owned = capture.state();
      NBTTagCompound seed = offered.getTagCompound().getCompoundTag("Genome");
      for (int i = 0; i < 160; i++) {
        NBTTagCompound liveKey = (NBTTagCompound)seed.copy();
        liveKey.setInteger("bound-live-access", i);
        cache.get(liveKey);
        cache.getIfPresent(liveKey);
      }
      capture.finishVerification();
      if (!owned.equals(capture.state()))
        throw new AssertionError("Live access/eviction rewrote the bound owned transition snapshot");
      Object local = field(cache, "localCache");
      Object ticker = field(local, "ticker");
      try {
        replace(local, "ticker", new Clock());
        try { capture.finishVerification(); throw new AssertionError("Bound cache ignored a changed ticker"); }
        catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
          if (!expected.code.equals("environment_changed")) throw expected;
        }
      } finally { replace(local, "ticker", ticker); }
      java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),
          "magicbees-pool-cache-runtime.json"), CanonicalJson.bytes(value));
    }
    LoadingCache custom = CacheBuilder.newBuilder().maximumSize(128).expireAfterAccess(1, TimeUnit.MINUTES)
        .ticker(new Clock()).build(loader);
    try {
      ((MagicBeesPoolCache)bound.invoke(null, custom, source)).close();
      throw new AssertionError("A caller-supplied ticker was accepted as the live native system clock");
    } catch (InvocationTargetException failure) {
      if (!(failure.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault))
        throw failure;
    }
  }

  private static void replace(Object owner, String name, Object value) throws Exception {
    Field property = owner.getClass().getDeclaredField(name);
    property.setAccessible(true); property.set(owner, value);
  }

  private static JsonElement chromosome(Object genome, MagicBeesPoolSource source) {
    forestry.api.genetics.IChromosome[] cells =
        (forestry.api.genetics.IChromosome[])field(genome, "chromosomes");
    if (cells == null || cells[0] == null) return JsonNull.INSTANCE;
    return object("primary", source.cachedAllele(cells[0].getPrimaryAllele()),
        "secondary", source.cachedAllele(cells[0].getSecondaryAllele()));
  }
  private static final class Clock extends com.google.common.base.Ticker {
    long now = 1000; JsonArray reads = array();
    @Override public long read() { reads.add(value(Long.toString(now))); return now; }
  }
  private static final class Action {
    final int key; final long now;
    Action(int key, long now) { this.key = key; this.now = now; }
  }
}
