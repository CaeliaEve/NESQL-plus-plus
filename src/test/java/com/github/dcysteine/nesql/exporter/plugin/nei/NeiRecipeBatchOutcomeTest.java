package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;

public final class NeiRecipeBatchOutcomeTest {
    private NeiRecipeBatchOutcomeTest() {}

    public static void main(String[] args) {
        assertItemUniverseFailurePropagatesToBatch();
        assertRecipeTypeCreationFailsClosedWithoutUnknownFallback();
        assertMobsInfoEecRowFailurePropagatesToBatch();
        assertMobsInfoInfernalReflectionFailurePropagatesToBatch();
        NeiRecipeBatchOutcome outcome = new NeiRecipeBatchOutcome();
        try {
            fakeHandlerExport();
            throw new AssertionError("Expected fake handler failure");
        } catch (IllegalStateException handlerFailure) {
            require(handlerFailure.getMessage().contains("fake handler failed"), "handler propagation");
            require(handlerFailure.getCause() != null, "row failure retained as cause");
            require(handlerFailure.getCause().getMessage().contains("fake row failed"), "row propagation");
            outcome.recordFailure("fake-handler", FakeHandler.class.getName(), handlerFailure);
        }

        PluginExportResult result = outcome.build(1, 0, 0);
        require(result.status == PluginExportResult.Status.PARTIAL, "batch status is partial");
        require(result.counts.get("handlersFailed") == 1L, "failed handler count");
        require(result.errors.size() == 1, "batch error count");
        require(result.errors.get(0).code.equals("nei-handler-export-failed"), "batch error code");
        require(result.errors.get(0).message.contains("fake handler failed"), "batch error detail");
    }

    private static void assertRecipeTypeCreationFailsClosedWithoutUnknownFallback() {
        try {
            NeiRecipeExportProcessor.requireRecipeTypeCreation(
                    "broken.handler",
                    "Broken Recipes",
                    "crafting",
                    new NeiRecipeExportProcessor.RecipeTypeCreation<String>() {
                        @Override
                        public String create() {
                            throw new IllegalArgumentException("recipe type registry failed");
                        }
                    });
            throw new AssertionError("Expected recipe type creation failure");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("broken.handler"), "recipe type handler retained");
            require(expected.getMessage().contains("Broken Recipes"), "recipe type name retained");
            require(expected.getCause() instanceof IllegalArgumentException,
                    "recipe type failure remains the cause");
            require(expected.getCause().getMessage().contains("registry failed"),
                    "recipe type registry detail retained");
        }
    }

    private static void assertMobsInfoEecRowFailurePropagatesToBatch() {
        NeiRecipeBatchOutcome outcome = new NeiRecipeBatchOutcome();
        try {
            NeiRecipeExportProcessor.exportRowsFailClosed(
                    "mobsinfo.mobhandler",
                    1,
                    new NeiRecipeExportProcessor.RecipeRowExporter() {
                        @Override
                        public boolean export(int recipeIndex) {
                            throw new IllegalArgumentException("EEC row failed");
                        }
                    });
            throw new AssertionError("Expected EEC row failure");
        } catch (IllegalStateException handlerFailure) {
            require(handlerFailure.getMessage().contains("mobsinfo.mobhandler"), "EEC handler retained");
            require(handlerFailure.getMessage().contains("row 0/1"), "EEC row retained");
            outcome.recordFailure("Extreme Entity Crusher", FakeHandler.class.getName(), handlerFailure);
        }

        PluginExportResult result = outcome.build(1, 0, 0);
        require(result.status == PluginExportResult.Status.PARTIAL, "EEC batch status is partial");
        require(result.counts.get("handlersFailed") == 1L, "EEC failed handler count");
        require(result.errors.get(0).message.contains("EEC row failed"), "EEC root cause reaches batch");
    }

    private static void assertMobsInfoInfernalReflectionFailurePropagatesToBatch() {
        NeiRecipeBatchOutcome outcome = new NeiRecipeBatchOutcome();
        try {
            NeiRecipeExportProcessor.exportRowsFailClosed(
                    "mobsinfo.mobhandlerinfernal",
                    1,
                    new NeiRecipeExportProcessor.RecipeRowExporter() {
                        @Override
                        public boolean export(int recipeIndex) {
                            NeiRecipeExportProcessor.readRequiredList(
                                    new BrokenInfernalRecipe(), "getOutputs", "all");
                            return true;
                        }
                    });
            throw new AssertionError("Expected infernal reflection failure");
        } catch (IllegalStateException handlerFailure) {
            require(handlerFailure.getMessage().contains("mobsinfo.mobhandlerinfernal"),
                    "infernal handler retained");
            require(handlerFailure.getCause() != null, "infernal reflection failure retained");
            outcome.recordFailure("Infernal Drops", BrokenInfernalRecipe.class.getName(), handlerFailure);
        }

        PluginExportResult result = outcome.build(1, 0, 0);
        require(result.status == PluginExportResult.Status.PARTIAL, "infernal batch status is partial");
        require(result.counts.get("handlersFailed") == 1L, "infernal failed handler count");
        require(result.errors.get(0).message.contains("infernal reflection failed"),
                "infernal reflection root cause reaches batch");
    }

    private static void assertItemUniverseFailurePropagatesToBatch() {
        NeiRecipeBatchOutcome outcome = new NeiRecipeBatchOutcome();
        outcome.recordItemUniverseResult(PluginExportResult.builder()
                .status(PluginExportResult.Status.PARTIAL)
                .count("itemUniverseEntriesFailed", 1)
                .error("nei-item-universe-normalization-failed", "copy failed", true)
                .build());

        PluginExportResult result = outcome.build(0, 0, 0);
        require(result.status == PluginExportResult.Status.PARTIAL, "universe failure reaches batch status");
        require(result.errors.get(0).code.equals("nei-item-universe-normalization-failed"),
                "universe error reaches batch result");
    }

    private static void fakeHandlerExport() {
        try {
            FakeHandler.exportRow();
        } catch (IllegalArgumentException rowFailure) {
            throw new IllegalStateException("fake handler failed", rowFailure);
        }
    }

    private static final class FakeHandler {
        private static void exportRow() {
            throw new IllegalArgumentException("fake row failed");
        }
    }

    public static final class BrokenInfernalRecipe {
        public java.util.List<Object> getOutputs() {
            throw new IllegalStateException("infernal reflection failed");
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("NEI batch outcome regression failed: " + label);
        }
    }
}
