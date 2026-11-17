package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Existing native test runner; real installed Diagram APIs, no font/drawing/GUI callbacks. */
final class NativeDiagramTest {
    private static final String API = "com.github.dcysteine.neicustomdiagram.api.";

    static void run() throws Exception {
        boolean savedReady = codechicken.nei.ItemList.loadFinished;
        List<ItemStack> savedPanel = codechicken.nei.ItemList.items;
        ItemStack queryPaper = new ItemStack(Items.paper, 3);
        net.minecraft.nbt.NBTTagCompound queryTag = new net.minecraft.nbt.NBTTagCompound();
        queryTag.setString("native-query", "preserve original");
        queryPaper.setTagCompound(queryTag);
        codechicken.nei.ItemList.items =
                Arrays.asList(queryPaper, new ItemStack(Items.iron_pickaxe, 1, 23));
        codechicken.nei.ItemList.loadFinished = true;
        java.lang.reflect.Field gameRoot =
                cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        gameRoot.setAccessible(true);
        Object priorRoot = gameRoot.get(null);
        gameRoot.set(null, new java.io.File("build/native-tests/diagram-config"));
        Class<?> configType = type("com.github.dcysteine.neicustomdiagram.main.config.Config");
        java.lang.reflect.Field configField = configType.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        Object savedConfig = configField.get(null);
        Object option =
                field(
                        type("com.github.dcysteine.neicustomdiagram.main.config.ConfigOptions"),
                        null,
                        "SHOW_EMPTY_DIAGRAMS");
        java.lang.reflect.Field optionProperty =
                option.getClass().getSuperclass().getDeclaredField("property");
        optionProperty.setAccessible(true);
        Object savedProperty = optionProperty.get(option);
        net.minecraftforge.common.config.Configuration ownedConfig =
                new net.minecraftforge.common.config.Configuration();
        replaceStatic(configField, ownedConfig);
        optionProperty.set(
                option,
                new net.minecraftforge.common.config.Property(
                        "show_empty_diagrams",
                        "false",
                        net.minecraftforge.common.config.Property.Type.BOOLEAN));
        try {
            runNative();
            if (System.getProperty("nesql.nativeFamily").equals("nei-diagram-generators"))
                NativeGtDiagramGeneratorsTest.run();
        } finally {
            replaceStatic(configField, savedConfig);
            optionProperty.set(option, savedProperty);
            gameRoot.set(null, priorRoot);
            codechicken.nei.ItemList.items = savedPanel;
            codechicken.nei.ItemList.loadFinished = savedReady;
        }
    }

    private static void runNative() throws Exception {
        net.minecraft.nbt.NBTTagCompound trapped = new net.minecraft.nbt.NBTTagCompound();
        trapped.setString("kind", "text");
        trapped.setString("text", "not a native note");
        trapped.setString("fact", "not a native reference");
        ItemStack trappedStack = new ItemStack(Items.paper);
        trappedStack.setTagCompound(trapped);
        JsonObject trappedComponent = new NativeDiagramContents().stack(trappedStack, false);
        List<String> dependencyIds = new ArrayList<>(), nativeTexts = new ArrayList<>();
        NativeComponentFacts.visit(
                object(
                        "component",
                        trappedComponent,
                        "raw",
                        com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(trapped),
                        "note",
                        object("kind", "text", "text", "actual native note")),
                dependencyIds::add,
                nativeTexts::add);
        require(
                dependencyIds.equals(
                                Collections.singletonList(
                                        trappedComponent.get("fact").getAsString()))
                        && nativeTexts.equals(Collections.singletonList("actual native note")),
                "Raw NBT collided with component/text publication");
        Object point =
                staticCall("draw.Point", "create", new Class<?>[] {int.class, int.class}, 9, 9);
        Object slot =
                call(
                        staticCall(
                                "diagram.layout.Slot",
                                "builder",
                                new Class<?>[] {type(API + "draw.Point")},
                                point),
                        "build");
        Object key =
                staticCall(
                        "diagram.layout.Layout$SlotKey",
                        "create",
                        new Class<?>[] {String.class},
                        "recipe-output");
        Object groupKey =
                staticCall(
                        "diagram.layout.Layout$SlotGroupKey",
                        "create",
                        new Class<?>[] {String.class},
                        "recipe-item-inputs-0");
        Object direction = Enum.valueOf((Class) type(API + "diagram.layout.Grid$Direction"), "SE");
        Object group =
                call(
                        staticCall(
                                "diagram.layout.SlotGroup",
                                "builder",
                                new Class<?>[] {
                                    int.class,
                                    int.class,
                                    type(API + "draw.Point"),
                                    type(API + "diagram.layout.Grid$Direction")
                                },
                                2,
                                1,
                                point,
                                direction),
                        "build");
        Object layoutBuilder = staticCall("diagram.layout.Layout", "builder", new Class<?>[0]);
        invoke(
                layoutBuilder.getClass(),
                layoutBuilder,
                "putSlot",
                new Class<?>[] {
                    type(API + "diagram.layout.Layout$SlotKey"), type(API + "diagram.layout.Slot")
                },
                key,
                slot);
        invoke(
                layoutBuilder.getClass(),
                layoutBuilder,
                "putSlotGroup",
                new Class<?>[] {
                    type(API + "diagram.layout.Layout$SlotGroupKey"),
                    type(API + "diagram.layout.SlotGroup")
                },
                groupKey,
                group);
        Object layout = call(layoutBuilder, "build");
        groupedQueries(layout);
        Object paper = component(new ItemStack(Items.paper, 7));
        Object iron = component(new ItemStack(Items.iron_ingot, 3));
        Object wildcard = component(new ItemStack(Items.paper, 1, OreDictionary.WILDCARD_VALUE));
        Object display = display(paper, 7), alternative = display(iron, null);
        NativeDiagramContents numeric = new NativeDiagramContents();
        require(
                numeric.component(component(new ItemStack(Items.paper, 1, 65535)))
                                .get("meta")
                                .getAsInt()
                        == 65535,
                "Native int metadata was truncated to a wildcard/short");
        require(
                numeric.component(component(new ItemStack(Items.paper, 1, -2)))
                                .get("meta")
                                .getAsInt()
                        == 0,
                "Adapter differs from the actual native ItemStack constructor's negative-value policy");
        Object mismatchedKey =
                staticCall(
                        "diagram.component.ItemComponent",
                        "create",
                        new Class<?>[] {net.minecraft.item.Item.class, int.class},
                        Items.paper,
                        -2);
        fails(() -> numeric.component(mismatchedKey));
        Object fluid =
                staticCall(
                        "diagram.component.FluidComponent",
                        "createWithNbt",
                        new Class<?>[] {net.minecraftforge.fluids.FluidStack.class},
                        new net.minecraftforge.fluids.FluidStack(
                                net.minecraftforge.fluids.FluidRegistry.WATER, 1250));
        Object builder = staticCall("diagram.Diagram", "builder", new Class<?>[0]);
        invoke(
                builder.getClass(),
                builder,
                "addLayout",
                new Class<?>[] {type(API + "diagram.layout.Layout")},
                layout);
        invoke(
                builder.getClass(),
                builder,
                "insertIntoSlot",
                new Class<?>[] {type(API + "diagram.layout.Layout$SlotKey"), Iterable.class},
                key,
                Arrays.asList(
                        display,
                        alternative,
                        display(fluid, 1250),
                        display(component(new ItemStack(Items.paper, 1, 65535)), null)));
        Object subgroup =
                invoke(
                        builder.getClass(),
                        builder,
                        "manualInsertIntoSlotGroup",
                        new Class<?>[] {type(API + "diagram.layout.Layout$SlotGroupKey")},
                        groupKey);
        invoke(
                subgroup.getClass(),
                subgroup,
                "insertIntoSlot",
                new Class<?>[] {int.class, int.class, Iterable.class},
                1,
                0,
                Collections.singletonList(display(wildcard, null)));
        Object label =
                staticCall(
                        "diagram.layout.ComponentLabel",
                        "create",
                        new Class<?>[] {
                            type(API + "diagram.component.Component"), type(API + "draw.Point")
                        },
                        iron,
                        point);
        Object tooltipBuilder = staticCall("diagram.tooltip.Tooltip", "builder", new Class<?>[0]);
        invoke(
                tooltipBuilder.getClass(),
                tooltipBuilder,
                "addTextLine",
                new Class<?>[] {String.class},
                "Cleanroom required\nNative note");
        Object tooltip = call(tooltipBuilder, "build");
        Object buttonBuilder =
                staticCall(
                        "diagram.interactable.CustomInteractable",
                        "builder",
                        new Class<?>[] {type(API + "draw.BoundedDrawable")},
                        label);
        invoke(
                buttonBuilder.getClass(),
                buttonBuilder,
                "setTooltip",
                new Class<?>[] {type(API + "diagram.tooltip.Tooltip")},
                tooltip);
        invoke(
                buttonBuilder.getClass(),
                buttonBuilder,
                "setInteract",
                new Class<?>[] {String.class},
                "neicustomdiagram.diagramgroup.gregtech.circuits");
        Object button = call(buttonBuilder, "build");
        invoke(
                builder.getClass(),
                builder,
                "addInteractable",
                new Class<?>[] {type(API + "diagram.interactable.Interactable")},
                button);
        Object diagram = call(builder, "build");
        Object matcherBuilder =
                staticCall("diagram.matcher.ComponentDiagramMatcher", "builder", new Class<?>[0]);
        Object matchEntry =
                invoke(
                        matcherBuilder.getClass(),
                        matcherBuilder,
                        "addDiagram",
                        new Class<?>[] {type(API + "diagram.Diagram")},
                        diagram);
        invoke(
                matchEntry.getClass(),
                matchEntry,
                "addComponent",
                new Class<?>[] {type(API + "diagram.component.Component")},
                paper);
        invoke(
                matchEntry.getClass(),
                matchEntry,
                "addComponent",
                new Class<?>[] {type(API + "diagram.component.Component")},
                component(new ItemStack(Items.iron_pickaxe)));
        Object matcher = call(matcherBuilder, "build");
        Object infoBuilder =
                staticCall(
                        "diagram.DiagramGroupInfo",
                        "builder",
                        new Class<?>[] {
                            String.class,
                            String.class,
                            type(API + "diagram.component.ItemComponent"),
                            int.class
                        },
                        "Native fixture",
                        "neicustomdiagram.diagramgroup.gregtech.circuits",
                        paper,
                        1);
        Object info = call(infoBuilder, "build");
        NativeDiagramSelection defaultSelection = new NativeDiagramSelection(info);
        require(
                defaultSelection
                        .policy()
                        .getAsJsonObject("empty")
                        .get("kind")
                        .getAsString()
                        .equals("never"),
                "Native default predicate was not recorded");
        Object directDiagram =
                type(API + "diagram.Diagram")
                        .getConstructor(
                                type(API + "diagram.layout.Layout"),
                                com.google.common.collect.Multiset.class,
                                com.google.common.collect.ImmutableList.class)
                        .newInstance(
                                field(diagram, "layout"),
                                com.google.common.collect.ImmutableMultiset.of(),
                                field(diagram, "interactables"));
        require(
                NativeDiagramSelection.insertions(directDiagram) == 0
                        && new NativeDiagramContents()
                                .read(directDiagram)
                                .equals(new NativeDiagramContents().read(diagram)),
                "Useful direct-constructor contents were replaced by inferred insertions");
        Object threshold =
                invoke(
                        type("com.github.dcysteine.neicustomdiagram.util.DiagramUtil"),
                        null,
                        "buildEmptyDiagramPredicate",
                        new Class<?>[] {int.class},
                        3);
        invoke(
                infoBuilder.getClass(),
                infoBuilder,
                "setEmptyDiagramPredicate",
                new Class<?>[] {java.util.function.Predicate.class},
                threshold);
        info = call(infoBuilder, "build");
        net.minecraftforge.common.config.Configuration nativeConfig =
                (net.minecraftforge.common.config.Configuration)
                        field(
                                type("com.github.dcysteine.neicustomdiagram.main.config.Config"),
                                null,
                                "CONFIG");
        net.minecraftforge.common.config.Property visibility =
                nativeConfig.get(
                        "diagram_groups",
                        "neicustomdiagram.diagramgroup.gregtech.circuits",
                        "ALWAYS_HIDDEN");
        NativeDiagramSelection selection = new NativeDiagramSelection(info);
        require(
                selection.policy().get("visibility").getAsString().equals("ALWAYS_HIDDEN")
                        && NativeDiagramSelection.insertions(diagram) == 2,
                "Native insertion multiset was guessed from cells or alternatives");
        visibility.set("SHOW_ON_CTRL");
        try {
            selection.verify();
            throw new AssertionError("Native visibility drift was ignored");
        } catch (Jobs.Fault expected) {
            require(expected.code.equals("environment_changed"), expected.toString());
        }
        for (String raw :
                Arrays.asList("true", "false", "native-unknown", "HIDE_ON_SHIFT", "SHOW_ON_CTRL")) {
            visibility.set(raw);
            JsonObject policy = new NativeDiagramSelection(info).policy();
            require(
                    policy.get("configuredVisibility").getAsString().equals(raw)
                            && visibility.getString().equals(raw),
                    "Read-only selection migrated native config");
            String expected =
                    raw.equals("true") || raw.equals("native-unknown")
                            ? "ALWAYS_SHOWN"
                            : raw.equals("false") ? "DISABLED" : raw;
            require(
                    policy.get("visibility").getAsString().equals(expected),
                    "Native visibility normalization differs");
        }
        visibility.set("ALWAYS_HIDDEN");
        Object unknownInfo = call(info, "toBuilder");
        invoke(
                unknownInfo.getClass(),
                unknownInfo,
                "setEmptyDiagramPredicate",
                new Class<?>[] {java.util.function.Predicate.class},
                (java.util.function.Predicate<Object>)
                        ignored -> {
                            throw new AssertionError("Unknown predicate must not execute");
                        });
        fails(() -> new NativeDiagramSelection(call(unknownInfo, "build")));
        Object nativeHandler =
                type(API + "diagram.DiagramGroup")
                        .getConstructor(
                                type(API + "diagram.DiagramGroupInfo"),
                                type(API + "diagram.matcher.DiagramMatcher"))
                        .newInstance(info, matcher);
        Object sharedHandler = type(API + "diagram.DiagramGroup")
                .getConstructor(type(API + "diagram.DiagramGroupInfo"),type(API + "diagram.matcher.DiagramMatcher"))
                .newInstance(info,matcher);
        List<Recipes.Handler> registrations=Recipes.handlers(Collections.singletonList((codechicken.nei.recipe.ICraftingHandler)nativeHandler),
                        Collections.emptyList(),Collections.singletonList((codechicken.nei.recipe.IUsageHandler)sharedHandler),Collections.emptyList());
        require(registrations.size()==1 && registrations.get(0).describe().get("registrationEquivalence").getAsString().equals("shared-native-bindings"),
                "Shared native semantic bindings were not unified across registrations");
        Object otherMatcher=call(staticCall("diagram.matcher.ComponentDiagramMatcher","builder",new Class<?>[0]),"build");
        Object conflictingHandler=type(API + "diagram.DiagramGroup")
                .getConstructor(type(API + "diagram.DiagramGroupInfo"),type(API + "diagram.matcher.DiagramMatcher"))
                .newInstance(info,otherMatcher);
        try {
            Recipes.handlers(Collections.singletonList((codechicken.nei.recipe.ICraftingHandler)nativeHandler),Collections.emptyList(),
                    Collections.singletonList((codechicken.nei.recipe.IUsageHandler)conflictingHandler),Collections.emptyList());
            throw new AssertionError("Different native diagram registries silently shared an identity");
        }catch(Jobs.Fault expected){require(expected.code.equals("handler_conflict"),expected.toString());}
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue((Class)type(API+"diagram.DiagramGroup"),sharedHandler,otherMatcher,"matcher");
        try {registrations.get(0).open(new Facts("en_US"));throw new AssertionError("Secondary native registration drift was ignored");}
        catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),expected.toString());}
        finally {cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue((Class)type(API+"diagram.DiagramGroup"),sharedHandler,matcher,"matcher");}
        Object option =
                field(
                        type("com.github.dcysteine.neicustomdiagram.main.config.ConfigOptions"),
                        null,
                        "SHOW_EMPTY_DIAGRAMS");
        net.minecraftforge.common.config.Property emptyOption =
                (net.minecraftforge.common.config.Property) field(option, "property");
        Object recipeType =
                Enum.valueOf(
                        (Class) type(API + "diagram.interactable.Interactable$RecipeType"),
                        "CRAFTING");
        Class<?>[] loadTypes = {
            String.class, type(API + "diagram.interactable.Interactable$RecipeType"), Object[].class
        };
        Object nativeFiltered =
                invoke(
                        nativeHandler.getClass(),
                        nativeHandler,
                        "loadDiagrams",
                        loadTypes,
                        "neicustomdiagram.diagramgroup.gregtech.circuits",
                        recipeType,
                        new Object[0]);
        require(
                (Integer) call(nativeFiltered, "numRecipes") == 0,
                "Native threshold did not filter this specimen");
        emptyOption.set("true");
        Object nativeGroup =
                invoke(
                        nativeHandler.getClass(),
                        nativeHandler,
                        "loadDiagrams",
                        loadTypes,
                        "neicustomdiagram.diagramgroup.gregtech.circuits",
                        recipeType,
                        new Object[0]);
        Object nativeItem =
                invoke(
                        nativeHandler.getClass(),
                        nativeHandler,
                        "loadDiagrams",
                        loadTypes,
                        "item",
                        recipeType,
                        new Object[] {new ItemStack(Items.paper)});
        require(
                (Integer) call(nativeGroup, "numRecipes") == 1
                        && (Integer) call(nativeItem, "numRecipes") == 0,
                "Native group action must bypass visibility; item lookup must retain it");
        NativeDiagramSelection beforeEmptyChange = new NativeDiagramSelection(info);
        emptyOption.set("false");
        try {
            beforeEmptyChange.verify();
            throw new AssertionError("Empty preference drift was ignored");
        } catch (Jobs.Fault expected) {
            require(expected.code.equals("environment_changed"), expected.toString());
        }
        JsonObject source =
                object(
                        "owner",
                        "neicustomdiagram",
                        "handler",
                        nativeHandler.getClass().getName(),
                        "key",
                        "neicustomdiagram.diagramgroup.gregtech.circuits");
        JsonArray transport, specimens = array(), fluidSpecimens = array();
        try (NativeGtDiagramReferences reader =
                new NativeGtDiagramReferences(
                        (codechicken.nei.recipe.ICraftingHandler) nativeHandler, source)) {
            require(
                    reader.size() == 11,
                    "Definition, diagram, both matcher directions or all-diagrams action disappeared");
            reader.finishVerification();
            codechicken.nei.ItemList.loadFinished = false;
            try {
                reader.verify();
                throw new AssertionError("Unready native query list was accepted");
            } catch (Jobs.Fault expected) {
                require(expected.code.equals("environment_changed"), expected.toString());
            } finally {
                codechicken.nei.ItemList.loadFinished = true;
            }
            ItemStack live = codechicken.nei.ItemList.items.get(0);
            live.getTagCompound().setString("native-query", "changed");
            try {
                reader.finishVerification();
                throw new AssertionError("Original query NBT drift was ignored");
            } catch (Jobs.Fault expected) {
                require(expected.code.equals("environment_changed"), expected.toString());
            } finally {
                live.getTagCompound().setString("native-query", "preserve original");
            }
            Object snapshot = field(reader, "snapshot");
            transport = (JsonArray) field(snapshot, "records");
            require(
                    transport.get(0).getAsJsonObject().getAsJsonObject("data").has("selection")
                            && transport
                                    .get(1)
                                    .getAsJsonObject()
                                    .getAsJsonObject("data")
                                    .has("nativeSlotInsertions"),
                    "Native empty-diagram and visibility selection evidence disappeared");
            NativeDiagramContents nativeContents =
                    (NativeDiagramContents) field(snapshot, "contents");
            boolean retainedQuery=false;
            for(JsonElement entry:transport){
                JsonObject data=entry.getAsJsonObject().getAsJsonObject("data");
                if(!data.get("kind").getAsString().equals("neiDiagramLookup"))continue;
                JsonObject original=data.getAsJsonObject("original");
                if(!original.get("kind").getAsString().equals("itemFact"))continue;
                ItemStack offered=nativeContents.items.get(original.get("fact").getAsString());
                if(offered!=null&&offered.getItem()==Items.paper&&offered.hasTagCompound()
                    &&offered.getTagCompound().getString("native-query").equals("preserve original"))retainedQuery=true;
            }
            require(retainedQuery,"Original NBT query lost its native normalized item fact");
            require(
                    transport.toString().contains("zeroForDamageableItem"),
                    "Native damaged-item query normalization was not recorded");
            for (Map.Entry<String, ItemStack> entry : nativeContents.items.entrySet()) {
                ItemStack stack = entry.getValue();
                specimens.add(
                        object(
                                "id",
                                entry.getKey(),
                                "registry",
                                net.minecraft.item.Item.itemRegistry.getNameForObject(
                                        stack.getItem()),
                                "meta",
                                Items.feather.getDamage(stack),
                                "nbt",
                                com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(
                                        stack.getTagCompound())));
            }
            for (Map.Entry<String, net.minecraftforge.fluids.FluidStack> entry :
                    nativeContents.fluids.entrySet()) {
                net.minecraftforge.fluids.FluidStack stack = entry.getValue();
                fluidSpecimens.add(
                        object(
                                "id",
                                entry.getKey(),
                                "registry",
                                stack.getFluid().getName(),
                                "nbt",
                                com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(
                                        stack.tag),
                                "temperature",
                                stack.getFluid().getTemperature(stack),
                                "density",
                                stack.getFluid().getDensity(stack),
                                "viscosity",
                                stack.getFluid().getViscosity(stack),
                                "luminosity",
                                stack.getFluid().getLuminosity(stack),
                                "gaseous",
                                stack.getFluid().isGaseous(stack)));
            }
            require(
                    transport
                                    .get(2)
                                    .getAsJsonObject()
                                    .getAsJsonObject("data")
                                    .get("query")
                                    .getAsString()
                                    .equals("crafting")
                            && transport
                                    .get(4)
                                    .getAsJsonObject()
                                    .getAsJsonObject("data")
                                    .get("query")
                                    .getAsString()
                                    .equals("usage"),
                    "Native binding directions were lost");
        }
        NativeDiagramContents contents = new NativeDiagramContents();
        JsonObject actual = contents.read(diagram);
        JsonArray cells = actual.getAsJsonArray("cells");
        require(cells.size() == 3, "Native empty logical cell was lost");
        JsonArray choices = cells.get(0).getAsJsonObject().getAsJsonArray("alternatives");
        require(
                choices.size() == 4
                        && choices.get(0).getAsJsonObject().get("amount").getAsString().equals("7"),
                "Alternatives/quantity were collapsed");
        require(
                choices.get(1).getAsJsonObject().get("amount").isJsonNull(),
                "Absent display quantity became consuming one");
        require(
                cells.get(1).getAsJsonObject().getAsJsonArray("alternatives").size() == 0,
                "Empty cell was populated");
        JsonObject template =
                cells.get(2)
                        .getAsJsonObject()
                        .getAsJsonArray("alternatives")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("component");
        require(
                template.get("meta").getAsInt() == 32767 && template.get("fact").isJsonNull(),
                "Wildcard was guessed as meta zero or captured as concrete icon");
        JsonObject annotation = actual.getAsJsonArray("annotations").get(0).getAsJsonObject();
        require(
                annotation
                        .getAsJsonObject("action")
                        .get("group")
                        .getAsString()
                        .endsWith("circuits"),
                "Native string navigation was lost");
        require(
                annotation.getAsJsonArray("tooltip").toString().contains("Cleanroom required"),
                "Native condition tooltip was lost");
        String saved = actual.toString();
        ((ItemStack) contents.items.values().iterator().next()).stackSize = 99;
        require(actual.toString().equals(saved), "Transport aliases mutable specimen state");
        invoke(
                buttonBuilder.getClass(),
                buttonBuilder,
                "setInteract",
                new Class<?>[] {Consumer.class},
                (Consumer<Object>)
                        ignored -> {
                            throw new AssertionError("Callback must not execute");
                        });
        Object unknown = call(buttonBuilder, "build");
        Object unknownBuilder = staticCall("diagram.Diagram", "builder", new Class<?>[0]);
        invoke(
                unknownBuilder.getClass(),
                unknownBuilder,
                "addInteractable",
                new Class<?>[] {type(API + "diagram.interactable.Interactable")},
                unknown);
        fails(() -> new NativeDiagramContents().read(call(unknownBuilder, "build")));
        // Equal coordinates in unrelated native slots must never create an inferred relation.
        Object ambiguousBuilder = staticCall("diagram.layout.Layout", "builder", new Class<?>[0]);
        invoke(
                ambiguousBuilder.getClass(),
                ambiguousBuilder,
                "putSlot",
                new Class<?>[] {
                    type(API + "diagram.layout.Layout$SlotKey"), type(API + "diagram.layout.Slot")
                },
                key,
                slot);
        Object key2 =
                staticCall(
                        "diagram.layout.Layout$SlotKey",
                        "create",
                        new Class<?>[] {String.class},
                        "unrelated");
        invoke(
                ambiguousBuilder.getClass(),
                ambiguousBuilder,
                "putSlot",
                new Class<?>[] {
                    type(API + "diagram.layout.Layout$SlotKey"), type(API + "diagram.layout.Slot")
                },
                key2,
                slot);
        Object ambiguous = staticCall("diagram.Diagram", "builder", new Class<?>[0]);
        invoke(
                ambiguous.getClass(),
                ambiguous,
                "addLayout",
                new Class<?>[] {type(API + "diagram.layout.Layout")},
                call(ambiguousBuilder, "build"));
        fails(() -> new NativeDiagramContents().read(call(ambiguous, "build")));
        Path output = Paths.get("build/native-tests/nei-diagram-content.json");
        Files.write(output, actual.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(
                Paths.get("build/native-tests/nei-diagram-contract.json"),
                object("records", transport, "items", specimens, "fluids", fluidSpecimens)
                        .toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println(
                "Native Diagram content: alternatives, exact quantity, empty cells, wildcard template, condition, navigation, unknown callback and ambiguity passed");
    }

    /** One original query may reach many diagrams; its large NBT must be stored once per direction. */
    private static void groupedQueries(Object layout) throws Exception {
        ItemStack original = new ItemStack(Items.paper);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        char[] text = new char[32768]; Arrays.fill(text, 'x');
        tag.setString("native-query-payload", new String(text)); original.setTagCompound(tag);
        Object input = component(original);
        Object builder = staticCall("diagram.matcher.ComponentDiagramMatcher", "builder", new Class<?>[0]);
        IdentityHashMap<Object,Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < 120; i++) {
            Object diagram = type(API + "diagram.Diagram")
                    .getConstructor(type(API + "diagram.layout.Layout"), com.google.common.collect.Multiset.class,
                            com.google.common.collect.ImmutableList.class)
                    .newInstance(layout, com.google.common.collect.ImmutableMultiset.of(), com.google.common.collect.ImmutableList.of());
            indices.put(diagram, i);
            Object entry = invoke(builder.getClass(), builder, "addDiagram", new Class<?>[]{type(API + "diagram.Diagram")}, diagram);
            invoke(entry.getClass(), entry, "addComponent", new Class<?>[]{type(API + "diagram.component.Component")}, input);
        }
        NativeDiagramBindings grouped = new NativeDiagramBindings();
        List<JsonObject> bindings = grouped.rows;
        Object matcher = call(builder,"build");
        NativeDiagramQueries query = new NativeDiagramQueries(matcher, false,
                new NativeDiagramContents(), indices, grouped, false, 28L * 1024 * 1024);
        query.scan(Collections.singletonList(original));
        require(bindings.size() == 2 && query.lookups.size() == 1, "Query payload was duplicated per destination");
        for (JsonObject binding : bindings) {
            Set<Integer> destinations = new HashSet<>();
            for (JsonElement index : binding.getAsJsonArray("diagrams")) destinations.add(index.getAsInt());
            require(destinations.size() == 120 && destinations.contains(0) && destinations.contains(119),
                    "Grouped native matching lost a destination");
        }
        require(query.lookups.get(0).getAsJsonArray("bindings").toString().equals("[0,1]"),
                "Original lookup lost a native direction");
        require(original.getTagCompound().getString("native-query-payload").length() == 32768,
                "Original native query was mutated");
        Object infoBuilder = staticCall("diagram.DiagramGroupInfo", "builder",
                new Class<?>[]{String.class,String.class,type(API+"diagram.component.ItemComponent"),int.class},
                "Grouped native queries",NativeGtDiagramReferences.PREFIX+"circuits",input,1);
        invoke(infoBuilder.getClass(), infoBuilder, "setIgnoreNbt", new Class<?>[]{boolean.class}, false);
        Object handler = type(API+"diagram.DiagramGroup")
                .getConstructor(type(API+"diagram.DiagramGroupInfo"),type(API+"diagram.matcher.DiagramMatcher"))
                .newInstance(call(infoBuilder,"build"),matcher);
        List<ItemStack> panel = codechicken.nei.ItemList.items;
        codechicken.nei.ItemList.items = Collections.singletonList(original);
        try (NativeGtDiagramReferences reader = new NativeGtDiagramReferences((codechicken.nei.recipe.ICraftingHandler)handler,
                object("owner","neicustomdiagram","handler",handler.getClass().getName(),"key",NativeGtDiagramReferences.PREFIX+"circuits"))) {
            reader.finishVerification();
            Object snapshot = field(reader,"snapshot");
            NativeDiagramContents contents = (NativeDiagramContents)field(snapshot,"contents");
            JsonArray items = array();
            for (Map.Entry<String,ItemStack> entry : contents.items.entrySet()) {
                ItemStack stack = entry.getValue();
                items.add(object("id",entry.getKey(),"registry",net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                        "meta",Items.feather.getDamage(stack),"nbt",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(stack.getTagCompound()),"tags",array()));
            }
            Path target = Paths.get("build/native-tests/nei-diagram-grouped-contract.json");
            Files.createDirectories(target.getParent());
            Files.write(target,com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(object("records",field(snapshot,"records"),"items",items,"fluids",array())));
            require(reader.counts().get("bindings").getAsInt()==2 && reader.counts().get("diagrams").getAsInt()==120,
                    "Published query closure duplicated or dropped native targets");
        } finally { codechicken.nei.ItemList.items = panel; }
        System.out.println("Native diagram queries: 120 destinations retain one signed/typed NBT binding per direction within the unchanged byte budget");
    }

    private static Object component(ItemStack stack) {
        return staticCall(
                "diagram.component.ItemComponent",
                "createWithNbt",
                new Class<?>[] {ItemStack.class},
                stack);
    }

    private static void replaceStatic(java.lang.reflect.Field field, Object value)
            throws Exception {
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private static Object display(Object component, Integer amount) {
        Object builder =
                staticCall(
                        "diagram.component.DisplayComponent",
                        "builder",
                        new Class<?>[] {type(API + "diagram.component.Component")},
                        component);
        if (amount != null)
            invoke(builder.getClass(), builder, "setStackSize", new Class<?>[] {int.class}, amount);
        return call(builder, "build");
    }

    private static Object staticCall(
            String cls, String method, Class<?>[] parameters, Object... arguments) {
        return invoke(type(API + cls), null, method, parameters, arguments);
    }

    private static Object call(Object target, String method) {
        return invoke(target.getClass(), target, method, new Class<?>[0]);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void fails(Runnable operation) {
        try {
            operation.run();
        } catch (Jobs.Fault expected) {
            require(expected.code.equals("reference_unsupported"), expected.toString());
            return;
        }
        throw new AssertionError("Unknown semantics were silently accepted");
    }
}
