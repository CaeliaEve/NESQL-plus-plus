package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

final class NativeImbuingSerializationTest {
    private NativeImbuingSerializationTest() {}

    static void run() throws Exception {
        OreDictionary.registerOre("nesqlImbuingSerialization", new ItemStack(Items.iron_ingot));
        Class<?> filterType = Class.forName("com.github.dcysteine.nesql.exporter.capture.ImbuingRecipes$Filter");
        Constructor<?> constructor = filterType.getDeclaredConstructor(Object.class);
        constructor.setAccessible(true);
        Object filter = constructor.newInstance(new ItemStack(Items.iron_ingot));
        Method definition = filterType.getDeclaredMethod("definition");
        definition.setAccessible(true);
        JsonObject json = (JsonObject) definition.invoke(filter);
        JsonArray ores = json.getAsJsonArray("ores");
        boolean found = false;
        if (ores != null) for (int index = 0; index < ores.size(); index++)
            found |= ores.get(index).getAsString().equals("nesqlImbuingSerialization");
        if (!found)
            throw new AssertionError("Imbuing ore memberships were not serialized as JSON strings");
    }
}
