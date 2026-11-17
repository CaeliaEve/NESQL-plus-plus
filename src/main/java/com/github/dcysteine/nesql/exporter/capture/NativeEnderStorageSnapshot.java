package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.*;

/** Owned native contents at one server tick. Never creates storage, aligns size or saves it. */
final class NativeEnderStorageSnapshot implements AutoCloseable {
    private static final String ROOT = "codechicken.enderstorage.";
    final JsonArray states = array();
    final Map<String, ItemStack> items = new TreeMap<>();
    final Map<String, FluidStack> fluids = new TreeMap<>();
    final long tick;
    int loaded, persisted;
    private long bytes;
    private final NativeDiagramContents components = new NativeDiagramContents();

    private NativeEnderStorageSnapshot(long tick) {
        this.tick = tick;
    }

    static NativeEnderStorageSnapshot read(
            Object manager,
            String personal,
            String type,
            int configured,
            int[] sizes,
            int capacity,
            long tick) {
        requireNativePlugins();
        if (manager == null
                || !manager.getClass().getName().equals(ROOT + "api.EnderStorageManager")
                || (boolean) field(manager, "client"))
            throw fault("A complete local server manager is required");
        if (personal == null
                || personal.isEmpty()
                || personal.getBytes(StandardCharsets.UTF_8).length > 65536
                || !Arrays.equals(sizes, new int[] {9, 27, 54})
                || configured < 0
                || configured >= sizes.length
                || capacity <= 0
                || tick < 0
                || !Arrays.asList("item", "liquid").contains(type))
            throw fault("Invalid snapshot context or native storage configuration");
        NativeEnderStorageSnapshot result = new NativeEnderStorageSnapshot(tick);
        try {
            Map<?, ?> live = (Map<?, ?>) field(manager, "storageMap");
            NBTTagCompound saved = (NBTTagCompound) field(manager, "saveTag");
            if (saved == null || live.size() > 262144 || saved.func_150296_c().size() > 262144)
                throw fault("Server state is unavailable or exceeds its key budget");
            TreeMap<String, Object> selected = new TreeMap<>();
            for (Object raw : saved.func_150296_c()) {
                Jobs.checkpoint();
                String key = (String) raw;
                if (accept(key, personal, type)) {
                    if (!(saved.getTag(key) instanceof NBTTagCompound))
                        throw fault("Invalid persisted channel tag");
                    selected.put(key, saved.getCompoundTag(key));
                    if (selected.size() > 8192)
                        throw fault("Owner/frequency state budget exceeded");
                }
            }
            synchronized (live) {
                for (Map.Entry<?, ?> entry : live.entrySet()) {
                    Jobs.checkpoint();
                    if (!(entry.getKey() instanceof String))
                        throw fault("Invalid loaded channel key");
                    String key = (String) entry.getKey();
                    if (accept(key, personal, type)) {
                        selected.put(key, entry.getValue());
                        if (selected.size() > 8192)
                            throw fault("Owner/frequency state budget exceeded");
                    }
                }
                if (selected.size() > 8192) throw fault("Owner/frequency state budget exceeded");
                List<String> keys = new ArrayList<>(selected.keySet());
                keys.sort(
                        Comparator.comparing((String key) -> owner(key).equals("global") ? 0 : 1)
                                .thenComparingInt(NativeEnderStorageSnapshot::frequency));
                for (String key : keys) {
                    Jobs.checkpoint();
                    Object value = selected.get(key);
                    JsonObject contents;
                    JsonElement raw;
                    String layer;
                    if (value instanceof NBTTagCompound) {
                        NBTTagCompound tag = (NBTTagCompound) value;
                        budget(tag);
                        raw = TypedNbt.encode(tag);
                        contents =
                                type.equals("item")
                                        ? persistedItems(tag, sizes, result)
                                        : persistedFluid(tag, capacity, result);
                        layer = "persistedServer";
                        result.persisted++;
                    } else {
                        String expected =
                                ROOT
                                        + (type.equals("item")
                                                ? "storage.item.EnderItemStorage"
                                                : "storage.liquid.EnderLiquidStorage");
                        if (value == null
                                || !value.getClass().getName().equals(expected)
                                || field(value, "manager") != manager
                                || !Objects.equals(field(value, "owner"), owner(key))
                                || (int) field(value, "freq") != frequency(key))
                            throw fault("Loaded storage ownership/type/key differs");
                        synchronized (value) {
                            contents =
                                    type.equals("item")
                                            ? liveItems(value, sizes, result)
                                            : liveFluid(value, capacity, result);
                        }
                        raw = JsonNull.INSTANCE;
                        layer = "loadedServer";
                        result.loaded++;
                    }
                    int freq = frequency(key);
                    JsonArray colours =
                            type.equals("item")
                                    ? array(freq >> 8 & 15, freq >> 4 & 15, freq & 15)
                                    : array(freq & 15, freq >> 4 & 15, freq >> 8 & 15);
                    JsonObject state =
                            object(
                                    "owner",
                                    owner(key),
                                    "frequency",
                                    freq,
                                    "colours",
                                    colours,
                                    "layer",
                                    layer,
                                    "raw",
                                    raw,
                                    "contents",
                                    contents);
                    int length = CanonicalJson.bytes(state).length;
                    if (length > 1024 * 1024 || (result.bytes += length) > 32L * 1024 * 1024)
                        throw fault("Native storage snapshot byte budget exceeded");
                    result.states.add(state);
                }
            }
            result.items.putAll(result.components.items);
            result.fluids.putAll(result.components.fluids);
            return result;
        } catch (RuntimeException | Error failure) {
            result.close();
            throw failure;
        }
    }

    static void requireNativePlugins() {
        Map<?, ?> plugins =
                (Map<?, ?>) field(type(ROOT + "api.EnderStorageManager"), null, "plugins");
        for (String kind : Arrays.asList("item", "liquid")) {
            Object plugin = plugins.get(kind);
            String expected =
                    ROOT
                            + "storage.Ender"
                            + (kind.equals("item") ? "Item" : "Liquid")
                            + "StoragePlugin";
            if (plugin == null || !plugin.getClass().getName().equals(expected))
                throw fault("Unknown or unready EnderStorage plugin: " + kind);
        }
    }

    private static boolean accept(String key, String personal, String type) {
        if (key.length() > 65580) throw fault("Storage key exceeds its budget");
        int first = key.indexOf('|'), last = key.lastIndexOf('|');
        if (first < 1 || last <= first) throw fault("Malformed native storage key");
        String owner = key.substring(first + 1, last), kind = key.substring(last + 1);
        if (!kind.equals(type) || !owner.equals("global") && !owner.equals(personal)) return false;
        frequency(key);
        return true;
    }

    private static String owner(String key) {
        return key.substring(key.indexOf('|') + 1, key.lastIndexOf('|'));
    }

    private static int frequency(String key) {
        String raw = key.substring(0, key.indexOf('|'));
        if (!raw.matches("0|[1-9][0-9]{0,3}")) throw fault("Invalid native frequency key");
        int value = Integer.parseInt(raw);
        if (value > 4095) throw fault("Native frequency exceeds 12 bits");
        return value;
    }

    private static JsonObject liveItems(
            Object value, int[] sizes, NativeEnderStorageSnapshot result) {
        int size = (int) field(value, "size");
        ItemStack[] stacks = (ItemStack[]) field(value, "items");
        if (size < 0 || size >= sizes.length || stacks == null || stacks.length != sizes[size])
            throw fault("Loaded storage size differs");
        return items(size, stacks, result);
    }

    private static JsonObject persistedItems(
            NBTTagCompound tag, int[] sizes, NativeEnderStorageSnapshot result) {
        if (!tag.hasKey("size", 1) || !tag.hasKey("Items", 9))
            throw fault("Persisted chest fields are missing");
        int size = tag.getByte("size");
        if (size < 0 || size >= sizes.length) throw fault("Persisted chest size is invalid");
        ItemStack[] stacks = new ItemStack[sizes[size]];
        NBTTagList entries = tag.getTagList("Items", 10);
        NBTTagList raw = (NBTTagList) tag.getTag("Items");
        if (raw.tagCount() != 0 && raw.func_150303_d() != 10)
            throw fault("Persisted items have the wrong native list element type");
        if (entries.tagCount() > stacks.length) throw fault("Persisted item budget exceeded");
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (!entry.hasKey("Slot", 2)) throw fault("Persisted item has no native short slot");
            int slot = entry.getShort("Slot");
            if (slot < 0 || slot >= stacks.length || stacks[slot] != null)
                throw fault("Persisted item slot is out of range or repeated");
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (stack == null) throw fault("Persisted item registry identity is unresolved");
            if (entry.hasKey("Quantity")) {
                NBTBase quantity = entry.getTag("Quantity");
                if (!(quantity instanceof NBTBase.NBTPrimitive))
                    throw fault("Persisted Quantity is not native numeric data");
                stack.stackSize = ((NBTBase.NBTPrimitive) quantity).func_150287_d();
            }
            stacks[slot] = stack;
        }
        return items(size, stacks, result);
    }

    private static JsonObject items(
            int size, ItemStack[] stacks, NativeEnderStorageSnapshot result) {
        JsonArray slots = array();
        boolean nonEmpty = false;
        long bytes = 0;
        for (ItemStack stack : stacks) {
            Jobs.checkpoint();
            if (stack == null) {
                slots.add(JsonNull.INSTANCE);
                continue;
            }
            if (stack.stackSize < 0) throw fault("Native inventory has a negative quantity");
            budget(stack.getTagCompound());
            result.components.beginQuery();
            JsonObject slot =
                    object(
                            "component",
                            result.components.stack(stack, true),
                            "amount",
                            Integer.toString(stack.stackSize));
            if ((bytes += CanonicalJson.bytes(slot).length) > 1024 * 1024)
                throw fault("Native chest contents exceed their leaf byte budget");
            slots.add(slot);
            nonEmpty = true;
        }
        return object("kind", "item", "sizeIndex", size, "slots", slots, "nonEmpty", nonEmpty);
    }

    private static JsonObject liveFluid(
            Object value, int capacity, NativeEnderStorageSnapshot result) {
        Object tank = field(value, "tank");
        if (tank == null
                || !tank.getClass()
                        .getName()
                        .equals(ROOT + "storage.liquid.EnderLiquidStorage$Tank")
                || (int) call(tank, "getCapacity") != capacity)
            throw fault("Loaded tank capacity differs");
        FluidStack stack = (FluidStack) field(tank, "fluid");
        return fluid(stack, capacity, result);
    }

    private static JsonObject persistedFluid(
            NBTTagCompound tag, int capacity, NativeEnderStorageSnapshot result) {
        if (!tag.hasKey("tank", 10)) throw fault("Persisted tank fields are missing");
        NBTTagCompound tank = tag.getCompoundTag("tank");
        FluidStack stack = FluidStack.loadFluidStackFromNBT(tank);
        // This is the actual pinned FluidUtils.read empty-tag rule; unknown names must not hide as
        // empty.
        if (stack == null) {
            if (!tank.hasNoTags()) throw fault("Persisted fluid registry identity is unresolved");
            stack = new FluidStack(FluidRegistry.WATER, 0);
        }
        return fluid(stack, capacity, result);
    }

    private static JsonObject fluid(
            FluidStack stack, int capacity, NativeEnderStorageSnapshot result) {
        if (stack == null)
            return object(
                    "kind",
                    "liquid",
                    "capacity",
                    capacity,
                    "fluid",
                    null,
                    "amount",
                    "0",
                    "nonEmpty",
                    false);
        if (stack.amount < 0 || stack.amount > capacity)
            throw fault("Native fluid amount exceeds its capacity");
        budget(stack.tag);
        result.components.beginQuery();
        return object(
                "kind",
                "liquid",
                "capacity",
                capacity,
                "fluid",
                result.components.stack(stack, true),
                "amount",
                Integer.toString(stack.amount),
                "nonEmpty",
                stack.amount != 0);
    }

    private static Object call(Object value, String method) {
        return invoke(value.getClass(), value, method, new Class<?>[0]);
    }

    static void budget(NBTBase tag) {
        if (tag != null) new NbtBudget().visit(tag, 0);
    }

    private static final class NbtBudget {
        int nodes;
        long bytes;

        void visit(NBTBase tag, int depth) {
            if (depth > 48 || ++nodes > 65536 || (bytes += 64) > 512 * 1024)
                throw fault("Native snapshot NBT exceeds its budget");
            switch (tag.getId()) {
                case 7:
                    bytes += ((NBTTagByteArray) tag).func_150292_c().length * 2L;
                    break;
                case 8:
                    bytes +=
                            ((NBTTagString) tag)
                                            .func_150285_a_()
                                            .getBytes(StandardCharsets.UTF_8)
                                            .length
                                    * 6L;
                    break;
                case 9:
                    {
                        List<?> entries =
                                ReflectionHelper.getPrivateValue(
                                        NBTTagList.class,
                                        (NBTTagList) tag,
                                        "tagList",
                                        "field_74747_a");
                        if (entries.size() > 65536) throw fault("NBT list exceeds its budget");
                        for (Object entry : entries) visit((NBTBase) entry, depth + 1);
                        break;
                    }
                case 10:
                    for (Object raw : ((NBTTagCompound) tag).func_150296_c()) {
                        String key = (String) raw;
                        bytes += key.getBytes(StandardCharsets.UTF_8).length * 6L;
                        visit(((NBTTagCompound) tag).getTag(key), depth + 1);
                    }
                    break;
                case 11:
                    bytes += ((NBTTagIntArray) tag).func_150302_c().length * 16L;
                    break;
                default:
                    if (tag.getId() < 1 || tag.getId() > 11)
                        throw fault("Unknown snapshot NBT type");
            }
            if (bytes > 512 * 1024) throw fault("Native snapshot NBT exceeds its byte budget");
        }
    }

    public void close() {
        Iterator<JsonElement> entries = states.iterator();
        while (entries.hasNext()) {
            entries.next();
            entries.remove();
        }
        items.clear();
        fluids.clear();
        components.items.clear();
        components.fluids.clear();
    }

    private static Jobs.Fault fault(String message) {
        return new Jobs.Fault("ender_storage", message);
    }
}
