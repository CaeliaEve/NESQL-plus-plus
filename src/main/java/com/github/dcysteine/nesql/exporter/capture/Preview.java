package com.github.dcysteine.nesql.exporter.capture;

import blockrenderer6343.client.world.ClientFakePlayer;
import blockrenderer6343.client.world.DummyWorld;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Probe;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.mojang.authlib.GameProfile;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.INEIPreviewModifier;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.threads.RunnableMachineUpdate;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** A bounded native construction in an owned dummy world. Never shares the game's player or registry stacks. */
final class Preview implements AutoCloseable {
    final Space world;
    final IMetaTileEntity machine;
    private final Probe probe;
    private final Models models;
    private final boolean complete;
    private final ItemStack trigger;
    private final ClientFakePlayer actor;
    private final ISurvivalBuildEnvironment environment;
    private final Set<String> notes = new LinkedHashSet<>();
    private Iterator<Position> positions;
    private final int[] minimum = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    private final int[] maximum = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    private final Map<String, Integer> palette = new LinkedHashMap<>();
    private final JsonArray appearances = new JsonArray();
    private final Set<String> blocks = new LinkedHashSet<>();
    private final List<Placed> pending = new ArrayList<>();
    private final JsonArray chunks = new JsonArray();
    private JsonArray cells = new JsonArray();
    private int rounds, count;
    private Integer result;
    private boolean finished, written;

    Preview(Structures.Machine source, Probe probe, Models models, boolean complete) {
        // GT's dummy-world check is an exact class lookup, not instanceof.
        GregTechAPI.addDummyWorld(Space.class);
        world = new Space(); this.probe = probe; this.trigger = trigger(probe);
        this.models = models; this.complete = complete;
        actor = new ClientFakePlayer(world, new GameProfile(new UUID(0, 0), "NESQLPreview")) {
            @Override public void addChatMessage(IChatComponent message) { note(message); }
            @Override public void addChatComponentMessage(IChatComponent message) { note(message); }
        };
        actor.capabilities.isCreativeMode = true;
        actor.setPosition(0, 64, 2);
        environment = ISurvivalBuildEnvironment.create(new Supply(), actor);
        boolean enabled = RunnableMachineUpdate.isCurrentThreadEnabled();
        try {
            RunnableMachineUpdate.setCurrentThreadEnabled(false);
            ItemStack controller = source.machine.getStackForm(1).copy();
            if (!controller.getItem().onItemUse(controller, actor, world, 0, 64, 0, 0, 0, 64, 0)) throw fault("Controller placement failed");
            TileEntity tile = world.getTileEntity(0, 64, 0);
            if (!(tile instanceof IGregTechTileEntity)) throw fault("Controller did not create a GregTech tile");
            IGregTechTileEntity base = (IGregTechTileEntity) tile;
            base.setFrontFacing(ForgeDirection.SOUTH);
            machine = base.getMetaTileEntity();
            if (base.getMetaTileID() != source.id || machine == source.machine || machine.getClass() != source.machine.getClass()
                    || !(machine instanceof IConstructable)) throw fault("Controller placement changed the machine identity");
            if (machine instanceof INEIPreviewModifier) ((INEIPreviewModifier) machine).onPreviewConstruct(this.trigger.copy());
        } catch (RuntimeException | Error failure) {
            try { world.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        } finally { RunnableMachineUpdate.setCurrentThreadEnabled(enabled); }
    }

    static ItemStack trigger(Probe probe) {
        ItemStack stack = new ItemStack(StructureLibAPI.getDefaultHologramItem(), probe.count);
        probe.channels.forEach((name, value) -> ChannelDataAccessor.setChannelData(stack, name, value));
        return stack;
    }

    private void note(IChatComponent message) {
        if (message != null && notes.size() < 128) notes.add(message.getFormattedText());
    }

    /** Survival calls honor their own placement budget; a single third-party callback is not preemptible. */
    boolean build() {
        if (finished) return true;
        Jobs.checkpoint();
        boolean enabled = RunnableMachineUpdate.isCurrentThreadEnabled();
        try {
            RunnableMachineUpdate.setCurrentThreadEnabled(false);
            long before = world.changes;
            if (machine instanceof ISurvivalConstructable) {
                result = ((ISurvivalConstructable) machine).survivalConstruct(trigger.copy(), 256, environment);
                if (result < -1) throw fault("Controller does not support survival construction in the preview environment");
                if (result != -1 && (result > 0 || world.changes != before)) {
                    if (++rounds >= 8192) throw fault("Construction exceeded its round budget");
                    return false;
                }
            } else ((IConstructable) machine).construct(trigger.copy(), false);
            if (machine instanceof INEIPreviewModifier) ((INEIPreviewModifier) machine).onPreviewStructureComplete(trigger.copy());
            if (world.blocks.size() <= 1) throw fault("Construction placed no structure blocks");
            TileEntity tile = world.getTileEntity(0, 64, 0);
            if (!(tile instanceof IGregTechTileEntity) || ((IGregTechTileEntity) tile).getMetaTileEntity() != machine) throw fault("Construction removed its controller");
            List<Position> ordered = new ArrayList<>();
            for (long key : world.blocks.keySet()) {
                Position position = new Position(x(key), y(key), z(key)); ordered.add(position);
                int[] at = {position.x, position.y, position.z};
                for (int axis = 0; axis < 3; axis++) { minimum[axis] = Math.min(minimum[axis], at[axis]); maximum[axis] = Math.max(maximum[axis], at[axis]); }
            }
            ordered.sort(Comparator.comparingInt((Position position) -> -position.z).thenComparingInt(position -> -position.y).thenComparingInt(position -> position.x));
            positions = ordered.iterator(); finished = true;
            return true;
        } finally { RunnableMachineUpdate.setCurrentThreadEnabled(enabled); }
    }

    /** Serializing blocks and tile NBT is also split into bounded client-thread work. */
    String capture(Facts facts, String structure) {
        if (!finished || written) throw new IllegalStateException("Construction is not ready to capture");
        // Texture encoding completes on the worker between cursor calls, before palette identities are assigned.
        for (Placed placed : pending) {
            String key = CanonicalJson.digest(placed.appearance);
            Integer index = palette.get(key);
            if (index == null) {
                if (palette.size() >= 8192) throw fault("Construction exceeds its appearance palette budget");
                index = palette.size(); palette.put(key, index); appearances.add(placed.appearance);
            }
            cells.add(object("at", array(placed.at.x - minimum[0], maximum[1] - placed.at.y, maximum[2] - placed.at.z), "index", index)); count++;
            if (cells.size() == 2048) flush(facts);
        }
        pending.clear();
        long deadline = System.nanoTime() + 2_000_000;
        for (int work = 0; work < (models == null ? 128 : 16) && positions.hasNext(); work++) {
            Jobs.checkpoint();
            if (work > 0 && System.nanoTime() >= deadline) return null;
            Position at = positions.next();
            Block block = world.getBlock(at.x, at.y, at.z);
            String registry = Block.blockRegistry.getNameForObject(block);
            if (registry == null || Block.blockRegistry.getObject(registry) != block) throw fault("Construction contains an unregistered block");
            TileEntity tile = world.getTileEntity(at.x, at.y, at.z);
            NBTTagCompound nbt = null;
            if (tile != null) { nbt = new NBTTagCompound(); tile.writeToNBT(nbt); }
            ItemStack item = block.getPickBlock(new MovingObjectPosition(at.x, at.y, at.z, 1, net.minecraft.util.Vec3.createVectorHelper(at.x, at.y, at.z)), world, at.x, at.y, at.z);
            JsonObject state = object("registry", registry, "meta", world.getBlockMetadata(at.x, at.y, at.z),
                    "nbt", TypedNbt.encode(nbt), "item", item == null || item.getItem() == null ? null : facts.item(item));
            String id = Identity.content("block", state);
            if (blocks.add(id)) { state.addProperty("id", id); facts.row("blocks", state); }
            JsonObject appearance = object("block", id, "model", null, "problem", null);
            if (models != null) {
                long before = world.changes;
                try { facts.model(models.capture(world, at.x, at.y, at.z, appearance)); }
                catch (Jobs.Fault failure) {
                    if (complete || !failure.code.equals("model_unsupported")) throw failure;
                    appearance.addProperty("problem", facts.text(failure.getMessage()));
                }
                if (before != world.changes) throw new Jobs.Fault("preview_changed", "Rendering changed constructed blocks");
                if (tile != null) {
                    NBTTagCompound after = new NBTTagCompound(); tile.writeToNBT(after);
                    if (!CanonicalJson.digest(TypedNbt.encode(nbt)).equals(CanonicalJson.digest(TypedNbt.encode(after)))) {
                        throw new Jobs.Fault("preview_changed", "Rendering changed block entity data");
                    }
                }
            }
            pending.add(new Placed(at, appearance));
        }
        if (!pending.isEmpty() || positions.hasNext()) return null;
        flush(facts);
        JsonArray messages = new JsonArray();
        for (String note : notes) messages.add(value(facts.text(note)));
        JsonObject record = object("structure", structure, "probe", probe.json(),
                "method", machine instanceof ISurvivalConstructable ? "survival" : "creative", "result", result,
                "size", array(maximum[0] - minimum[0] + 1, maximum[1] - minimum[1] + 1, maximum[2] - minimum[2] + 1),
                "origin", array(minimum[0], maximum[1], maximum[2]), "controller", array(-minimum[0], maximum[1] - 64, maximum[2]),
                "palette", appearances, "rendered", models != null, "chunks", chunks, "cells", count, "notes", messages);
        String id = Identity.content("build", record); record.addProperty("id", id); facts.row("builds", record); written = true;
        return id;
    }

    private void flush(Facts facts) {
        if (cells.size() == 0) return;
        JsonObject shape = object("cells", cells); String id = Identity.content("shape", shape);
        shape.addProperty("id", id); facts.row("shapes", shape); chunks.add(value(id)); cells = new JsonArray();
    }
    @Override public void close() {
        boolean enabled = RunnableMachineUpdate.isCurrentThreadEnabled();
        try { RunnableMachineUpdate.setCurrentThreadEnabled(false); world.close(); }
        finally { RunnableMachineUpdate.setCurrentThreadEnabled(enabled); }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("preview_failed", message); }
    private static long key(int x, int y, int z) { return ((long) (x & 65535) << 24) | ((long) (z & 65535) << 8) | y; }
    private static int x(long key) { return (short) (key >>> 24); }
    private static int y(long key) { return (int) (key & 255); }
    private static int z(long key) { return (short) (key >>> 8); }
    private static final class Position {
        final int x, y, z;
        Position(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
    private static final class Placed {
        final Position at; final JsonObject appearance;
        Placed(Position at, JsonObject appearance) { this.at = at; this.appearance = appearance; }
    }

    private static final class Supply implements IItemSource {
        private final List<ItemStack> source = ItemList.items;
        private final List<ItemStack> recent = new ArrayList<>();
        @Override public Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, boolean simulate, int count) {
            if (count < 1) return Collections.emptyMap();
            if (!ItemList.loadFinished || ItemList.items != source) throw new Jobs.Fault("items_changed", "NEI item list changed during construction");
            for (ItemStack stack : recent) {
                ItemStack copy = stack.copy(); if (predicate.test(copy)) return Collections.singletonMap(copy, count);
            }
            for (ItemStack stack : source) {
                Jobs.checkpoint(); ItemStack copy = stack.copy();
                if (predicate.test(copy)) {
                    if (recent.size() == 64) recent.remove(0);
                    recent.add(copy.copy()); return Collections.singletonMap(copy, count);
                }
            }
            return Collections.emptyMap();
        }
        @Override public boolean takeOne(ItemStack stack, boolean simulate) { return stack != null && stack.getItem() != null; }
        @Override public boolean takeAll(ItemStack stack, boolean simulate) { return stack != null && stack.getItem() != null; }
    }

    /** All observable blocks and tiles belong to this object; no chunks or players from the live world are reused. */
    static final class Space extends DummyWorld implements AutoCloseable {
        final Map<Long, Block> blocks = new LinkedHashMap<>();
        private final Map<Long, Integer> metadata = new LinkedHashMap<>();
        private final Map<Long, TileEntity> tiles = new LinkedHashMap<>();
        long changes;
        private boolean closed;
        Space() { rand.setSeed(0); }
        private boolean inside(int x, int y, int z) { return x >= -32768 && x <= 32767 && z >= -32768 && z <= 32767 && y >= 0 && y < 256; }
        private void writable(int x, int y, int z) {
            Jobs.checkpoint();
            if (closed || !inside(x, y, z)) throw fault("Construction left its preview bounds");
        }
        @Override public Block getBlock(int x, int y, int z) { return inside(x, y, z) ? blocks.getOrDefault(key(x, y, z), Blocks.air) : Blocks.air; }
        @Override public int getBlockMetadata(int x, int y, int z) { return inside(x, y, z) ? metadata.getOrDefault(key(x, y, z), 0) : 0; }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return inside(x, y, z) ? tiles.get(key(x, y, z)) : null; }
        @Override public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
            writable(x, y, z);
            if (block == null || meta < 0 || meta > 15) throw fault("Invalid preview block state");
            long key = key(x, y, z);
            if (getBlock(x, y, z) == block && getBlockMetadata(x, y, z) == meta) return false;
            removeTileEntity(x, y, z);
            if (block == Blocks.air) { blocks.remove(key); metadata.remove(key); }
            else {
                if (!blocks.containsKey(key) && blocks.size() >= 1_048_576) throw fault("Construction exceeds one million blocks");
                blocks.put(key, block); metadata.put(key, meta);
                if (block.hasTileEntity(meta)) { TileEntity tile = block.createTileEntity(this, meta); if (tile != null) setTileEntity(x, y, z, tile); }
                block.onBlockAdded(this, x, y, z);
            }
            changes++; return true;
        }
        @Override public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta, int flags) {
            writable(x, y, z); long key = key(x, y, z);
            if (meta < 0 || meta > 15) throw fault("Invalid preview block metadata");
            if (!blocks.containsKey(key) || getBlockMetadata(x, y, z) == meta) return false;
            metadata.put(key, meta); TileEntity tile = tiles.get(key);
            if (tile != null) { tile.updateContainingBlockInfo(); tile.blockMetadata = meta; }
            changes++; return true;
        }
        @Override public void setTileEntity(int x, int y, int z, TileEntity tile) {
            writable(x, y, z);
            if (tile == null || !blocks.containsKey(key(x, y, z))) throw fault("Preview tile has no block");
            TileEntity previous = tiles.put(key(x, y, z), tile);
            if (previous != null && previous != tile) previous.invalidate();
            tile.setWorldObj(this); tile.xCoord = x; tile.yCoord = y; tile.zCoord = z; tile.validate(); changes++;
        }
        @Override public void removeTileEntity(int x, int y, int z) {
            if (!inside(x, y, z)) return;
            TileEntity tile = tiles.remove(key(x, y, z)); if (tile != null) { tile.invalidate(); changes++; }
        }
        @Override public boolean spawnEntityInWorld(Entity entity) { return false; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            RuntimeException failure = null;
            try {
                for (TileEntity tile : new ArrayList<>(tiles.values())) {
                    try { tile.invalidate(); }
                    catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
                }
            }
            finally { tiles.clear(); blocks.clear(); metadata.clear(); }
            if (failure != null) throw failure;
        }
    }
}
