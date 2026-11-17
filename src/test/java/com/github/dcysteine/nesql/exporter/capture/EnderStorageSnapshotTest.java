package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Actual installed storage objects; no manager load/save, lazy lookup, refresh or packet call. */
final class EnderStorageSnapshotTest {
    private static final String ROOT = "codechicken.enderstorage.";

    static void run() throws Exception {
        Class<?> managerClass = type(ROOT + "api.EnderStorageManager");
        sun.misc.Unsafe unsafe = unsafe();
        Object manager = unsafe.allocateInstance(managerClass);
        set(manager, "client", false);
        Map<String, Object> loaded = Collections.synchronizedMap(new HashMap<>());
        set(manager, "storageMap", loaded);
        set(manager, "storageList", Collections.synchronizedMap(new HashMap<>()));
        set(manager, "dirtyStorage", Collections.synchronizedList(new ArrayList<>()));
        NBTTagCompound saved = new NBTTagCompound();
        set(manager, "saveTag", saved);
        Object plugin = type(ROOT + "storage.EnderItemStoragePlugin");
        Class<?> pluginClass = (Class<?>) plugin;
        Field config = pluginClass.getDeclaredField("configSize");
        config.setAccessible(true);
        int priorConfig = config.getInt(null);
        Map<String, Object> plugins = (Map<String, Object>) field(managerClass, null, "plugins");
        Map<String, Object> priorPlugins = new HashMap<>(plugins);
        plugins.put("item", type(ROOT + "storage.EnderItemStoragePlugin").newInstance());
        plugins.put("liquid", type(ROOT + "storage.EnderLiquidStoragePlugin").newInstance());
        config.setInt(null, 1);
        try {
            Object inventory =
                    type(ROOT + "storage.item.EnderItemStorage")
                            .getConstructor(managerClass, String.class, int.class)
                            .newInstance(manager, "global", 0x123);
            ItemStack[] live = new ItemStack[54];
            live[40] = new ItemStack(Items.paper, 7);
            set(inventory, "items", live);
            set(inventory, "size", 2);
            set(inventory, "open", 0);
            loaded.put("291|global|item", inventory);
            saved.setTag("291|global|item", persisted(0, 0, new ItemStack(Items.iron_ingot, 3)));
            saved.setTag("564|Player|Test|item", persisted(0, 8, new ItemStack(Items.paper, 5)));
            saved.setTag("565|Other|item", persisted(0, 0, new ItemStack(Items.paper, 11)));
            NBTTagCompound before = (NBTTagCompound) saved.copy();
            NativeEnderStorageSnapshot snapshot =
                    NativeEnderStorageSnapshot.read(
                            manager, "Player|Test", "item", 1, new int[] {9, 27, 54}, 16000, 99);
            require(
                    snapshot.states.size() == 2,
                    "Unloaded persisted channel disappeared or another owner's channel leaked");
            JsonObject global = snapshot.states.get(0).getAsJsonObject(),
                    personal = snapshot.states.get(1).getAsJsonObject();
            require(
                    global.get("layer").getAsString().equals("loadedServer")
                            && global.getAsJsonObject("contents")
                                    .getAsJsonArray("slots")
                                    .get(40)
                                    .getAsJsonObject()
                                    .get("amount")
                                    .getAsString()
                                    .equals("7"),
                    "Loaded contents did not override old persisted contents");
            require(
                    global.getAsJsonObject("contents").getAsJsonArray("slots").size() == 54
                            && personal.getAsJsonObject("contents").getAsJsonArray("slots").size()
                                    == 9,
                    "Stored size or original empty positions were aligned away");
            require(
                    personal.get("owner").getAsString().equals("Player|Test"),
                    "Owner key parsing lost a literal delimiter");
            live[40].stackSize = 17;
            require(
                    snapshot.items.values().stream().anyMatch(stack -> stack.stackSize == 7),
                    "Snapshot retained a live ItemStack");
            require(
                    before.equals(saved)
                            && ((ItemStack[]) field(inventory, "items")) == live
                            && (int) field(inventory, "size") == 2
                            && ((List<?>) field(manager, "dirtyStorage")).isEmpty(),
                    "Read-only capture resized, dirtied or saved native state");
            saved.setTag("566|Player|Test|item", persisted(0, 10, new ItemStack(Items.paper)));
            try {
                NativeEnderStorageSnapshot.read(
                        manager, "Player|Test", "item", 1, new int[] {9, 27, 54}, 16000, 100);
                throw new AssertionError("Out-of-range saved slot was hidden");
            } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                require(expected.code.equals("ender_storage"), expected.toString());
            }
            saved.removeTag("566|Player|Test|item");
            NBTTagCompound tank = new NBTTagCompound();
            tank.setTag(
                    "tank",
                    new net.minecraftforge.fluids.FluidStack(
                                    net.minecraftforge.fluids.FluidRegistry.WATER, 1250)
                            .writeToNBT(new NBTTagCompound()));
            saved.setTag("291|global|liquid", tank);
            NativeEnderStorageSnapshot liquids =
                    NativeEnderStorageSnapshot.read(
                            manager, "Player|Test", "liquid", 1, new int[] {9, 27, 54}, 16000, 99);
            Object nativePlugin = plugins.put("item", new Object());
            try {
                NativeEnderStorageSnapshot.read(
                        manager, "Player|Test", "item", 1, new int[] {9, 27, 54}, 16000, 100);
                throw new AssertionError("Unknown storage plugin was accepted as native");
            } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                require(expected.code.equals("ender_storage"), expected.toString());
            } finally {
                plugins.put("item", nativePlugin);
            }
            require(
                    liquids.states
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("colours")
                            .toString()
                            .equals("[3,2,1]"),
                    "Tank's native display colours were not reversed");
            require(
                    liquids.states
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("contents")
                            .get("amount")
                            .getAsString()
                            .equals("1250"),
                    "Tank amount was lost");
            JsonObject lock =
                    new NativeDiagramContents().stack(new ItemStack(Items.diamond), false);
            Field tankSize = type(ROOT + "EnderStorage").getDeclaredField("enderTankSize");
            tankSize.setAccessible(true);
            int priorTankSize = tankSize.getInt(null);
            tankSize.setInt(null, 16);
            try {
                Object liveTank =
                        type(ROOT + "storage.liquid.EnderLiquidStorage")
                                .getConstructor(managerClass, String.class, int.class)
                                .newInstance(manager, "global", 0x123);
                Object tankBody = field(liveTank, "tank");
                set(
                        tankBody,
                        "fluid",
                        new net.minecraftforge.fluids.FluidStack(
                                net.minecraftforge.fluids.FluidRegistry.WATER, 2000));
                loaded.put("291|global|liquid", liveTank);
                NativeEnderStorageSnapshot liveLiquids =
                        NativeEnderStorageSnapshot.read(
                                manager,
                                "Player|Test",
                                "liquid",
                                1,
                                new int[] {9, 27, 54},
                                16000,
                                100);
                require(
                        liveLiquids.loaded == 1
                                && liveLiquids.persisted == 0
                                && liveLiquids
                                        .states
                                        .get(0)
                                        .getAsJsonObject()
                                        .getAsJsonObject("contents")
                                        .get("amount")
                                        .getAsString()
                                        .equals("2000"),
                        "Loaded native tank did not override stale stored fluid");
                liveLiquids.close();
                loaded.remove("291|global|liquid");
            } finally {
                tankSize.setInt(null, priorTankSize);
            }
            snapshot.items.put(lock.get("fact").getAsString(), new ItemStack(Items.diamond));
            JsonArray domains = array();
            for (String storage : new String[] {"item", "liquid"}) {
                String key =
                        NativeEnderStorageReferences.PREFIX
                                + (storage.equals("item") ? "chestoverview" : "tankoverview");
                JsonObject source =
                        object(
                                "owner",
                                "neicustomdiagram",
                                "handler",
                                "com.github.dcysteine.neicustomdiagram.api.diagram.CustomDiagramGroup",
                                "key",
                                key);
                JsonArray rules = array();
                rules.add(
                        object(
                                "role",
                                "block",
                                "registry",
                                "minecraft:paper",
                                "highMetadata",
                                storage.equals("item") ? 0 : 1));
                if (storage.equals("item"))
                    rules.add(
                            object(
                                    "role",
                                    "pouch",
                                    "registry",
                                    "minecraft:iron_ingot",
                                    "highMetadata",
                                    null));
                domains.add(
                        NativeEnderStorageReferences.records(
                                source,
                                "Controlled EnderStorage state",
                                "Native API state specimen",
                                storage,
                                "Player|Test",
                                1,
                                16000,
                                rules,
                                lock,
                                storage.equals("item") ? snapshot : liquids));
            }
            JsonArray items = array();
            for (ItemStack stack : snapshot.items.values()) {
                JsonObject component = new NativeDiagramContents().stack(stack, false);
                items.add(
                        object(
                                "id",
                                component.get("fact"),
                                "registry",
                                component.get("registry"),
                                "meta",
                                component.get("meta"),
                                "nbt",
                                component.get("nbt")));
            }
            JsonArray fluids = array();
            for (net.minecraftforge.fluids.FluidStack stack : liquids.fluids.values()) {
                JsonObject component = new NativeDiagramContents().stack(stack, false);
                net.minecraftforge.fluids.Fluid fluid = stack.getFluid();
                fluids.add(
                        object(
                                "id",
                                component.get("fact"),
                                "registry",
                                component.get("registry"),
                                "nbt",
                                component.get("nbt"),
                                "temperature",
                                fluid.getTemperature(stack),
                                "density",
                                fluid.getDensity(stack),
                                "viscosity",
                                fluid.getViscosity(stack),
                                "luminosity",
                                fluid.getLuminosity(stack),
                                "gaseous",
                                fluid.isGaseous(stack)));
            }
            NativeContractFixture.directory();
            set(manager, "client", true);
            try {
                NativeEnderStorageSnapshot.read(
                        manager, "Player|Test", "item", 1, new int[] {9, 27, 54}, 16000, 100);
                throw new AssertionError(
                        "Partial client observations pretended to be a complete server snapshot");
            } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                require(expected.code.equals("ender_storage"), expected.toString());
            } finally {
                set(manager, "client", false);
            }
            set(manager, "saveTag", new NBTTagCompound());
            set(manager, "storageMap", Collections.synchronizedMap(new HashMap<>()));
            NativeEnderStorageSnapshot empty =
                    NativeEnderStorageSnapshot.read(
                            manager, "Player|Test", "item", 1, new int[] {9, 27, 54}, 16000, 100);
            require(
                    empty.states.size() == 0 && empty.loaded == 0 && empty.persisted == 0,
                    "A ready empty snapshot was treated as unready");
            JsonObject first =
                    domains.get(0)
                            .getAsJsonArray()
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("data");
            require(first.getAsJsonArray("actions").size()==2,
                    "Pinned NEICustomDiagram 1.7.5 has global/personal actions, without refresh callbacks");
            JsonArray emptyRows =
                    NativeEnderStorageReferences.records(
                            first.getAsJsonObject("source"),
                            "Controlled empty state",
                            "Native API empty specimen",
                            "item",
                            "Player|Test",
                            1,
                            16000,
                            first.getAsJsonArray("queryRules"),
                            lock,
                            empty);
            Files.write(
                    NativeContractFixture.directory().resolve("enderstorage-empty-contract.json"),
                    CanonicalJson.bytes(
                            object("records", emptyRows, "items", items, "fluids", fluids)));
            empty.close();
            Files.write(
                    NativeContractFixture.directory().resolve("enderstorage-state-sample.json"),
                    CanonicalJson.bytes(
                            object(
                                    "chests",
                                    snapshot.states,
                                    "tanks",
                                    liquids.states,
                                    "items",
                                    items,
                                    "fluids",
                                    fluids)));
            Files.write(
                    NativeContractFixture.directory().resolve("enderstorage-contract.json"),
                    CanonicalJson.bytes(
                            object("domains", domains, "items", items, "fluids", fluids)));
            System.out.println(
                    "EnderStorage snapshots: live/persisted precedence, literal owners, stored slots, tank colours, immutable copies, malformed slot/unknown plugin/client rejection and ready empty state passed");
            snapshot.close();
            liquids.close();
        } finally {
            config.setInt(null, priorConfig);
            plugins.clear();
            plugins.putAll(priorPlugins);
        }
    }

    private static NBTTagCompound persisted(int size, int slot, ItemStack stack) {
        NBTTagCompound result = new NBTTagCompound(), entry = new NBTTagCompound();
        result.setByte("size", (byte) size);
        stack.writeToNBT(entry);
        entry.setShort("Slot", (short) slot);
        NBTTagList entries = new NBTTagList();
        entries.appendTag(entry);
        result.setTag("Items", entries);
        return result;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> c = target.getClass(); c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new AssertionError(name);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
