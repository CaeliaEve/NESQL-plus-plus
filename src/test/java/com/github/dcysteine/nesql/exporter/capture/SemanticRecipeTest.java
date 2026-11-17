package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import java.lang.reflect.Method;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Recipe facts and content icons must not depend on any native GUI callback. */
final class SemanticRecipeTest {
    static void run() throws Exception {
        Snapshot lifecycle=new Snapshot();
        if(lifecycle.size()!=0)throw new AssertionError("Ready-empty snapshot changed meaning");
        lifecycle.close();lifecycle.close();
        if(lifecycle.releases!=1)throw new AssertionError("Snapshot release is not idempotent");
        try{lifecycle.size();throw new AssertionError("Closed native snapshot remains readable");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){if(!expected.code.equals("capture_closed"))throw expected;}
        // Recipe rows own semantics only: no discarded per-slot GUI tree.
        for (java.lang.reflect.Field field : RecipeRow.class.getDeclaredFields())
            if (field.getName().equals("elements"))
                throw new AssertionError("Recipe still allocates a native GUI element tree");
        {
            NoGui handler = new NoGui();
            Recipes.Handler source = new Recipes.Handler(handler, 0);
            Facts facts = new Facts("en_US");
            RecipeRow row = new RecipeRow(facts, source.origin, source.id, 0);
            for(boolean keep:new boolean[]{false,true}) {
                RecipeRow invalid=new RecipeRow(facts,source.origin,source.id,0);
                try { invalid.fluidInput(0,new FluidStack(FluidRegistry.WATER,-4),keep);throw new AssertionError("Negative fluid input was silently replaced with one mB"); }
                catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) { if(!expected.code.equals("invalid_amount"))throw expected; }
            }
            row.fluidInput(0, new FluidStack(FluidRegistry.WATER, 3));
            try { new RecipeRow(facts,source.origin,source.id,0).fluidInput(0,new FluidStack(FluidRegistry.WATER,0));
                throw new AssertionError("Unknown-family zero amount was silently accepted"); }
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) { if(!expected.code.equals("invalid_amount"))throw expected; }
            row.fluidOutput(0, new FluidStack(FluidRegistry.LAVA, 7));
            Method note = RecipeRow.class.getDeclaredMethod("note", String.class, String.class, String.class);
            String identity = com.github.dcysteine.nesql.exporter.source.Identity.recipe(row.record);
            note.invoke(row, "native:requirement", "NoGui#getRequirement", "Requires an empty output tank");
            if (!identity.equals(com.github.dcysteine.nesql.exporter.source.Identity.recipe(row.record)))
                throw new AssertionError("Display note changed the recipe identity");
            try {
                note.invoke(row, "native:requirement", "NoGui#getRequirement", "Conflicting duplicate");
                throw new AssertionError("Duplicate native note accepted");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                if (!(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault)) throw expected;
            }
            if (row.branch().record.getAsJsonArray("notes").size() != 1)
                throw new AssertionError("Branch lost native explanations");
            Facts emittedFacts=new Facts("en_US");
            final RecipeRow[] emitted=new RecipeRow[1];
            RegistryRecipes registry=new RegistryRecipes(){
                public int size(){return 1;}
                public boolean capture(int index,RecipeRow captured){
                    captured.fluidInput(0,new FluidStack(FluidRegistry.WATER,3));
                    captured.fluidOutput(0,new FluidStack(FluidRegistry.LAVA,7));
                    captured.note("native:requirement","NoGui#getRequirement","Requires an empty output tank");
                    emitted[0]=captured;return true;
                }
            };
            Recipes.Cursor cursor = new Recipes.Cursor(source, emittedFacts, registry, null);
            if(cursor.capture(0).kind!=com.github.dcysteine.nesql.exporter.task.CaptureOutcome.Kind.CAPTURED)throw new AssertionError("Semantic capture did not commit a recipe");
            row=emitted[0];
            Facts.Batch batch = emittedFacts.drain();
            com.google.gson.JsonArray texts = new com.google.gson.JsonArray();
            com.google.gson.JsonArray fluids = new com.google.gson.JsonArray();
            for (Facts.Record record : batch.records) {
                if (record.kind.equals("strings")) texts.add(record.value);
                if (record.kind.equals("fluids")) fluids.add(record.value);
                if (record.kind.equals("views") || record.kind.equals("tracks"))
                    throw new AssertionError("Recipe emitted native GUI presentation records");
            }
            if (!batch.pictures.isEmpty())
                throw new AssertionError("Recipe requested GUI assets");
            for (Facts.Icon icon : batch.icons) if (icon.kind.equals("fluids")) fluids.add(icon.record);
            if (batch.icons.size() != 2 || batch.records.size() < 1)
                throw new AssertionError("Removing GUI capture discarded content icons or facts");
            if (row.record.has("view")
                    || !row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                    || !row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("7"))
                throw new AssertionError("Semantic recipe changed");
            cursor.close();
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("build/native-tests"));
            java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/semantic-notes-contract.json"),
                    com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
                        com.github.dcysteine.nesql.exporter.source.Json.object("recipe", row.record, "strings", texts, "fluids", fluids)));
        }
        System.out.println("Semantic recipes: no native GUI callbacks; facts and content icons retained");
    }

    private static final class Snapshot extends ReaderSnapshot implements RegistryRecipes {
        int releases;
        public int size(){checkOpen();return 0;}
        public boolean capture(int index,RecipeRow row){checkOpen();throw new IndexOutOfBoundsException();}
        protected void release(){releases++;}
    }

    private static final class NoGui extends TemplateRecipeHandler {
        @Override public String getRecipeName() { return "Semantic recipe"; }
        @Override public String getGuiTexture() { throw new AssertionError("GUI texture requested"); }
        @Override public int getRecipeHeight(int index) { throw new AssertionError("GUI dimensions requested"); }
        @Override public void drawBackground(int index) { throw new AssertionError("GUI background requested"); }
        @Override public void drawForeground(int index) { throw new AssertionError("GUI foreground requested"); }
    }
}
