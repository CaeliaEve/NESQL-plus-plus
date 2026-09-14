package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Advisory placement hints must not be confused with mandatory inventory facts. */
final class StructuresTest {
    private StructuresTest() {}

    static void run() {
        net.minecraft.block.Block fluid = new net.minecraft.block.Block(net.minecraft.block.material.Material.water) {};
        net.minecraft.block.Block.blockRegistry.addObject(4094, "nesql:placement_fluid", fluid);
        require(Item.getItemFromBlock(fluid) == null, "The fixture block unexpectedly has an item registration");
        IStructureElement<Object> water = StructureUtility.ofBlock(fluid, 0);
        BlocksToPlace wet = water.getBlocksToPlace(null, null, 0, 64, 0, null, null);
        ItemStack missingItem = wet.getStacks().iterator().next();
        require(missingItem != null && missingItem.getItem() == null,
                "The native no-item block factory no longer reproduces an empty Item fact");
        try { new Facts("en_US").item(missingItem); throw new AssertionError("Expected the original empty item failure"); }
        catch (IllegalArgumentException expected) { require(expected.getMessage().equals("Empty item fact"), "Wrong reproduction"); }
        Function<ItemStack, String> unused = stack -> { throw new AssertionError("A missing placement item reached fact capture"); };
        require(Structures.placements(wet, unused).size() == 0, "A non-item water block acquired a fake item");
        require(missingItem.getItem() == null && missingItem.stackSize == 1, "The provider's stack was changed");
        require(Structures.placements(null, unused) == null, "Absent provider became an empty list");
        require(Structures.placements(BlocksToPlace.create(stack -> true), unused) == null, "Predicate-only suggestions became enumerated");
        require(Structures.placements(BlocksToPlace.errored, unused) == null, "An errored provider was advertised as enumerated");
        require(Structures.placements(BlocksToPlace.createEmpty(), unused).size() == 0, "Explicit empty suggestions changed");

        IStructureElement<Object> stone = StructureUtility.ofBlock(Blocks.stone, 0);
        IStructureElement<Object> chain = StructureUtility.ofChain(water, stone, stone);
        JsonArray mixed = Structures.placements(chain.getBlocksToPlace(null, null, 0, 64, 0, null, null), StructuresTest::id);
        require(mixed.size() == 1 && mixed.get(0).getAsString().equals(id(new ItemStack(Blocks.stone))),
                "Native chained hints lost the real placement or kept duplicates");

        ItemStack plain = new ItemStack(Blocks.stone, 4, 0), tagged = plain.copy();
        NBTTagCompound tag = new NBTTagCompound(); tag.setLong("value", Long.MAX_VALUE); tagged.setTagCompound(tag);
        List<ItemStack> suggestions = new ArrayList<>(Arrays.asList(tagged, plain, tagged));
        BlocksToPlace supplied = BlocksToPlace.create(suggestions);
        suggestions.add(null); suggestions.add(missingItem);
        JsonArray identities = Structures.placements(supplied, stack -> {
            String id = id(stack);
            stack.stackSize = 99;
            if (stack.hasTagCompound()) stack.getTagCompound().setLong("value", 1);
            return id;
        });
        require(identities.size() == 2 && identities.get(0).getAsString().compareTo(identities.get(1).getAsString()) < 0,
                "Placement identities are not sorted and unique");
        require(plain.stackSize == 4 && tagged.stackSize == 4 && tag.getLong("value") == Long.MAX_VALUE,
                "Fact capture mutated advisory NBT or quantity");

        try {
            Structures.placements(BlocksToPlace.create(Collections.nCopies(1025, missingItem)), unused);
            throw new AssertionError("Missing items bypassed the advisory scan budget");
        } catch (Jobs.Fault expected) { require(expected.code.equals("structure_limit"), "Wrong placement limit"); }
        Jobs.Fault original = new Jobs.Fault("unregistered_item", "fixture invalid reference");
        try {
            Structures.placements(BlocksToPlace.create(plain), stack -> { throw original; });
            throw new AssertionError("An actual item failure was hidden");
        } catch (Jobs.Fault expected) {
            Jobs.Fault outer = Structures.failure("Structure index=16; controller=1000; piece='main'; symbol='A'", expected);
            require(outer.code.equals("unregistered_item") && expected.getCause() == original && outer.getCause() == expected
                    && outer.getMessage().contains("Placement suggestion index=0") && outer.getMessage().contains("controller=1000"),
                    "Placement diagnostics lost the error code, cause or location");
        }
        java.util.concurrent.CancellationException cancelled = new java.util.concurrent.CancellationException("fixture");
        try {
            Structures.placements(BlocksToPlace.create(plain), stack -> { throw cancelled; });
            throw new AssertionError("Placement ignored cancellation");
        } catch (java.util.concurrent.CancellationException expected) { require(expected == cancelled, "Cancellation was reclassified"); }
        System.out.println("Structure placements: native block factory without an Item, chained hints, missing/empty providers, NBT copies, budgets and diagnostics passed");
    }

    private static String id(ItemStack stack) {
        return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound()));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
