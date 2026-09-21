package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.util.recipe.Sievert;
import gregtech.api.util.recipe.QuantumComputerRecipeData;
import gtnhintergalactic.recipe.SpaceMiningData;
import gtnhlanth.common.tileentity.recipe.beamline.SourceChamberMetadata;
import gtnhlanth.common.tileentity.recipe.beamline.TargetChamberMetadata;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Set;

/** Exercises the pinned metadata classes that were rejected in the real handler sweep. */
final class ValuesTest {
    private ValuesTest() {}

    static void run() {
        Facts facts = new Facts("en_US");
        JsonObject radiation = fields(new Sievert(123, true), facts);
        expect(radiation, "sievert", "integer", "123");
        expect(radiation, "isExact", "flag", "true");
        require(radiation.entrySet().size() == 2, "Radiation metadata lost fields");
        JsonObject computer = fields(new QuantumComputerRecipeData(0.25f, -0.5f, 12, 1000, true), facts);
        expect(computer, "heatConstant", "decimal", "0.25");
        expect(computer, "coolConstant", "decimal", "-0.5");
        expect(computer, "computation", "decimal", "12");
        expect(computer, "maxHeat", "decimal", "1000");
        expect(computer, "subZero", "flag", "true");
        JsonObject mining = fields(new SpaceMiningData("asteroid", 2, 20, 3, 12, 128, 40), facts);
        require(mining.entrySet().size() == 7 && mining.getAsJsonObject("asteroidName").get("kind").getAsString().equals("text"),
                "Mining metadata lost its name or range fields");
        expect(mining, "minDistance", "integer", "2");
        expect(mining, "maxDistance", "integer", "20");
        expect(mining, "minSize", "integer", "3");
        expect(mining, "maxSize", "integer", "12");
        expect(mining, "computation", "integer", "128");
        expect(mining, "recipeWeight", "integer", "40");

        // Seed a catalogued focus item so this metadata test has no Minecraft client dependency.
        ItemStack focus = new ItemStack(Items.paper, 0, 0);
        NBTTagCompound tags = new NBTTagCompound(); tags.setLong("identity", Long.MAX_VALUE); focus.setTagCompound(tags);
        String item = Identity.item("minecraft:paper", 0, TypedNbt.encode(tags));
        Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items"); known.add(item);
        TargetChamberMetadata target = TargetChamberMetadata.builder(focus).amount(0).energy(1, 3, 0.5f).minFocus(2).build();
        JsonObject chamber = fields(target, facts);
        require(chamber.entrySet().size() == 7, "Target chamber lost fields");
        expect(chamber, "particleID", "integer", "0");
        expect(chamber, "amount", "integer", "0");
        expect(chamber, "minEnergy", "decimal", "1");
        expect(chamber, "maxEnergy", "decimal", "3");
        expect(chamber, "minFocus", "decimal", "2");
        expect(chamber, "energyRatio", "decimal", "0.5");
        JsonObject focusValue = chamber.getAsJsonObject("focusItem").getAsJsonObject("values");
        require(focusValue.getAsJsonObject("stack").getAsJsonObject("target").get("id").getAsString().equals(item)
                && focusValue.getAsJsonObject("amount").get("amount").getAsString().equals("0")
                && focus.stackSize == 0 && tags.getLong("identity") == Long.MAX_VALUE, "Focus identity or catalyst quantity changed");
        JsonObject source = fields(SourceChamberMetadata.builder().rate(5).energy(6, 0.25f).focus(7).build(), facts);
        require(source.entrySet().size() == 5, "Source chamber lost fields");
        expect(source, "particleID", "integer", "0");
        expect(source, "rate", "integer", "5");
        expect(source, "maxEnergy", "decimal", "6");
        expect(source, "focus", "decimal", "7");
        expect(source, "energyRatio", "decimal", "0.25");
        try { Values.capture(new Object(), facts); throw new AssertionError("Unknown metadata was stringified"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("metadata_type"), "Wrong unsupported metadata error"); }
        try { Values.capture(new QuantumComputerRecipeData(Float.NaN, 0, 0, 0, false), facts); throw new AssertionError("Accepted non-finite metadata"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("metadata_number"), "Typed metadata bypassed numeric validation"); }
        System.out.println("Recipe metadata: radiation, mining, beamline and quantum fields preserve types, identities and quantities");
    }

    private static JsonObject fields(Object input, Facts facts) { return Values.capture(input, facts).getAsJsonObject("values"); }
    private static void expect(JsonObject fields, String name, String kind, String value) {
        JsonObject field = fields.getAsJsonObject(name);
        require(field.get("kind").getAsString().equals(kind) && field.get("value").getAsString().equals(value), "Incorrect metadata field: " + name);
        if (!kind.equals("flag")) require(field.get("value").getAsJsonPrimitive().isString(), "Exact metadata was not a string: " + name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
