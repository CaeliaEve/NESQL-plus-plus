package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategoryList;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.research.ResearchPage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Uses the real TC API to reproduce Gadomancy's cross-category virtual registrations. */
public final class StudiesTest {
    private StudiesTest() {}

    public static void run() {
        LinkedHashMap<String, ResearchCategoryList> previous = ResearchCategories.researchCategories;
        try {
            ResearchCategories.researchCategories = new LinkedHashMap<>();
            for (String category : new String[] {"BASICS", "THAUMATURGY", "ALCHEMY", "ARTIFICE", "GOLEMANCY", "ELDRITCH", "gadomancy"}) {
                ResearchCategories.registerCategory(category, null, null);
            }
            Map<String, ResearchItem> originals = new LinkedHashMap<>();
            String[][] keys = {
                    {"ELEMENTALAXE", "ARTIFICE"}, {"ELEMENTALPICK", "ARTIFICE"}, {"ELEMENTALSHOVEL", "ARTIFICE"},
                    {"ELEMENTALSWORD", "ARTIFICE"}, {"BOOTSTRAVELLER", "ARTIFICE"}, {"FOCUSPRIMAL", "ELDRITCH"},
                    {"PAVEWARD", "ARTIFICE"}, {"WANDPEDFOC", "THAUMATURGY"}, {"COREUSE", "GOLEMANCY"}, {"RUNICARMOR", "ARTIFICE"}
            };
            for (int index = 0; index < keys.length; index++) {
                String key = keys[index][0], category = keys[index][1];
                originals.put(key, visible(key, category, index).registerResearchItem());
                new ResearchItem(key, "gadomancy").registerResearchItem();
            }
            ResearchItem hidden = new ResearchItem("HIDDEN_RECIPE", "ARTIFICE").setSiblings("RUNICARMOR").registerResearchItem();
            Studies studies = Studies.capture();
            require(studies.all().size() == 11 && studies.get("HIDDEN_RECIPE") == hidden, "Unique virtual research was dropped");
            for (Map.Entry<String, ResearchItem> entry : originals.entrySet()) {
                require(studies.get(entry.getKey()) == entry.getValue() && Studies.find(entry.getKey()) == entry.getValue(),
                        "Research and recipe references did not retain the native definition: " + entry.getKey());
            }
            JsonObject report = studies.describe();
            require(report.get("valid").getAsBoolean() && report.get("entries").getAsInt() == 21
                    && report.get("references").getAsInt() == 10 && report.getAsJsonArray("rows").size() == 10,
                    "Virtual research registrations were not reported");
            JsonObject axe = row(report, "ELEMENTALAXE");
            require(axe.getAsJsonObject("resolved").get("category").getAsString().equals("ARTIFICE")
                    && axe.getAsJsonArray("registrations").get(1).getAsJsonObject().get("reference").getAsBoolean(),
                    "Diagnostics lost the original category or virtual reference");
            require(studies.get("@knowledge") == null && Studies.find("@knowledge") == null, "Unregistered knowledge flags were rejected");
            rejected("research_reference", () -> studies.get("MISSING"), "MISSING");
            rejected("research_key", () -> studies.get(""), "prerequisite");

            // Native lookup scans item keys, not category-map keys or declared category fields.
            ResearchCategoryList gadomancy = ResearchCategories.getResearchList("gadomancy");
            gadomancy.research.put("ENTRY_ALIAS", originals.get("ELEMENTALAXE"));
            Studies aliased = Studies.capture();
            require(aliased.all().size() == 11 && aliased.describe().get("aliases").getAsInt() == 1,
                    "The same object registered at another location became a new research identity");
            rejected("research_reference", () -> aliased.get("ENTRY_ALIAS"), "ENTRY_ALIAS");
            gadomancy.research.remove("ENTRY_ALIAS");
            ResearchItem moved = visible("MOVED", "OLD_CATEGORY", 30);
            gadomancy.research.put("MOVED", moved);
            require(Studies.capture().get("MOVED") == moved && moved.category.equals("OLD_CATEGORY"), "Capture rewrote moved research");
            gadomancy.research.remove("MOVED");

            ResearchItem original = originals.get("ELEMENTALAXE");
            ResearchItem marker = gadomancy.research.get("ELEMENTALAXE");
            for (ResearchItem competing : new ResearchItem[] {
                    visible("ELEMENTALAXE", "gadomancy", 30),
                    new ResearchItem("ELEMENTALAXE", "gadomancy").setAutoUnlock(),
                    new ResearchItem("ELEMENTALAXE", "gadomancy").setSiblings("HIDDEN_RECIPE"),
                    new ResearchItem("ELEMENTALAXE", "gadomancy").setPages(new ResearchPage("Meaningful research text")),
                    new ResearchItem("ELEMENTALAXE", "gadomancy") { @Override public String getName() { return "Custom behavior"; } }
            }) {
                gadomancy.research.put("ELEMENTALAXE", competing);
                Studies conflict = Studies.capture();
                require(!conflict.describe().get("valid").getAsBoolean(), "A substantive collision was silently discarded");
                rejected("research_registry", conflict::all, "ELEMENTALAXE", "ARTIFICE", "gadomancy");
            }
            gadomancy.research.put("ELEMENTALAXE", marker);

            // A patch may choose a different registered object than a sorted map would.
            LinkedHashMap<String, ResearchCategoryList> reversed = new LinkedHashMap<>();
            reversed.put("gadomancy", gadomancy);
            reversed.put("ARTIFICE", ResearchCategories.getResearchList("ARTIFICE"));
            reversed.put("THAUMATURGY", ResearchCategories.getResearchList("THAUMATURGY"));
            reversed.put("GOLEMANCY", ResearchCategories.getResearchList("GOLEMANCY"));
            reversed.put("ELDRITCH", ResearchCategories.getResearchList("ELDRITCH"));
            Studies patched = new Studies(reversed, ResearchCategories::getResearch);
            require(patched.get("ELEMENTALAXE") == original, "Exporter guessed a winner instead of calling the native resolver");
            ResearchItem outside = visible("ELEMENTALAXE", "STALE_CACHE", 0);
            Studies stale = new Studies(ResearchCategories.researchCategories,
                    key -> key.equals("ELEMENTALAXE") ? outside : ResearchCategories.getResearch(key));
            rejected("research_registry", stale::all, "ELEMENTALAXE", "outside");

            ResearchCategories.getResearchList("ARTIFICE").research.put("ELEMENTALAXE", visible("ELEMENTALAXE", "ARTIFICE", 0));
            rejected("registry_changed", () -> studies.get("ELEMENTALAXE"), "ELEMENTALAXE");
            ResearchCategories.getResearchList("ARTIFICE").research.put("ELEMENTALAXE", original);
            ResearchCategories.researchCategories = reversed;
            rejected("research_registry", () -> Studies.capture().all(), "BOOTSTRAVELLER", "gadomancy");
            ResearchCategories.researchCategories = new LinkedHashMap<>();
            rejected("research_registry", () -> Studies.capture().all(), "not loaded");
            System.out.println("Research registry: 10 native virtual references, aliases, content conflicts and lookup consistency passed");
        } finally {
            ResearchCategories.researchCategories = previous;
        }
    }

    private static ResearchItem visible(String key, String category, int position) {
        return new ResearchItem(key, category, new AspectList(), position, position, 2, new ResourceLocation("thaumcraft", "test/research"));
    }

    private static JsonObject row(JsonObject report, String key) {
        for (com.google.gson.JsonElement value : report.getAsJsonArray("rows")) {
            JsonObject row = value.getAsJsonObject();
            if (row.has("key") && key.equals(row.get("key").getAsString())) return row;
        }
        throw new AssertionError("Missing research diagnostics: " + key);
    }

    private static void rejected(String code, Runnable action, String... details) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (Jobs.Fault expected) {
            require(expected.code.equals(code), "Wrong research failure: " + expected.code);
            for (String detail : details) require(expected.getMessage().contains(detail), "Research failure lacks " + detail + ": " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
