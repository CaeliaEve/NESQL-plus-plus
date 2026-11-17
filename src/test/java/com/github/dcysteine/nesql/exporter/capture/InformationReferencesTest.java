package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.InformationHandler;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/** Native information registrations, isolated language data and no recipe GUI. */
final class InformationReferencesTest {
    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        List<Object> registry = (List<Object>) field(InformationHandler.class, null, "ITEM_INFO");
        List<Object> saved = new ArrayList<>(registry);
        boolean ready = ItemList.loadFinished;
        List<ItemStack> panel = ItemList.items;
        Map<String, String> language =
                (Map<String, String>)
                        field(field(StatCollector.class, null, "localizedName"), "languageList");
        Map<String, String> savedLanguage = new HashMap<>(language);
        JsonArray domains = array(), items = array(), empty = array();
        try {
            registry.clear();
            InformationHandler.addInformationPage("minecraft:paper", "nesql.fixture.documentation");
            Object page = registry.get(0);
            List<ItemStack> links = (List<ItemStack>) field(page, "items");
            links.add(new ItemStack(Items.paper, 3));
            links.add(new ItemStack(Items.paper, 5));
            InformationHandler.addInformationPage(
                    "minecraft:iron_ingot", "Literal\\nsecond line\n");
            ItemList.items = Arrays.asList(new ItemStack(Items.paper));
            ItemList.loadFinished = true;
            Recipes.Handler registration=Recipes.handlers(Collections.singletonList(new InformationHandler()),Collections.emptyList(),
                    Collections.singletonList(new InformationHandler()),Collections.emptyList()).get(0);
            require(registration.describe().get("registrationEquivalence").getAsString().equals("shared-native-bindings"),
                    "Native documentation registrations did not bind their shared source");
            List<ItemStack> registeredPanel=ItemList.items;
            ItemList.items=new ArrayList<>(registeredPanel);
            try{registration.describe();throw new AssertionError("Equal-content replacement of a documentation source was accepted");}
            catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong documentation source drift rejection");}
            ItemList.items=registeredPanel;
            language.put(
                    "nesql.fixture.documentation",
                    "Native translated paragraph\\nsecond paragraph");
            JsonObject source =
                    object(
                            "owner",
                            "NEI",
                            "handler",
                            InformationHandler.class.getName(),
                            "key",
                            "information");
            try (NativeInformationReferences reader =
                    new NativeInformationReferences(new InformationHandler(), source)) {
                require(
                        reader.size() == 3,
                        "Information definition or unmatched native page disappeared");
                NativeInformationReferences.Snapshot snapshot =
                        (NativeInformationReferences.Snapshot) field(reader, "snapshot");
                JsonArray rows = snapshot.records;
                domains.add(rows);
                for (Map.Entry<String, ItemStack> specimen : snapshot.specimens.entrySet()) {
                    ItemStack stack = specimen.getValue();
                    items.add(
                            object(
                                    "id",
                                    specimen.getKey(),
                                    "registry",
                                    Item.itemRegistry.getNameForObject(stack.getItem()),
                                    "meta",
                                    Items.feather.getDamage(stack),
                                    "nbt",
                                    TypedNbt.encode(stack.getTagCompound())));
                }
                JsonObject linked = rows.get(1).getAsJsonObject().getAsJsonObject("data");
                require(
                        linked.getAsJsonArray("items")
                                .get(0)
                                .getAsJsonObject()
                                .get("amount")
                                .getAsString()
                                .equals("3"),
                        "Documentation link quantity was normalized");
                require(
                        linked.getAsJsonArray("items")
                                .get(1)
                                .getAsJsonObject()
                                .get("amount")
                                .getAsString()
                                .equals("5"),
                        "Duplicate documentation link was deduped");
                require(
                        linked.getAsJsonArray("lines")
                                .get(0)
                                .getAsString()
                                .equals("Native translated paragraph"),
                        "Native translation key was exported as body");
                language.put("nesql.fixture.documentation", "changed translation");
                fails("environment_changed", reader::finishVerification);
                language.put(
                        "nesql.fixture.documentation",
                        "Native translated paragraph\\nsecond paragraph");
                JsonObject unmatched = rows.get(2).getAsJsonObject().getAsJsonObject("data");
                require(
                        !unmatched.get("listed").getAsBoolean()
                                && unmatched.getAsJsonArray("lines").size() == 3,
                        "Unmatched page or literal/actual newline disappeared");
                reader.finishVerification();
                links.get(0).stackSize = 4;
                fails("environment_changed", reader::finishVerification);
                links.get(0).stackSize = 3;
                reader.finishVerification();
                links.add(null);
                fails(
                        "reference_invalid",
                        () -> new NativeInformationReferences(new InformationHandler(), source));
                links.remove(links.size() - 1);
            }
            registry.clear();
            try (NativeInformationReferences reader =
                    new NativeInformationReferences(new InformationHandler(), source)) {
                require(
                        reader.size() == 1 && reader.counts().get("pages").getAsInt() == 0,
                        "Ready empty information registry has no proof");
                empty = ((NativeInformationReferences.Snapshot) field(reader, "snapshot")).records;
                reader.close();
                fails("capture_closed", reader::verify);
            }
            ItemList.loadFinished = false;
            fails(
                    "handler_not_ready",
                    () -> new NativeInformationReferences(new InformationHandler(), source));
            ItemList.loadFinished = true;
            extraUtilities(language, domains, items);
            Files.createDirectories(Paths.get("build/native-tests"));
            Files.write(
                    Paths.get("build/native-tests/nei-information-contract.json"),
                    object("domains", domains, "items", items, "empty", empty)
                            .toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println(
                    "Information references: unmatched pages, quantities, native readiness and drift");
        } finally {
            registry.clear();
            registry.addAll(saved);
            ItemList.items = panel;
            ItemList.loadFinished = ready;
            language.clear();
            language.putAll(savedLanguage);
        }
    }

    private static void extraUtilities(
            Map<String, String> language, JsonArray domains, JsonArray items) throws Exception {
        Class<?> nativeType = Class.forName(NativeInformationReferences.EXTRA_UTILS_HANDLER);
        require(
                nativeType
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toString()
                        .contains("/native-tests/extrautilities.jar!"),
                "Installed Extra Utilities binary required");
        codechicken.nei.recipe.ICraftingHandler handler =
                (codechicken.nei.recipe.ICraftingHandler) nativeType.newInstance();
        Item custom =
                new Item() {
                    public String getUnlocalizedName(ItemStack stack) {
                        return "item.nesql_fixture_info.state" + Items.feather.getDamage(stack);
                    }

                    public int getDamage(ItemStack stack) {
                        return 91;
                    }
                }.setUnlocalizedName("nesql_fixture_info");
        Item.itemRegistry.addObject(12201, "nesql_fixture_info", custom);
        language.put(
                "item.nesql_fixture_info.documentation", "Generic native documentation\nlast line");
        language.put("item.nesql_fixture_info.state2.documentation.0", "Specific first paragraph");
        language.put("item.nesql_fixture_info.state2.documentation.1", "Specific second paragraph");
        ItemStack generic = new ItemStack(custom, 9, 1), specific = new ItemStack(custom, 7, 2);
        ItemList.items = Arrays.asList(generic, specific, new ItemStack(Items.iron_ingot));
        JsonObject source =
                object(
                        "owner",
                        "ExtraUtilities",
                        "handler",
                        nativeType.getName(),
                        "key",
                        "extraUtilities.documentation");
        try (NativeInformationReferences reader =
                new NativeInformationReferences(handler, source)) {
            NativeInformationReferences.Snapshot snapshot =
                    (NativeInformationReferences.Snapshot) field(reader, "snapshot");
            require(
                    reader.size() == 3,
                    "Native predicate accepted missing documentation or lost a valid subtype");
            JsonObject first = snapshot.records.get(1).getAsJsonObject().getAsJsonObject("data");
            JsonObject second = snapshot.records.get(2).getAsJsonObject().getAsJsonObject("data");
            require(
                    !first.get("precise").getAsBoolean() && second.get("precise").getAsBoolean(),
                    "Installed specific/generic selection policy changed");
            require(
                    second.getAsJsonArray("textSources").size() == 2
                            && second.getAsJsonArray("lines")
                                    .get(1)
                                    .getAsString()
                                    .equals("Specific second paragraph"),
                    "Sequential documentation paragraph disappeared");
            require(
                    first.getAsJsonArray("items")
                                    .get(0)
                                    .getAsJsonObject()
                                    .get("amount")
                                    .getAsString()
                                    .equals("1")
                            && generic.stackSize == 9
                            && specific.stackSize == 7,
                    "Native display quantity mutated the source");
            domains.add(snapshot.records);
            for (Map.Entry<String, ItemStack> entry : snapshot.specimens.entrySet()) {
                ItemStack stack = entry.getValue();
                items.add(
                        object(
                                "id",
                                entry.getKey(),
                                "registry",
                                Item.itemRegistry.getNameForObject(stack.getItem()),
                                "meta",
                                Items.feather.getDamage(stack),
                                "nbt",
                                TypedNbt.encode(stack.getTagCompound())));
            }
            reader.finishVerification();
            language.remove("item.nesql_fixture_info.state2.documentation.1");
            fails("environment_changed", reader::finishVerification);
        }
    }
    /**
     * Isolate only the unused font-renderer class initializer; all native predicate code remains.
     */
    public static final class NoInfoGuiInitialization
            implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name, String transformed, byte[] bytes) {
            if (!NativeInformationReferences.EXTRA_UTILS_HANDLER.equals(transformed)) return bytes;
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
            for (org.objectweb.asm.tree.MethodNode method : node.methods)
                if (method.name.equals("<clinit>")) {
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    if (method.localVariables != null) method.localVariables.clear();
                    method.instructions.add(
                            new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
                    method.maxStack = 0;
                    method.maxLocals = 0;
                }
            org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
    }

    private static void fails(String code, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + code);
        } catch (Jobs.Fault error) {
            if (!error.code.equals(code)) throw error;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
