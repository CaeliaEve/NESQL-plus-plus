package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeDiagramContents.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * Native query normalization, stored once per original identity instead of repeated per binding.
 */
final class NativeDiagramQueries {
    private static final int MAX = 262144;
    final List<JsonObject> lookups = new ArrayList<>();
    private final Object matcher;
    private final boolean ignoreNbt, finite;
    private final NativeDiagramContents contents;
    private final IdentityHashMap<Object, Integer> diagrams;
    private final NativeDiagramBindings bindings;
    private final Map<String, List<Integer>> normalized = new HashMap<>();
    private final Set<String> originals = new HashSet<>();
    private final Map<String, String> damagePolicies = new HashMap<>();
    private long bytes;

    NativeDiagramQueries(
            Object matcher,
            boolean ignoreNbt,
            NativeDiagramContents contents,
            IdentityHashMap<Object, Integer> diagrams,
            NativeDiagramBindings bindings,
            boolean finite,
            long initialBytes) {
        this.matcher = matcher;
        this.ignoreNbt = ignoreNbt;
        this.contents = contents;
        this.diagrams = diagrams;
        this.bindings = bindings;
        this.finite = finite;
        this.bytes = initialBytes;

    }

    void scan(List<ItemStack> panel) {
        if (panel.size() > MAX) throw fault("Native query item-list budget exceeded");
        List<Object> seeds = new ArrayList<>();
        for (ItemStack stack : contents.items.values()) seeds.add(stack.copy());
        for (FluidStack stack : contents.fluids.values()) seeds.add(stack.copy());
        if (finite)
            for (Object direction : map(field(matcher, "matchData")).values()) {
                com.google.common.collect.Multimap<?, ?> values =
                        (com.google.common.collect.Multimap<?, ?>) direction;
                for (Object component : values.keySet()) seeds.add(call(component, "stack"));
            }
        for (ItemStack stack : panel) inspect(stack);
        Map<String, Fluid> fluids = FluidRegistry.getRegisteredFluids();
        if (fluids.size() > MAX) throw fault("Native query fluid-registry budget exceeded");
        for (String name : new TreeSet<>(fluids.keySet()))
            inspect(new FluidStack(fluids.get(name), 1000));
        for (Object seed : seeds) inspect(seed);
        Set<Integer> covered = new HashSet<>();
        for (JsonObject lookup : lookups)
            for (JsonElement index : lookup.getAsJsonArray("bindings"))
                covered.add(index.getAsInt());
        if (covered.size() != bindings.rows.size())
            throw fault("Native binding has no verified original query");
    }

    private void inspect(Object original) {
        Jobs.checkpoint();
        contents.beginQuery();
        if (original == null
                || original instanceof ItemStack && ((ItemStack) original).getItem() == null)
            throw fault("Invalid native query stack");
        Object owned =
                original instanceof ItemStack
                        ? ((ItemStack) original).copy()
                        : original instanceof FluidStack ? ((FluidStack) original).copy() : null;
        if (owned == null) throw fault("Unknown native query type");
        boolean item = owned instanceof ItemStack;
        String damage =
                item
                        ? (((ItemStack) owned).getItem().isDamageable()
                                ? "zeroForDamageableItem"
                                : "preserved")
                        : "notApplicable";
        Object nativeComponent =
                invoke(
                        type(
                                API
                                        + (item
                                                ? "diagram.component.ItemComponent"
                                                : "diagram.component.FluidComponent")),
                        null,
                        ignoreNbt ? "create" : "createWithNbt",
                        new Class<?>[] {item ? ItemStack.class : FluidStack.class},
                        owned);
        JsonObject component = contents.component(nativeComponent, false),
                raw = contents.stack(owned, false);
        if (item) {
            String prior = damagePolicies.putIfAbsent(raw.get("registry").getAsString(), damage);
            if (prior != null && !prior.equals(damage))
                throw fault("Native isDamageable policy changed within a query snapshot");
        }
        JsonObject expected = copy(raw), actual = copy(component);
        expected.remove("fact");
        actual.remove("fact");
        if (ignoreNbt) expected.add("nbt", JsonNull.INSTANCE);
        if (damage.equals("zeroForDamageableItem")) expected.addProperty("meta", 0);
        if (!expected.equals(actual))
            throw fault("Native query normalization changed during capture");
        String identity = CanonicalJson.digest(component);
        List<Integer> destinations = normalized.get(identity);
        if (destinations == null) {
            destinations = matches(nativeComponent, component);
            normalized.put(identity, destinations);
        }
        if (destinations.isEmpty() || !originals.add(CanonicalJson.digest(raw))) return;
        JsonArray indices = array();
        for (int index : destinations) indices.add(value(index));
        JsonObject lookup = object("original", compact(raw), "damage", damage, "bindings", indices);
        int size = CanonicalJson.bytes(lookup).length;
        if (lookups.size() >= MAX || size > 1024 * 1024 || (bytes += size) > 32L * 1024 * 1024)
            throw fault("Native lookup byte/count budget exceeded before accumulation: bytes="+bytes+"; lookups="+lookups.size()+"; next="+size);
        contents.stack(owned, true);
        contents.component(nativeComponent, true);
        lookups.add(lookup);
    }

    private List<Integer> matches(Object nativeComponent, JsonObject component) {
        List<Integer> result = new ArrayList<>();
        Class<?> mode = type(API + "diagram.interactable.Interactable$RecipeType");
        for (String direction : Arrays.asList("CRAFTING", "USAGE")) {
            Collection<?> matching =
                    collection(
                            invoke(
                                    matcher.getClass(),
                                    matcher,
                                    "match",
                                    new Class<?>[] {
                                        mode, type(API + "diagram.component.Component")
                                    },
                                    Enum.valueOf((Class) mode, direction),
                                    nativeComponent));
            JsonObject value = object("query", direction.toLowerCase(Locale.ROOT), "component", component);
            Integer index = bindings.index(value);
            List<Integer> targets = new ArrayList<>(); Set<Integer> unique = new HashSet<>();
            for (Object diagram : matching) {
                if (!diagrams.containsKey(diagram))
                    throw fault("Native query returned a diagram outside its finite closure");
                int target = diagrams.get(diagram);
                if (!unique.add(target)) throw fault("Native query repeated a diagram destination");
                targets.add(target);
            }
            if (targets.isEmpty()) {
                if (finite && index != null) throw fault("Native registered query lost its destination closure");
                continue;
            }
            if (finite) {
                if (index == null) throw fault("Native query differs from its registered component map");
                Set<Integer> registered = new HashSet<>();
                for (JsonElement target : bindings.rows.get(index).getAsJsonArray("diagrams")) registered.add(target.getAsInt());
                if (!registered.equals(unique)) throw fault("Native query differs from its registered destination closure");
            } else {
                if (index != null) throw fault("Native normalized query was accumulated twice");
                long before = bindings.bytes;
                for (int target : targets) index = bindings.add(value, target);
                if ((bytes += bindings.bytes - before) > 32L * 1024 * 1024)
                    throw fault("Native binding byte budget exceeded before accumulation: bytes="+bytes+"; bindings="+bindings.rows.size()+"; lookups="+lookups.size());
            }
            result.add(index);
        }
        Collections.sort(result);
        return result;
    }

    private static JsonObject copy(JsonObject value) {
        return new JsonParser().parse(value.toString()).getAsJsonObject();
    }

}
