package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;
import com.google.gson.Gson;

import java.io.IOException;

/** Behavioral contract coverage for raw Native UI capture identity v2. */
public final class RawExportUiCaptureKeyContractTest {
    private static final Gson GSON = new Gson();

    private RawExportUiCaptureKeyContractTest() {}

    public static void main(String[] args) throws Exception {
        runAll();
    }

    static void runAll() throws Exception {
        assertCaptureKeyIsDeterministicAndFieldSensitive();
        assertTemplateSerializationUsesCaptureKeyOnly();
        assertMissingBlankAndLegacyOnlyKeysAreRejected();
        assertWrongCensusSchemaIsRejected();
    }

    private static void assertCaptureKeyIsDeterministicAndFieldSensitive() {
        String baseline = captureKey("native-nei", "native-nei", 166, 65, 0, 1, "nei:textures/gui.png");
        assertEquals(
                "same capture descriptor produces the same key",
                baseline,
                captureKey("native-nei", "native-nei", 166, 65, 0, 1, "nei:textures/gui.png"));
        assertNotEquals("family changes captureKey", baseline,
                captureKey("crafting-table", "native-nei", 166, 65, 0, 1, "nei:textures/gui.png"));
        assertNotEquals("layout changes captureKey", baseline,
                captureKey("native-nei", "crafting-grid", 166, 65, 0, 1, "nei:textures/gui.png"));
        assertNotEquals("width changes captureKey", baseline,
                captureKey("native-nei", "native-nei", 167, 65, 0, 1, "nei:textures/gui.png"));
        assertNotEquals("height changes captureKey", baseline,
                captureKey("native-nei", "native-nei", 166, 66, 0, 1, "nei:textures/gui.png"));
        assertNotEquals("yShift changes captureKey", baseline,
                captureKey("native-nei", "native-nei", 166, 65, 1, 1, "nei:textures/gui.png"));
        assertNotEquals("page size changes captureKey", baseline,
                captureKey("native-nei", "native-nei", 166, 65, 0, 2, "nei:textures/gui.png"));
        assertNotEquals("resource changes captureKey", baseline,
                captureKey("native-nei", "native-nei", 166, 65, 0, 1, "nei:textures/other.png"));
    }

    private static void assertTemplateSerializationUsesCaptureKeyOnly() throws Exception {
        RawExportUiFamilyCensusWriter.UiFamilyBucket family = validFamily();
        RawExportUiTemplateCatalogWriter.UiTemplateBucket template =
                RawExportUiTemplateCatalogWriter.buildTemplateBucket(family);
        String json = GSON.toJson(template);
        assertContains("serialized template exposes captureKey", json, "\"captureKey\":");
        assertNotContains("serialized template must not expose legacy familyKey", json, "\"familyKey\":");
        assertEquals("template preserves exact captureKey", family.captureKey, template.captureKey);
    }

    private static void assertMissingBlankAndLegacyOnlyKeysAreRejected() throws Exception {
        assertIOException("missing captureKey", new IoAction() {
            @Override
            public void run() throws Exception {
                RawExportUiTemplateCatalogWriter.buildTemplateBucket(validFamilyWithCaptureKey(null));
            }
        });
        assertIOException("blank captureKey", new IoAction() {
            @Override
            public void run() throws Exception {
                RawExportUiTemplateCatalogWriter.buildTemplateBucket(validFamilyWithCaptureKey("   "));
            }
        });
        final RawExportUiFamilyCensusWriter.UiFamilyBucket legacyOnly = GSON.fromJson(
                "{\"familyKey\":\"legacy/fallback-must-not-activate\","
                        + "\"canonicalMachineFamily\":\"native-nei\","
                        + "\"layoutKind\":\"native-nei\"}",
                RawExportUiFamilyCensusWriter.UiFamilyBucket.class);
        assertIOException("legacy-only familyKey", new IoAction() {
            @Override
            public void run() throws Exception {
                RawExportUiTemplateCatalogWriter.buildTemplateBucket(legacyOnly);
            }
        });
    }

    private static void assertWrongCensusSchemaIsRejected() throws Exception {
        RawExportUiTemplateCatalogWriter.validateCensusSchemaVersion(
                NativeUiExportAbi.UI_FAMILY_CENSUS_SCHEMA);
        assertIOException("missing census schema", new IoAction() {
            @Override
            public void run() throws Exception {
                RawExportUiTemplateCatalogWriter.validateCensusSchemaVersion(null);
            }
        });
        assertIOException("legacy census schema", new IoAction() {
            @Override
            public void run() throws Exception {
                RawExportUiTemplateCatalogWriter.validateCensusSchemaVersion(
                        NativeUiExportAbi.RAW_EXPORT_SCHEMA + "/ui-family-census");
            }
        });
        assertIOException("unrelated census schema", new IoAction() {
            @Override
            public void run() throws Exception {
                RawExportUiTemplateCatalogWriter.validateCensusSchemaVersion("wrong/schema/v2");
            }
        });
    }

    private static RawExportUiFamilyCensusWriter.UiFamilyBucket validFamily() {
        return validFamilyWithCaptureKey(
                captureKey("native-nei", "native-nei", 166, 65, 0, 1, "nei:textures/gui.png"));
    }

    private static RawExportUiFamilyCensusWriter.UiFamilyBucket validFamilyWithCaptureKey(String captureKey) {
        RawExportUiFamilyCensusWriter.UiFamilyBucket family =
                new RawExportUiFamilyCensusWriter.UiFamilyBucket();
        family.captureKey = captureKey;
        family.canonicalMachineFamily = "native-nei";
        family.layoutKind = "native-nei";
        family.width = 166;
        family.height = 65;
        family.maxRecipesPerPage = 1;
        family.imageResource = "nei:textures/gui.png";
        return family;
    }

    private static String captureKey(
            String family,
            String layoutKind,
            int width,
            int height,
            int yShift,
            int maxRecipesPerPage,
            String imageResource) {
        return RawExportUiFamilyCensusWriter.buildCaptureKey(
                family, layoutKind, width, height, yShift, maxRecipesPerPage, imageResource);
    }

    private static void assertIOException(String label, IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + ": expected IOException");
        } catch (IOException expected) {
            // Expected fail-closed path.
        }
    }

    private static void assertContains(String label, String value, String expected) {
        if (!value.contains(expected)) {
            throw new AssertionError(label + ": missing " + expected + " in " + value);
        }
    }

    private static void assertNotContains(String label, String value, String forbidden) {
        if (value.contains(forbidden)) {
            throw new AssertionError(label + ": found " + forbidden + " in " + value);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertNotEquals(String label, Object left, Object right) {
        if (left.equals(right)) {
            throw new AssertionError(label + ": both=" + left);
        }
    }

    private interface IoAction {
        void run() throws Exception;
    }
}
