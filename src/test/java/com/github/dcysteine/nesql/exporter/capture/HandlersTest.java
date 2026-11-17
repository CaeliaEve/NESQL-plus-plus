package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;

/** A subclass does not inherit verified semantics merely by inheriting a NEI UI. */
public final class HandlersTest {
    public static void run() {
        recordKinds();
        referenceOwnership();
        registryOwnership();
        require(StructureReferences.supportsClass(StructureReferences.GT)
                && StructureReferences.supportsClass(StructureReferences.COMPAT), "Native structures lost their dedicated route");
        require(!StructureReferences.supportsClass("unknown.TemplateStructureHandler"), "Unverified structure subclass claimed support");
        require(Recipes.adapter(new FurnaceRecipeHandler()) == Recipes.Adapter.FURNACE, "Furnace loader disappeared");
        require(Recipes.adapter(new ShapedRecipeHandler()) == Recipes.Adapter.SHAPED, "Shaped loader disappeared");
        require(Recipes.adapter(new ShapelessRecipeHandler()) == Recipes.Adapter.SHAPELESS, "Shapeless loader disappeared");
        require(Recipes.adapter(new FurnaceRecipeHandler() {}) == null, "Unverified furnace subclass claimed support");
        require(Recipes.adapter(new ShapedRecipeHandler() {}) == null, "Unverified shaped subclass claimed support");
        require(Recipes.adapter(new ShapelessRecipeHandler() {}) == null, "Unverified shapeless subclass claimed support");
        require(BrewingRecipes.supportsClass("codechicken.nei.recipe.BrewingRecipeHandler"), "Brewing handler was not routed to its dedicated adapter");
        require(FireworkRecipes.supportsClass("codechicken.nei.recipe.FireworkRecipeHandler"), "Firework handler was not routed to its dedicated adapter");
        require(!FireworkRecipes.supportsClass("codechicken.nei.recipe.FuelRecipeHandler"), "Fuel handler claimed firework semantics");
        require(ProjectRedRecipes.supportsClass("mrtjp.projectred.core.libmc.recipe.PRShapedRecipeHandler"), "ProjectRed shaped handler was not routed");
        require(ProjectRedRecipes.supportsClass("mrtjp.projectred.core.libmc.recipe.PRShapelessRecipeHandler"), "ProjectRed shapeless handler was not routed");
        require(Ic2AdvancedRecipes.supportsClass("ic2.neiIntegration.core.recipehandler.AdvRecipeHandler"), "IC2 advanced shaped handler was not routed");
        require(Ic2AdvancedRecipes.supportsClass("ic2.neiIntegration.core.recipehandler.AdvShapelessRecipeHandler"), "IC2 advanced shapeless handler was not routed");
        require(Ic2LatheRecipes.supportsClass("ic2.neiIntegration.core.recipehandler.LatheRecipeHandler"), "IC2 lathe handler was not routed");
        require(ProjectBlueRecipes.supportsClass("gcewing.projectblue.nei.NEIRecipeHandler"), "ProjectBlue handler was not routed");
        require(FuelRecipes.supportsClass("codechicken.nei.recipe.FuelRecipeHandler"), "Fuel handler did not get an explicit dedicated module");
        require(BotaniaWorldRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerPureDaisy"), "Botania pure daisy handler was not routed");
        require(BotaniaWorldRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerElvenTrade"), "Botania elven trade handler was not routed");
        require(BotaniaWorldRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerPetalApothecary"), "Botania petal handler was not routed");
        require(BotaniaWorldRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerBrewery"), "Botania brewery handler was not routed");
        for (String handler : new String[] {
                "net.bdew.gendustry.nei.ImprinterHandler", "net.bdew.gendustry.nei.SamplerHandler",
                "net.bdew.gendustry.nei.TransposerHandler", "net.bdew.gendustry.nei.MutatronHandler",
                "net.bdew.gendustry.nei.ReplicatorHandler", "net.bdew.gendustry.nei.ExtractorHandler"})
            require(GendustryGeneticsRecipes.supportsClass(handler), "Gendustry genetics handler was not routed: " + handler);
        require(!GendustryGeneticsRecipes.supportsClass("net.bdew.gendustry.nei.TemplateCraftingHandler"), "Gendustry template handler claimed genetics semantics");
        require(HarvestCraftRecipes.supportsClass("com.pam.harvestcraft.GardenRecipeHandler"), "HarvestCraft garden handler was not routed");
        require(HarvestCraftRecipes.supportsClass("com.pam.harvestcraft.FishTrapRecipeHandler"), "HarvestCraft fish trap handler was not routed");
        require(HarvestCraftRecipes.supportsClass("com.pam.harvestcraft.AnimalTrapRecipeHandler"), "HarvestCraft animal trap handler was not routed");
        require(ChiselRecipes.supportsClass("team.chisel.compat.nei.RecipeHandlerChisel"), "Chisel handler was not routed");
        require(ForgeRegistryReferences.supportsClass("tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary"), "Forge OreDictionary requires its reference adapter");
        require(ForgeRegistryReferences.supportsClass("tonius.neiintegration.mods.mcforge.RecipeHandlerFluidRegistry"), "Forge FluidRegistry requires its reference adapter");
        require(NativeReferences.class.isAssignableFrom(FireworkRecipes.class), "Firework rules must have a dedicated non-preview domain");
        require(NativeReferences.class.isAssignableFrom(Ic2LatheRecipes.class), "IC2 lathe must route through its native state domain");
    }

    /** Report actual emitted records, independently of the discovery adapter label. */
    private static void recordKinds() {
        NativeReferences references = new NativeReferences() {
            public int size() { return 0; }
            public String domain() { return "fixture"; }
            public void verify() {}
            public com.google.gson.JsonObject counts() { return new com.google.gson.JsonObject(); }
            public void capture(int index, Facts facts) { throw new AssertionError("No records in fixture"); }
        };
        Recipes.Handler source = new Recipes.Handler(new FurnaceRecipeHandler(), 0);
        try (Recipes.Cursor cursor = new Recipes.Cursor(source, null, null, references)) {
            require("references".equals(cursor.recordKind()), "Reference cursor reported its discovery recipe adapter");
        }
        try (Recipes.Cursor cursor = new Recipes.Cursor(source, null, null, null)) {
            require("recipes".equals(cursor.recordKind()), "Recipe cursor was reported as references");
        }
    }

    /** A category callback may fail before the owned reference cursor is returned. */
    private static void referenceOwnership() {
        com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure =
                new com.github.dcysteine.nesql.exporter.task.Jobs.Fault("fixture_domain", "Native domain callback failed");
        RuntimeException cleanup = new RuntimeException("Native reference cleanup failed");
        int[] closed = {0};
        NativeReferences references = new NativeReferences() {
            public int size() { return 0; }
            public String domain() { throw failure; }
            public void verify() {}
            public com.google.gson.JsonObject counts() { return new com.google.gson.JsonObject(); }
            public void capture(int index, Facts facts) { throw new AssertionError("No fixture records"); }
            public void close() { closed[0]++; throw cleanup; }
        };
        Recipes.Handler source = new Recipes.Handler(new FurnaceRecipeHandler(), 0);
        try {
            source.openReferences(new Facts("en_US"), references);
            throw new AssertionError("Native category failure was swallowed");
        } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault actual) {
            require(actual == failure, "Native failure identity was replaced");
            require(closed[0] == 1, "Failed reference adoption leaked or closed twice");
            require(actual.getSuppressed().length == 1 && actual.getSuppressed()[0] == cleanup,
                    "Cleanup failure hid the original callback failure");
        }
    }
    private static void registryOwnership() {
        com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure =
                new com.github.dcysteine.nesql.exporter.task.Jobs.Fault("fixture_program", "Native program callback failed");
        RuntimeException cleanup = new RuntimeException("Native registry cleanup failed");
        int[] closed = {0};
        RegistryRecipes registry = new RegistryRecipes() {
            public int size() { return 0; }
            public boolean capture(int index, RecipeRow row) { throw new AssertionError("Empty fixture"); }
            public String program(Facts facts) { throw failure; }
            public void close() { closed[0]++; throw cleanup; }
        };
        Recipes.Handler source = new Recipes.Handler(new FurnaceRecipeHandler(), 0);
        try {
            source.openRegistry(new Facts("en_US"), registry);
            throw new AssertionError("Native category/program failure was swallowed");
        } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault actual) {
            require(actual == failure, "Native registry failure identity was replaced");
            require(closed[0] == 1, "Failed registry adoption leaked or closed twice");
            require(actual.getSuppressed().length == 1 && actual.getSuppressed()[0] == cleanup,
                    "Registry cleanup failure hid the original category failure");
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
