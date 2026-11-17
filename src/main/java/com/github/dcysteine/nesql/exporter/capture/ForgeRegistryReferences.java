package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Forge registrations are observations, never conversions. No native GUI is constructed. */
final class ForgeRegistryReferences implements NativeReferences {
    private static final String PREFIX = "tonius.neiintegration.mods.mcforge.RecipeHandler";
    private static final String CONFIG = "tonius.neiintegration.config.Config";
    private final boolean ore, enabled;
    private final String domain, shape;
    private final String lookupSignature;
    private Object[] roots;
    private final List<Entry> entries = new ArrayList<>();
    private final List<JsonObject> records = new ArrayList<>();
    private final Map<String,ItemStack> specimens = new LinkedHashMap<>();
    private final Map<String,FluidStack> liquids = new LinkedHashMap<>();
    private final List<NativeItemVariants> variants = new ArrayList<>();
    private boolean closed;

    static boolean supportsClass(String name) {
        return (PREFIX + "OreDictionary").equals(name) || (PREFIX + "FluidRegistry").equals(name);
    }
    static boolean supports(ICraftingHandler handler) { return handler != null && supportsClass(handler.getClass().getName()); }
    static Object[] registrationAnchors(IRecipeHandler handler) {
        if (!supportsClass(handler.getClass().getName())) throw fault("Unknown Forge registration handler");
        return roots(handler.getClass().getName().endsWith("OreDictionary"));
    }
    private static boolean enabled(boolean ore) { return (Boolean) field(type(CONFIG), null, ore ? "handlerOreDictionary" : "handlerFluidRegistry"); }
    private static Object[] roots(boolean ore) {
        List<Object> result = new ArrayList<>(); result.add(Boolean.valueOf(enabled(ore)));
        if (ore) {
            for (String name : new String[]{"nameToId", "idToName", "idToStack", "idToStackUn", "stackToId"}) result.add(field(OreDictionary.class, null, name));
        } else {
            for (String name : new String[]{"fluids", "fluidIDs", "fluidNames"}) result.add(field(FluidRegistry.class, null, name));
            for (String name : new String[]{"containerFluidMap", "filledContainerMap", "emptyContainers"}) result.add(field(FluidContainerRegistry.class, null, name));
        }
        for (Object value : result) if (value == null) throw new Jobs.Fault("handler_not_ready", "Native Forge registry is unavailable");
        for (int index = 1; index < result.size(); index++) {
            boolean accepted = ore ? (index == 1 ? NativeOreStorage.names(result.get(index))
                    : index == 5 ? NativeOreStorage.reverse(result.get(index)) : result.get(index).getClass() == ArrayList.class)
                    : result.get(index).getClass() == (index <= 3 ? com.google.common.collect.HashBiMap.class : index == 6 ? HashSet.class : HashMap.class);
            if (!accepted) throw fault("Unadapted native Forge registry storage");
        }
        return result.toArray();
    }
    ForgeRegistryReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown Forge registry handler");
        ore = handler.getClass().getName().endsWith("OreDictionary"); roots = roots(ore); enabled = enabled(ore);
        if (enabled) {
            if (ore) {
                List<String> names = new ArrayList<>(Arrays.asList(OreDictionary.getOreNames()));
                names.removeIf(Objects::isNull); Collections.sort(names);
                for (String name : names) entries.add(new Entry(name, null));
            } else {
                List<Fluid> fluids = new ArrayList<>(FluidRegistry.getRegisteredFluids().values());
                fluids.removeIf(Objects::isNull); for (Fluid fluid : fluids) auditFluid(fluid);
                fluids.sort(Comparator.comparingInt(Fluid::getID));
                for (Fluid fluid : fluids) entries.add(new Entry(fluid.getName(), fluid));
            }
        }
        if (entries.size() > 262144) throw fault("Forge registry exceeds its entry budget");
        JsonArray data = array();
        data.add(object("kind", "forgeRegistryDefinition", "source", source, "registry", ore ? "oreDictionary" : "fluidRegistry",
                "enabled", enabled, "entries", entries.size(), "consuming", false));
        for (Entry entry : entries) {
            State state = read(entry, true); entry.signature = CanonicalJson.digest(state.raw); entry.anchors = state.anchors; data.add(state.data);
        }
        domain = Identity.content("reference", object("kind", "forgeRegistry", "records", data));
        for (int index = 0; index < data.size(); index++) {
            JsonObject row = object("domain", domain, "order", index, "data", data.get(index));
            row.addProperty("id", Identity.content("refrow", row));
            if (CanonicalJson.bytes(row).length > 1024 * 1024) throw new Jobs.Fault("reference_limit", "Forge registry leaf exceeds 1 MiB");
            records.add(row);
        }
        shape = shape(); lookupSignature = lookupSignature(); finishVerification();
    }
    public int size() { check(); return records.size(); }
    public String domain() { check(); return domain; }
    public JsonObject counts() { check(); return object("entries", entries.size(), "items", specimens.size(), "fluids", liquids.size()); }
    public void verify() {
        check(); Object[] current;
        try { current = roots(ore); }
        catch (Jobs.Fault failure) { Jobs.Fault drift = changed(); drift.initCause(failure); throw drift; }
        if (current.length != roots.length) throw changed();
        for (int index = 0; index < roots.length; index++) if (current[index] != roots[index]) throw changed();
        if (!shape.equals(shape())) throw changed();
    }
    public void finishVerification() {
        verify(); for (Entry entry : entries) verify(entry); for (NativeItemVariants source : variants) source.verify();
        if (!lookupSignature.equals(lookupSignature())) throw changed();
    }
    public void capture(int index, Facts facts) {
        verify(); Entry entry = index == 0 ? null : entries.get(index - 1); if (entry != null) verify(entry);
        try {
            JsonObject row = records.get(index), data = row.getAsJsonObject("data");
            if (index != 0) {
                for (JsonElement id : data.getAsJsonArray("items")) {
                    String key = id.getAsString(); if (!facts.item(specimens.get(key).copy()).equals(key)) throw changed();
                }
                if (!ore) {
                    Set<String> ids = new LinkedHashSet<>(); ids.add(data.getAsJsonObject("fluid").get("id").getAsString());
                    for (JsonElement container : data.getAsJsonArray("containers")) ids.add(container.getAsJsonObject().getAsJsonObject("fluid").get("id").getAsString());
                    for (String id : ids) if (!facts.fluid(liquids.get(id).copy()).equals(id)) throw changed();
                }
            }
            facts.row("references", copy(row));
        } catch (RuntimeException | Error failure) {
            try { verify(); if (entry != null) verify(entry); }
            catch (RuntimeException | Error drift) { if (drift != failure) failure.addSuppressed(drift); }
            throw failure;
        }
        verify(); if (entry != null) verify(entry);
    }
    private void verify(Entry entry) {
        State current = read(entry, false);
        if (!entry.signature.equals(CanonicalJson.digest(current.raw)) || entry.anchors.size() != current.anchors.size()) throw changed();
        for (int index = 0; index < entry.anchors.size(); index++) if (entry.anchors.get(index) != current.anchors.get(index)) throw changed();
    }
    private String shape() {
        if (!enabled) return "disabled";
        if (ore) return ((Map<?,?>) roots[1]).size() + ":" + ((List<?>) roots[2]).size() + ":" + ((List<?>) roots[3]).size()
                + ":" + ((List<?>) roots[4]).size() + ":" + ((Map<?,?>) roots[5]).size();
        return CanonicalJson.digest(object("fluids", ((Map<?,?>) roots[1]).size(), "ids", ((Map<?,?>) roots[2]).size(), "names", ((Map<?,?>) roots[3]).size(),
                "containers", ((Map<?,?>) roots[4]).size(), "filled", ((Map<?,?>) roots[5]).size(), "empty", ((Set<?>) roots[6]).size()));
    }
    private String lookupSignature() {
        if (!enabled) return "disabled";
        // Only JDK scalar/list facts are encoded; no item, fluid or container callbacks.
        if (ore) return CanonicalJson.digest(object("names", new Gson().toJsonTree(roots[1]),
                "orderedNames", new Gson().toJsonTree(roots[2]), "reverse", new Gson().toJsonTree(roots[5])));
        JsonArray ids = array();
        for (Map.Entry<?,?> entry : ((Map<?,?>) roots[3]).entrySet()) ids.add(object("id", entry.getKey(), "name", entry.getValue()));
        return CanonicalJson.digest(ids);
    }
    private State read(Entry entry, boolean display) {
        Jobs.checkpoint(); State state = new State(); Set<String> seen = new LinkedHashSet<>();
        if (ore) {
            Map<?,?> names = (Map<?,?>) field(OreDictionary.class, null, "nameToId"); Object id = names.get(entry.name);
            if (!(id instanceof Integer)) throw changed(); int nativeId = (Integer) id;
            List<?> lists = (List<?>) field(OreDictionary.class, null, "idToStack"); if (nativeId < 0 || nativeId >= lists.size()) throw changed();
            if (!entry.name.equals(((List<?>) roots[2]).get(nativeId))) throw changed();
            Object list = lists.get(nativeId); if (list == null || list.getClass() != ArrayList.class) throw fault("Unadapted native ore storage");
            if (((List<?>) list).size() > 262144) throw fault("Forge ore member budget exceeded");
            state.anchors.add(list); JsonArray members = array();
            for (Object item : (List<?>) list) members.add(stack(item, state, display, seen));
            state.raw = withoutDisplays(object("nativeId", nativeId, "name", entry.name, "members", members));
            if (display) state.data = object("kind", "oreGroup", "nativeId", nativeId, "name", entry.name, "members", members, "items", ids(seen));
        } else {
            Fluid fluid = entry.fluid; auditFluid(fluid); if (FluidRegistry.getFluid(entry.name) != fluid) throw changed(); state.anchors.add(fluid);
            Block block = fluid.getBlock(); state.anchors.add(block); JsonElement blockData = JsonNull.INSTANCE;
            if (block != null) {
                String registry = Block.blockRegistry.getNameForObject(block); if (registry == null || Block.blockRegistry.getObject(registry) != block) throw fault("Unregistered Forge fluid block");
                Item blockItem = Item.getItemFromBlock(block); state.anchors.add(blockItem); String icon = null;
                if (blockItem != null && display) { icon = specimen(new ItemStack(blockItem)); seen.add(icon); }
                blockData = object("registry", registry, "display", icon);
            }
            JsonObject info = object("id", Identity.fluid(entry.name, value(null)), "registry", entry.name, "nativeId", fluid.getID(),
                    "temperature", fluid.getTemperature(), "luminosity", fluid.getLuminosity(), "density", fluid.getDensity(), "viscosity", fluid.getViscosity(),
                    "gaseous", fluid.isGaseous(), "placeable", fluid.canBePlacedInWorld(), "block", blockData);
            JsonArray containers = array();
            for (FluidContainerRegistry.FluidContainerData container : FluidContainerRegistry.getRegisteredFluidContainerData()) {
                if (container == null || container.getClass() != FluidContainerRegistry.FluidContainerData.class || container.fluid == null || container.fluid.getClass() != FluidStack.class) throw fault("Unadapted native fluid container entry");
                if (container.fluid.getFluid() != fluid) continue;
                state.anchors.add(container); state.anchors.add(container.fluid); state.anchors.add(container.fluid.tag);
                String liquid = Identity.fluid(entry.name, TypedNbt.encode(container.fluid.tag)); if (display) liquids.put(liquid, container.fluid.copy());
                containers.add(object("empty", stack(container.emptyContainer, state, display, seen), "filled", stack(container.filledContainer, state, display, seen),
                        "fluid", object("id", liquid, "amount", container.fluid.amount, "nbt", TypedNbt.encode(container.fluid.tag))));
            }
            JsonObject rawInfo = copy(info); if (!rawInfo.get("block").isJsonNull()) rawInfo.getAsJsonObject("block").add("display", JsonNull.INSTANCE);
            state.raw = object("fluid", rawInfo, "containers", withoutDisplays(containers));
            if (display) {
                liquids.put(info.get("id").getAsString(), new FluidStack(fluid, 1));
                state.data = object("kind", "fluid", "fluid", info, "items", ids(seen), "containers", containers);
            }
        }
        return state;
    }
    private JsonObject stack(Object value, State state, boolean display, Set<String> ids) {
        if (value == null || value.getClass() != ItemStack.class) throw fault("Unadapted Forge registry stack");
        ItemStack stack = (ItemStack) value; Item item = stack.getItem();
        if (item == null || ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class) throw fault("Forge metadata callback requires an adapter");
        String registry = Item.itemRegistry.getNameForObject(item); if (registry == null || Item.itemRegistry.getObject(registry) != item) throw fault("Unregistered Forge registry item");
        state.anchors.add(stack); state.anchors.add(item); state.anchors.add(stack.getTagCompound());
        int meta = Items.feather.getDamage(stack); JsonArray displays = array();
        if (display) {
            ItemStack[] samples;
            if (meta == OreDictionary.WILDCARD_VALUE) { NativeItemVariants source = new NativeItemVariants(stack); variants.add(source); samples = source.copies(); }
            else samples = new ItemStack[]{stack.copy()};
            for (ItemStack sample : samples) {
                sample.stackSize = 1; sample.setTagCompound(stack.getTagCompound() == null ? null : (net.minecraft.nbt.NBTTagCompound) stack.getTagCompound().copy());
                String id = specimen(sample); displays.add(value(id)); ids.add(id);
            }
        }
        return object("registry", registry, "meta", meta, "amount", stack.stackSize, "nbt", TypedNbt.encode(stack.getTagCompound()), "displays", displays);
    }
    private String specimen(ItemStack stack) {
        String id = Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); specimens.put(id, stack); return id;
    }
    private static JsonArray ids(Set<String> ids) { JsonArray result = array(); for (String id : ids) result.add(value(id)); return result; }
    private static void auditFluid(Fluid fluid) {
        try {
            if (fluid.getClass().getMethod("hashCode").getDeclaringClass() != Object.class || fluid.getClass().getMethod("equals", Object.class).getDeclaringClass() != Object.class) throw fault("Unadapted native fluid key callback");
        } catch (NoSuchMethodException error) { throw fault("Missing native fluid key method"); }
    }
    private static JsonElement withoutDisplays(JsonElement input) {
        if (input.isJsonArray()) { JsonArray result = array(); for (JsonElement member : input.getAsJsonArray()) result.add(withoutDisplays(member)); return result; }
        if (input.isJsonObject()) {
            JsonObject result = object(); for (Map.Entry<String,JsonElement> member : input.getAsJsonObject().entrySet()) if (!member.getKey().equals("displays")) result.add(member.getKey(), withoutDisplays(member.getValue())); return result;
        }
        return input;
    }
    private void check() { Jobs.checkpoint(); if (closed) throw new Jobs.Fault("capture_closed", "Forge registry reader is closed"); }
    public void close() { if (closed) return; closed = true; entries.clear(); records.clear(); specimens.clear(); liquids.clear(); variants.clear(); roots = null; }
    private static JsonObject copy(JsonObject row) { return new JsonParser().parse(row.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("reference_unsupported", message); }
    private static Jobs.Fault changed() { return new Jobs.Fault("environment_changed", "Native Forge registry source, configuration, member or facts changed"); }
    private static final class Entry {
        final String name; final Fluid fluid; String signature; List<Object> anchors;
        Entry(String name, Fluid fluid) { this.name = name; this.fluid = fluid; }
    }
    private static final class State { JsonElement raw; JsonObject data; final List<Object> anchors = new ArrayList<>(); }
}
