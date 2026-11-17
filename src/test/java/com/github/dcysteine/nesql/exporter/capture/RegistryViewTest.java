package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.CaptureOutcome;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fluids.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Registry and direct cursors must not ask for display pages to publish facts. */
public final class RegistryViewTest {
    public static void run() {
        for (boolean direct : new boolean[] {false, true}) {
            Pages handler = new Pages();
            RegistryRecipes registry = new RegistryRecipes() {
                public int size() { return 3; }
                public boolean capture(int index, RecipeRow row) { populate(index, row); return true; }
            };
            Recipes.Handler source = new Recipes.Handler(handler, 0);
            Facts facts = new Facts("en_US");
            try (Recipes.Cursor cursor = new Recipes.Cursor(source, facts, registry, null)) {
                cursor.capture(2);
            }
            Facts.Batch batch = facts.drain();
            if (!batch.pictures.isEmpty() || batch.icons.size() != 2)
                throw new AssertionError("Semantic registry lost content icons or requested GUI assets");
            boolean found = false;
            for (Facts.Record record : batch.records) if (record.kind.equals("recipes")) {
                found = record.value.getAsJsonArray("outputs").get(0).getAsJsonObject().get("amount").getAsInt() == 3;
            }
            if (!found) throw new AssertionError("Later registry index was not captured");
        }
        accounting();
        lifecycle();
        System.out.println("Registry semantics: no GUI, explicit outcomes, transactional facts and dedupe");
    }
    private static void lifecycle() {
        Facts facts = new Facts("en_US");
        final int[] closes = {0};
        RegistryRecipes registry = new RegistryRecipes() {
            public int size() { return 1; }
            public boolean capture(int index, RecipeRow row) { populate(0, row); return true; }
            public void close() { closes[0]++; throw new Jobs.Fault("fixture_cleanup", "original reader cleanup failure"); }
        };
        Pages handler = new Pages();
        Recipes.Cursor cursor = new Recipes.Cursor(new Recipes.Handler(handler, 0), facts, registry, null);
        Recipes.Cursor.Attempt staged = cursor.attempt(0, facts);
        try { cursor.close(); throw new AssertionError("Reader cleanup failure disappeared"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("fixture_cleanup"), "Original reader cleanup failure changed"); }
        Facts.Batch abandoned = facts.drain();
        require(abandoned.records.isEmpty() && abandoned.icons.isEmpty(), "Cursor teardown left pending facts alive");
        try { staged.commit(); throw new AssertionError("Closed cursor committed a pending recipe"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("capture_closed"), "Wrong closed commit error"); }
        staged.close();
        cursor.close();
        require(closes[0] == 1, "Cursor cleaned a native reader more than once");
        try { cursor.size(); throw new AssertionError("Closed cursor queried a released reader"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("capture_closed"), "Wrong closed cursor error"); }
    }
    private static void accounting() {
        Facts facts = new Facts("en_US");
        String prior = facts.text("previously committed");
        facts.drain();
        final int[] mode = {0};
        JsonObject proof = object("reason", "native predicate has no candidates", "nativeIndex", 0);
        RegistryRecipes registry = new RegistryRecipes() {
            public int size() { return 4; }
            public boolean capture(int index, RecipeRow row) { throw new AssertionError("Expanded capture bypassed"); }
            public JsonObject exclusion(int index) { return mode[0] == 2 ? proof : null; }
            public List<RecipeRow> captureRows(int index, RecipeRow row) {
                row.facts.text("attempt text");
                if (mode[0] == 0 || mode[0] == 2) return Collections.emptyList();
                populate(0, row);
                if (mode[0] == 1) return Arrays.asList(row, null);
                if (mode[0] == 4) throw new java.util.concurrent.CancellationException("cancel after facts");
                if (mode[0] == 5) row.note("test:native-note", "test adapter", "distinct necessary information");
                return Collections.singletonList(row);
            }
        };
        Pages handler = new Pages();
        Recipes.Handler source = new Recipes.Handler(handler, 0);
        try (Recipes.Cursor cursor = new Recipes.Cursor(source, facts, registry, null)) {
            try { cursor.capture(0); throw new AssertionError("Unproven empty capture passed"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("capture_empty"), "Wrong empty failure"); }
            require(facts.drain().records.isEmpty(), "Empty attempt leaked strings");
            mode[0] = 1;
            try { cursor.capture(0); throw new AssertionError("Broken later branch passed"); }
            catch (Jobs.Fault expected) {}
            Facts.Batch rejected = facts.drain();
            require(rejected.records.isEmpty() && rejected.icons.isEmpty(), "Partial branch leaked facts");
            mode[0] = 3;
            try (Recipes.Cursor.Attempt attempt = cursor.attempt(0, facts)) {
                Facts.Batch batch = facts.drain();
                require(batch.icons.size() == 2, "Retry lost previously memoized fluid facts");
                batch.freezeRecords();
                // A downstream renderer/snapshot rejection must not reserve the recipe ID.
            }
            require(facts.drain().records.isEmpty(), "Detached rejected batch leaked into the next attempt");
            CaptureOutcome captured = cursor.capture(0);
            require(captured.kind == CaptureOutcome.Kind.CAPTURED && captured.emitted == 1, "Rejected branch poisoned dedupe");
            Facts.Batch committed = facts.drain();
            require(committed.icons.size() == 2 && recipeCount(committed) == 1, "Retry omitted its complete facts");
            require(facts.text("previously committed").equals(prior) && facts.drain().records.isEmpty(), "Rollback erased old facts");
            CaptureOutcome duplicate = cursor.capture(1);
            require(duplicate.kind == CaptureOutcome.Kind.DUPLICATE && duplicate.duplicates == 1
                    && duplicate.json().getAsJsonArray("duplicateOrigins").get(0).getAsJsonObject().get("firstIndex").getAsInt() == 0,
                    "Duplicate lost native provenance");
            require(recipeCount(facts.drain()) == 0, "Duplicate emitted a second recipe");
            mode[0] = 5;
            try { cursor.capture(2); throw new AssertionError("Different native information was deduped away"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("recipe_identity_conflict"), "Wrong identity conflict"); }
            mode[0] = 2;
            CaptureOutcome excluded = cursor.capture(2);
            proof.addProperty("reason", "mutated caller proof");
            require(excluded.kind == CaptureOutcome.Kind.EXCLUDED
                    && !excluded.json().getAsJsonObject("proof").get("reason").getAsString().equals("mutated caller proof"),
                    "Exclusion lacks immutable evidence");
            require(facts.drain().records.isEmpty(), "Excluded attempt published unused facts");
            mode[0] = 4;
            try { cursor.capture(3); throw new AssertionError("Cancellation disappeared"); }
            catch (java.util.concurrent.CancellationException expected) {}
            require(facts.drain().records.isEmpty(), "Cancelled attempt leaked facts");
            JsonObject counts = cursor.accounting();
            require(counts.get("captured").getAsInt() == 1 && counts.get("duplicate").getAsInt() == 1
                    && counts.get("excluded").getAsInt() == 1, "Failed/abandoned attempts contaminated accounting");
        }
        // Batched checks stage independent captures before worker validation. A failed
        // first snapshot must not turn a later valid entry into a false exclusion.
        mode[0] = 3;
        try (Recipes.Cursor cursor = new Recipes.Cursor(source, facts, registry, null)) {
            try (Recipes.Cursor.Attempt first = cursor.attempt(0, new Facts("en_US"));
                 Recipes.Cursor.Attempt second = cursor.attempt(1, new Facts("en_US"))) {
                first.close();
                require(second.commit().kind == CaptureOutcome.Kind.CAPTURED, "Abandoned staged attempt poisoned the next entry");
            }
            try (Recipes.Cursor.Attempt third = cursor.attempt(2, new Facts("en_US"));
                 Recipes.Cursor.Attempt fourth = cursor.attempt(3, new Facts("en_US"))) {
                require(third.commit().kind == CaptureOutcome.Kind.DUPLICATE && fourth.commit().kind == CaptureOutcome.Kind.DUPLICATE,
                        "Deferred commit lost successful duplicate classification");
            }
        }
        RegistryRecipes manyExcluded = new RegistryRecipes() {
            public int size() { return 64; }
            public boolean capture(int index, RecipeRow row) {
                return row.exclude(object("native", "test native matcher", "reason", "empty_native_required_input_predicate", "nativeIndex", index));
            }
        };
        try (Recipes.Cursor cursor = new Recipes.Cursor(source, facts, manyExcluded, null)) {
            for (int index = 0; index < 64; index++) require(cursor.capture(index).kind == CaptureOutcome.Kind.EXCLUDED, "Proven native exclusion was lost");
            require(cursor.accounting().get("excluded").getAsInt() == 64 && cursor.exclusions().getAsJsonObject("preview").get("omitted").getAsInt() == 48,
                    "Bounded exclusion preview changed full accounting");
        }
    }
    private static int recipeCount(Facts.Batch batch) {
        int count = 0;
        for (Facts.Record record : batch.records) if (record.kind.equals("recipes")) count++;
        return count;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void populate(int index, RecipeRow row) {
        row.fluidInput(0, new FluidStack(FluidRegistry.WATER, 1));
        row.fluidOutput(0, new FluidStack(FluidRegistry.LAVA, index + 1));
    }
    private static final class Pages extends TemplateRecipeHandler {
        @Override public void drawBackground(int index) { throw new AssertionError("Background requested"); }
        @Override public void drawForeground(int index) { throw new AssertionError("Foreground requested"); }
        @Override public int getRecipeHeight(int index) { throw new AssertionError("Dimensions requested"); }
        @Override public String getRecipeName() { return "Registry semantics"; }
        @Override public String getGuiTexture() { throw new AssertionError("Texture requested"); }
    }
}
