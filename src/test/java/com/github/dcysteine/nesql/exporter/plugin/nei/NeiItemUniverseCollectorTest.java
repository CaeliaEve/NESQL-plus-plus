package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public final class NeiItemUniverseCollectorTest {
    private NeiItemUniverseCollectorTest() {}

    public static void main(String[] args) {
        assertNormalizationFailuresAreObservableAndPartial();
        assertAllNormalizationFailuresAreFailed();
        assertEmptyCoreUniverseIsFailed();
        assertOnlyMissingThaumcraftRootClassIsSkipped();
        assertInstalledThaumcraftFailuresAreFailed();
        assertInstalledThaumcraftPartialResultIsPreserved();
        assertFatalThaumcraftErrorsEscape();
    }


    private static void assertOnlyMissingThaumcraftRootClassIsSkipped() {
        NeiItemUniverse.ThaumcraftAugmentation absent = NeiItemUniverse.runThaumcraftAugmentation(
                Collections.<String, Object>emptyMap(),
                new NeiItemUniverse.ThaumcraftPresenceProbe() {
                    @Override
                    public Class<?> loadRootClass() throws ClassNotFoundException {
                        throw new ClassNotFoundException("Thaumcraft absent");
                    }
                },
                unreachableAugmenter());
        require(absent.result.status == PluginExportResult.Status.SKIPPED,
                "missing Thaumcraft root class is skipped");

        NeiItemUniverse.ThaumcraftAugmentation installedApiClassMissing =
                NeiItemUniverse.runThaumcraftAugmentation(
                        Collections.<String, Object>emptyMap(),
                        presentThaumcraft(),
                        new NeiItemUniverse.InstalledThaumcraftAugmenter() {
                            @Override
                            public NeiItemUniverse.ThaumcraftAugmentation augment(
                                    Class<?> configItemsClass,
                                    Map<String, ?> target) throws Exception {
                                throw new ClassNotFoundException("WandRod API missing");
                            }
                        });
        require(installedApiClassMissing.result.status == PluginExportResult.Status.FAILED,
                "installed Thaumcraft API class failure is failed");
    }

    private static void assertInstalledThaumcraftFailuresAreFailed() {
        assertInstalledThaumcraftFailure("API lookup failed");
        assertInstalledThaumcraftFailure("wand registry is empty");
        assertInstalledThaumcraftFailure("variant generation failed");
    }

    private static void assertInstalledThaumcraftFailure(final String message) {
        NeiItemUniverse.ThaumcraftAugmentation augmentation = NeiItemUniverse.runThaumcraftAugmentation(
                Collections.<String, Object>emptyMap(),
                presentThaumcraft(),
                new NeiItemUniverse.InstalledThaumcraftAugmenter() {
                    @Override
                    public NeiItemUniverse.ThaumcraftAugmentation augment(
                            Class<?> configItemsClass,
                            Map<String, ?> target) {
                        throw new IllegalStateException(message);
                    }
                });
        require(augmentation.result.status == PluginExportResult.Status.FAILED,
                "installed Thaumcraft failure is failed: " + message);
        require(augmentation.result.errors.get(0).message.contains(message),
                "installed Thaumcraft failure detail retained: " + message);
    }

    private static void assertInstalledThaumcraftPartialResultIsPreserved() {
        NeiItemUniverse.ThaumcraftAugmentation augmentation = NeiItemUniverse.runThaumcraftAugmentation(
                Collections.<String, Object>emptyMap(),
                presentThaumcraft(),
                new NeiItemUniverse.InstalledThaumcraftAugmenter() {
                    @Override
                    public NeiItemUniverse.ThaumcraftAugmentation augment(
                            Class<?> configItemsClass,
                            Map<String, ?> target) {
                        return new NeiItemUniverse.ThaumcraftAugmentation(
                                3,
                                PluginExportResult.builder()
                                        .status(PluginExportResult.Status.PARTIAL)
                                        .count("thaumcraftVariantsAdded", 3)
                                        .count("thaumcraftVariantsFailed", 1)
                                        .error("thaumcraft-wand-variant-failed", "one variant failed", true)
                                        .build());
                    }
                });
        require(augmentation.result.status == PluginExportResult.Status.PARTIAL,
                "installed Thaumcraft variant failure is partial");
        require(augmentation.added == 3, "successful Thaumcraft variants retained");
    }

    private static void assertFatalThaumcraftErrorsEscape() {
        try {
            NeiItemUniverse.runThaumcraftAugmentation(
                    Collections.<String, Object>emptyMap(),
                    presentThaumcraft(),
                    new NeiItemUniverse.InstalledThaumcraftAugmenter() {
                        @Override
                        public NeiItemUniverse.ThaumcraftAugmentation augment(
                                Class<?> configItemsClass,
                                Map<String, ?> target) throws Exception {
                            throw new java.lang.reflect.InvocationTargetException(
                                    new AssertionError("fatal Thaumcraft error"));
                        }
                    });
            throw new AssertionError("Expected fatal Thaumcraft error");
        } catch (AssertionError expected) {
            require("fatal Thaumcraft error".equals(expected.getMessage()),
                    "fatal Thaumcraft error is not swallowed");
        }
    }

    private static NeiItemUniverse.ThaumcraftPresenceProbe presentThaumcraft() {
        return new NeiItemUniverse.ThaumcraftPresenceProbe() {
            @Override
            public Class<?> loadRootClass() {
                return Object.class;
            }
        };
    }

    private static NeiItemUniverse.InstalledThaumcraftAugmenter unreachableAugmenter() {
        return new NeiItemUniverse.InstalledThaumcraftAugmenter() {
            @Override
            public NeiItemUniverse.ThaumcraftAugmentation augment(
                    Class<?> configItemsClass,
                    Map<String, ?> target) {
                throw new AssertionError("installed augmenter must not run when Thaumcraft is absent");
            }
        };
    }

    private static void assertEmptyCoreUniverseIsFailed() {
        NeiItemUniverseCollector.Result<String> result = NeiItemUniverseCollector.collect(
                Collections.<String>emptyList(),
                new NeiItemUniverseCollector.Normalizer<String, String>() {
                    @Override
                    public NeiItemUniverseCollector.Normalized<String> normalize(String entry) {
                        return new NeiItemUniverseCollector.Normalized<String>(entry, entry);
                    }
                });

        PluginExportResult exportResult = result.toPluginExportResult();
        require(exportResult.status == PluginExportResult.Status.FAILED, "empty core universe is failed");
        require(exportResult.errors.get(0).code.equals("nei-item-universe-empty"), "empty universe code");
    }

    private static void assertNormalizationFailuresAreObservableAndPartial() {
        NeiItemUniverseCollector.Result<String> result = NeiItemUniverseCollector.collect(
                Arrays.asList("valid", "copy-failure", "id-failure"),
                new NeiItemUniverseCollector.Normalizer<String, String>() {
                    @Override
                    public NeiItemUniverseCollector.Normalized<String> normalize(String entry) {
                        if ("copy-failure".equals(entry)) {
                            throw new IllegalStateException("copy failed");
                        }
                        if ("id-failure".equals(entry)) {
                            throw new IllegalArgumentException("id failed");
                        }
                        return new NeiItemUniverseCollector.Normalized<String>(entry, "item:" + entry);
                    }
                });

        require(result.values().size() == 1, "valid entry retained");
        require(result.failureCount() == 2, "normalization failures retained");
        PluginExportResult exportResult = result.toPluginExportResult();
        require(exportResult.status == PluginExportResult.Status.PARTIAL, "mixed universe is partial");
        require(exportResult.errors.size() == 2, "one error per failed core entry");
        require(exportResult.counts.get("itemUniverseEntriesTotal") == 3L, "total count");
        require(exportResult.counts.get("itemUniverseEntriesNormalized") == 1L, "normalized count");
        require(exportResult.counts.get("itemUniverseEntriesFailed") == 2L, "failed count");
    }

    private static void assertAllNormalizationFailuresAreFailed() {
        NeiItemUniverseCollector.Result<String> result = NeiItemUniverseCollector.collect(
                Arrays.asList("broken"),
                new NeiItemUniverseCollector.Normalizer<String, String>() {
                    @Override
                    public NeiItemUniverseCollector.Normalized<String> normalize(String entry) {
                        throw new IllegalStateException("copy failed");
                    }
                });

        PluginExportResult exportResult = result.toPluginExportResult();
        require(exportResult.status == PluginExportResult.Status.FAILED, "fully broken universe is failed");
        require(exportResult.errors.get(0).core, "universe normalization failure is core");
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("NEI item universe regression failed: " + label);
        }
    }
}
