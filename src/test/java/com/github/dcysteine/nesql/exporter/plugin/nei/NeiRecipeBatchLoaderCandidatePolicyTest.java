package com.github.dcysteine.nesql.exporter.plugin.nei;

public final class NeiRecipeBatchLoaderCandidatePolicyTest {
    private static final String FLUID_CANNER =
            "ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler";

    private NeiRecipeBatchLoaderCandidatePolicyTest() {}

    public static void main(String[] args) {
        Throwable expected = new IllegalStateException(
                "handler wrapper",
                new IllegalArgumentException("Cannot create a fluidstack from a null fluid"));
        require(NeiRecipeBatchLoader.isUnsupportedCandidateFailure(FLUID_CANNER, expected),
                "Fluid Canner null-fluid candidate is rejected at candidate scope");
        require(!NeiRecipeBatchLoader.isUnsupportedCandidateFailure(
                        "another.Handler", expected),
                "the same exception from another handler remains fail-closed");
        require(!NeiRecipeBatchLoader.isUnsupportedCandidateFailure(
                        FLUID_CANNER, new IllegalArgumentException("different bug")),
                "unrelated Fluid Canner failures remain fail-closed");
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("NEI candidate policy regression failed: " + label);
        }
    }
}
