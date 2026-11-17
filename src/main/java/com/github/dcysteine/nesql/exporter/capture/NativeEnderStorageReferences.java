package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeDiagramContents.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.task.ServerSnapshots;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.server.integrated.IntegratedServer;

/**
 * Native global/current-personal world state, never a consuming recipe or lazy diagram callback.
 */
final class NativeEnderStorageReferences implements NativeReferences {
    static final String PREFIX = "neicustomdiagram.diagramgroup.enderstorage.";
    private static final String ROOT = "codechicken.enderstorage.";
    private final ICraftingHandler handler;
    private final JsonObject source;
    private final Object info, matcher, actions, manager, player, world;
    private final IntegratedServer server;
    private final String personal, type, name, description;
    private final int configured, capacity;
    private final Item lockItem;
    private final NativeEnderStorageSnapshot snapshot;
    private final JsonArray rows;
    private boolean closed;

    NativeEnderStorageReferences(ICraftingHandler handler, JsonObject source) {
        this.handler = handler;
        this.source = copy(source);
        String suffix = handler.getHandlerId().substring(PREFIX.length());
        if (!Arrays.asList("chestoverview", "tankoverview").contains(suffix)
                || !source.get("handler").getAsString().equals(handler.getClass().getName())
                || !source.get("key").getAsString().equals(handler.getHandlerId())
                || !handler.getClass().getName().equals(API + "diagram.CustomDiagramGroup"))
            throw fault("Unknown EnderStorage overview source");
        MagicApi.version("NativeEnderStorageReferences","EnderStorage");
        type = suffix.equals("chestoverview") ? "item" : "liquid";
        info = field(handler, "info");
        matcher = field(handler, "matcher");
        actions = field(handler, "customBehaviorMap");
        String generator =
                "com.github.dcysteine.neicustomdiagram.generators.enderstorage."
                        + suffix
                        + ".EnderStorage"
                        + (type.equals("item") ? "Chest" : "Tank")
                        + "Overview";
        if (matcher == null
                || !matcher.getClass()
                        .getName()
                        .equals(API + "diagram.matcher.CustomDiagramMatcher")
                || !list(field(matcher, "allDiagrams")).isEmpty())
            throw fault("Unknown EnderStorage dynamic matcher");
        nativeCallback(field(matcher, "diagramFunction"), generator);
        Map<?, ?> behavior = map(actions);
        if (behavior.size() != 2) throw fault("Pinned NEICustomDiagram 1.7.5 actions differ");
        for (String owner : Arrays.asList("global", "personal")) {
                Object supplier = behavior.get(handler.getHandlerId() + "-" + owner);
                if (supplier == null) throw fault("Missing native EnderStorage action");
                nativeCallback(supplier, generator);
            }
        name = (String) call(info, "groupName");
        description = (String) call(info, "description");
        if (!(boolean) call(info, "ignoreNbt"))
            throw fault("Native EnderStorage query policy differs");
        Minecraft game = Minecraft.getMinecraft();
        server = game.getIntegratedServer();
        player = game.thePlayer;
        world = game.theWorld;
        if (server == null || player == null || world == null || !game.isSingleplayer())
            throw new Jobs.Fault(
                    "handler_not_ready",
                    "EnderStorage requires a local integrated-server snapshot");
        personal = game.thePlayer.getDisplayName();
        configured = (int) field(type(ROOT + "storage.EnderItemStoragePlugin"), null, "configSize");
        capacity = (int) field(type(ROOT + "storage.liquid.EnderLiquidStorage"), null, "CAPACITY");
        manager = field(type(ROOT + "api.EnderStorageManager"), null, "serverManager");
        JsonArray queryRules = array();
        Class<?> ender = type(ROOT + "EnderStorage");
        Item block = Item.getItemFromBlock((Block) field(ender, null, "blockEnderChest"));
        queryRules.add(
                object(
                        "role",
                        "block",
                        "registry",
                        registry(block),
                        "highMetadata",
                        type.equals("item") ? 0 : 1));
        if (type.equals("item"))
            queryRules.add(
                    object(
                            "role",
                            "pouch",
                            "registry",
                            registry((Item) field(ender, null, "itemEnderPouch")),
                            "highMetadata",
                            null));
        lockItem = (Item) field(ender, null, "personalItem");
        registry(lockItem);
        try {
            snapshot =
                    ServerSnapshots.INSTANCE.call(
                            server,
                            () -> {
                                if (field(
                                                type(ROOT + "api.EnderStorageManager"),
                                                null,
                                                "serverManager")
                                        != manager)
                                    throw new Jobs.Fault(
                                            "environment_changed",
                                            "EnderStorage server manager changed before snapshot");
                                return NativeEnderStorageSnapshot.read(
                                        manager,
                                        personal,
                                        type,
                                        configured,
                                        (int[])
                                                field(
                                                        type(
                                                                ROOT
                                                                        + "storage.EnderItemStoragePlugin"),
                                                        null,
                                                        "sizes"),
                                        capacity,
                                        server.worldServers[0].getTotalWorldTime());
                            });
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot capture owned EnderStorage state", failure);
        }
        try {
            NativeDiagramContents configuration = new NativeDiagramContents();
            JsonObject lock =
                    configuration.stack(new net.minecraft.item.ItemStack(lockItem, 1, 0), true);
            snapshot.items.putAll(configuration.items);
            rows =
                    records(
                            source,
                            name,
                            description,
                            type,
                            personal,
                            configured,
                            capacity,
                            queryRules,
                            lock,
                            snapshot);
        } catch (RuntimeException | Error failure) {
            snapshot.close();
            throw failure;
        }
    }

    static JsonArray records(
            JsonObject source,
            String name,
            String description,
            String type,
            String personal,
            int configured,
            int capacity,
            JsonArray rules,
            JsonObject lock,
            NativeEnderStorageSnapshot snapshot) {
        JsonArray data = array();
        String group = source.get("key").getAsString();
        JsonArray actions = array();
        for (String owner : Arrays.asList("global", "personal"))
                actions.add(
                        object(
                                "key",
                                group + "-" + owner,
                                "owner",
                                owner,
                                "requiresNewSnapshot",
                                false));
        data.add(
                object(
                        "kind",
                        "enderStorageDefinition",
                        "source",
                        source,
                        "group",
                        group,
                        "name",
                        name,
                        "description",
                        description,
                        "storageType",
                        type,
                        "personalOwner",
                        personal,
                        "capturedTick",
                        Long.toString(snapshot.tick),
                        "scope",
                        "integratedServerGlobalAndCurrentPersonal",
                        "consistency",
                        "ownedAtServerTick",
                        "absentFrequency",
                        "unallocatedNativeEmpty",
                        "ready",
                        true,
                        "configuredSize",
                        configured,
                        "sizes",
                        array(9, 27, 54),
                        "tankCapacity",
                        capacity,
                        "queryRules",
                        rules,
                        "personalLockItem",
                        lock,
                        "ignoreNbt",
                        true,
                        "actions",
                        actions,
                        "counts",
                        object(
                                "states",
                                snapshot.states.size(),
                                "loaded",
                                snapshot.loaded,
                                "persisted",
                                snapshot.persisted),
                        "consuming",
                        false));
        for (int i = 0; i < snapshot.states.size(); i++) {
            JsonObject row = copy(snapshot.states.get(i).getAsJsonObject());
            row.addProperty("kind", "enderStorageState");
            row.add("source", copy(source));
            row.addProperty("group", group);
            row.addProperty("state", i);
            row.addProperty("consuming", false);
            data.add(row);
        }
        String domain =
                Identity.content(
                        "reference", object("kind", "enderStorageSnapshot", "records", data));
        JsonArray rows = array();
        long bytes = 0;
        for (int i = 0; i < data.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", data.get(i));
            row.addProperty("id", Identity.content("refrow", row));
            int length = CanonicalJson.bytes(row).length;
            if (length > 1024 * 1024 || (bytes += length) > 32L * 1024 * 1024)
                throw fault("EnderStorage transport budget exceeded");
            rows.add(row);
        }
        return rows;
    }

    private static void nativeCallback(Object callback, String owner) {
        List<Object> captures = captured(callback);
        if (!callback.getClass().getName().startsWith(owner + "$$Lambda$")
                || captures.size() != 1
                || !captures.get(0).getClass().getName().equals(owner))
            throw fault("Unknown EnderStorage callback owner");
    }

    private static String registry(Item item) {
        String registry = Item.itemRegistry.getNameForObject(item);
        if (item == null || registry == null || Item.itemRegistry.getObject(registry) != item)
            throw fault("EnderStorage query item is unregistered");
        return registry;
    }

    public int size() {
        verify();
        return rows.size();
    }

    public String domain() {
        verify();
        return rows.get(0).getAsJsonObject().get("domain").getAsString();
    }

    public JsonObject counts() {
        verify();
        return copy(
                rows.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));
    }

    public void verify() {
        if (closed) throw new Jobs.Fault("capture_closed", "EnderStorage snapshot is closed");
        Jobs.checkpoint();
        NativeEnderStorageSnapshot.requireNativePlugins();
        if (!Arrays.equals(
                (int[]) field(type(ROOT + "storage.EnderItemStoragePlugin"), null, "sizes"),
                new int[] {9, 27, 54}))
            throw new Jobs.Fault("environment_changed", "EnderStorage size table changed");
        Minecraft game = Minecraft.getMinecraft();
        if (game.getIntegratedServer() != server
                || game.theWorld != world
                || game.thePlayer != player
                || !game.thePlayer.getDisplayName().equals(personal)
                || field(type(ROOT + "api.EnderStorageManager"), null, "serverManager") != manager
                || field(handler, "matcher") != matcher
                || field(handler, "customBehaviorMap") != actions
                || field(handler, "info") != info
                || (int) field(type(ROOT + "storage.EnderItemStoragePlugin"), null, "configSize")
                        != configured
                || (int) field(type(ROOT + "storage.liquid.EnderLiquidStorage"), null, "CAPACITY")
                        != capacity
                || !Objects.equals(call(info, "groupName"), name)
                || !Objects.equals(call(info, "description"), description))
            throw new Jobs.Fault(
                    "environment_changed",
                    "EnderStorage world, owner, manager, callback, configuration or locale changed");
        if (field(type(ROOT + "EnderStorage"), null, "personalItem") != lockItem)
            throw new Jobs.Fault("environment_changed", "EnderStorage personal lock item changed");
    }

    public void finishVerification() {
        verify(); /* Contents deliberately belong to capturedTick, not a later live tick. */
    }

    public void capture(int index, Facts facts) {
        verify();
        JsonObject row = copy(rows.get(index).getAsJsonObject());
        publish(row.get("data"), facts);
        facts.row("references", row);
    }

    private void publish(JsonElement value, Facts facts) {
        NativeComponentFacts.visit(
                value,
                id -> {
                    if (snapshot.items.containsKey(id)) {
                        if (!id.equals(facts.item(snapshot.items.get(id))))
                            throw fault("Owned item identity drift");
                    } else if (snapshot.fluids.containsKey(id)) {
                        if (!id.equals(facts.fluid(snapshot.fluids.get(id))))
                            throw fault("Owned fluid identity drift");
                    } else throw fault("Missing snapshot fact");
                },
                facts::text);
    }

    public void close() {
        if (!closed) {
            snapshot.close();
            Iterator<JsonElement> entries = rows.iterator();
            while (entries.hasNext()) {
                entries.next();
                entries.remove();
            }
            closed = true;
        }
    }

    private static Object call(Object value, String method) {
        return invoke(value.getClass(), value, method, new Class<?>[0]);
    }

    private static JsonObject copy(JsonObject value) {
        return new JsonParser().parse(value.toString()).getAsJsonObject();
    }

    private static Jobs.Fault fault(String message) {
        return new Jobs.Fault("ender_storage", message);
    }
}
