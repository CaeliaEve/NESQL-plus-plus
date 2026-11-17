package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

final class BotaniaLexiconTest {
    static void run() throws Exception {
        Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BotaniaLexiconReferences");
        Class<?> entry = Class.forName("vazkii.botania.api.lexicon.LexiconEntry");
        Class<?> category = Class.forName("vazkii.botania.api.lexicon.LexiconCategory");
        Object parent = category.getConstructor(String.class).newInstance("botania.category.fixture");
        cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
        Field controller = loader.getClass().getDeclaredField("modController"); controller.setAccessible(true);
        Object previousController = controller.get(loader);
        cpw.mods.fml.common.LoadController owned = new cpw.mods.fml.common.LoadController(loader);
        Field active = owned.getClass().getDeclaredField("activeContainer"); active.setAccessible(true);
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata(); metadata.modId = "Botania";
        active.set(owned, new cpw.mods.fml.common.DummyModContainer(metadata));
        Object nativeEntry;
        try {
            controller.set(loader, owned);
            nativeEntry = entry.getConstructor(String.class, category).newInstance("botania.entry.fixture", parent);
        } finally { controller.set(loader, previousController); }
        ItemStack stack = new ItemStack(Items.book, 5);
        entry.getMethod("addExtraDisplayedRecipe", ItemStack.class).invoke(nativeEntry, stack);
        Method snapshot = adapter.getDeclaredMethod("snapshot", List.class, JsonObject.class, Function.class, Map.class);
        snapshot.setAccessible(true);
        JsonObject source = object("owner", "Botania", "handler",
                "vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania", "key", "botania.lexica");
        Function<String, String> translate = key -> key.startsWith("botania.nei.quickInfo:") ? "&aNative line<br>Second line" : "Native " + key;
        Map<String, ItemStack> specimens = new TreeMap<>();
        JsonArray rows = (JsonArray) snapshot.invoke(null, Collections.singletonList(nativeEntry), source, translate, specimens);
        JsonObject data = rows.get(1).getAsJsonObject().getAsJsonObject("data");
        if (rows.size() != 2 || !data.getAsJsonObject("knowledge").get("autoUnlock").getAsBoolean()
                || !data.get("tagline").isJsonNull() || data.getAsJsonArray("items").size() != 1
                || !data.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("quickInfo").get("value").getAsString().equals("\u00a7aNative line\nSecond line")
                || stack.stackSize != 5) throw new AssertionError("Native lexicon information was flattened or consumed");
        JsonArray fallback = (JsonArray) snapshot.invoke(null, Collections.singletonList(nativeEntry), source,
                (Function<String, String>) key -> key, new HashMap<>());
        JsonObject item = fallback.get(1).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("items").get(0).getAsJsonObject();
        if (item.get("hasQuickInfo").getAsBoolean() || !item.getAsJsonObject("quickInfo").get("key").getAsString().equals("botania.nei.lexicaNoInfo"))
            throw new AssertionError("Missing native quick info was fabricated");
        entry.getMethod("addExtraDisplayedRecipe", ItemStack.class).invoke(nativeEntry, new ItemStack(Items.apple));
        JsonArray drift = (JsonArray) snapshot.invoke(null, Collections.singletonList(nativeEntry), source, translate, new HashMap<>());
        if (rows.equals(drift)) throw new AssertionError("Lexicon item drift invisible");
        JsonArray items = array();
        for (Map.Entry<String, ItemStack> fact : specimens.entrySet()) {
            ItemStack specimen = fact.getValue();
            items.add(object("id", fact.getKey(), "registry", Item.itemRegistry.getNameForObject(specimen.getItem()),
                    "meta", specimen.getItemDamage(), "nbt", JsonNull.INSTANCE, "tags", array()));
        }
        java.nio.file.Path target = java.nio.file.Paths.get("build/native-tests/botania-lexicon-contract.json");
        java.nio.file.Files.write(target, CanonicalJson.bytes(object("records", rows, "items", items)));
        Item metadataItem=new Item(){@Override public int getDamage(ItemStack offered){return 731;}};
        Item.itemRegistry.addObject(31997,"fixture:lexiconRawMetadata",metadataItem);
        ItemStack physical=new ItemStack(metadataItem,3,17);
        entry.getMethod("addExtraDisplayedRecipe",ItemStack.class).invoke(nativeEntry,physical);
        Map<String,ItemStack> physicalSpecimens=new HashMap<>();
        snapshot.invoke(null,Collections.singletonList(nativeEntry),source,translate,physicalSpecimens);
        String actual=physicalSpecimens.entrySet().stream().filter(value->value.getValue().getItem()==metadataItem).findFirst().get().getKey();
        String expected=com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:fixture:lexiconRawMetadata",17,JsonNull.INSTANCE);
        if(!actual.equals(expected)||Items.feather.getDamage(physical)!=17||physical.stackSize!=3)throw new AssertionError("Lexicon content identity differs from raw Facts identity: actual="+actual+", expected="+expected+", registry="+Item.itemRegistry.getNameForObject(metadataItem)+", rawMeta="+Items.feather.getDamage(physical)+", quantity="+physical.stackSize);
        System.out.println("Botania lexicon: native entries, knowledge, absent tagline, formatted quick info, fallback and drift passed");
    }
}
