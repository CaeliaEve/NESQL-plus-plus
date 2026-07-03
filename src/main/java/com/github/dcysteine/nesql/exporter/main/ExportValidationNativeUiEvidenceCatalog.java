package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Owns Native UI validation evidence projection and health policy. */
final class ExportValidationNativeUiEvidenceCatalog {
    private static final List<NativeUiRawCountDescriptor> RAW_COUNT_DESCRIPTORS =
            validateRawCountDescriptors(Arrays.asList(
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_LAYOUTS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiLayouts = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_SLOTS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiSlots = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_RECTS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiRects = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_PRIMITIVES,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiPrimitives = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_MISSING_SURFACES,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiMissingSurfaces = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_SLOT_BOUNDS_VIOLATIONS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiSlotBoundsViolations = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_RECT_BOUNDS_VIOLATIONS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiRectBoundsViolations = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_PRIMITIVE_BOUNDS_VIOLATIONS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiPrimitiveBoundsViolations = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_BACKGROUND_BOUNDS_VIOLATIONS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiBackgroundBoundsViolations = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_COORDINATE_CONTRACT_VIOLATIONS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiCoordinateContractViolations = value;
                                }
                            }),
                    rawCount(ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_INTERACTION_CONTRACT_VIOLATIONS,
                            new NativeUiRawCountWriter() {
                                @Override
                                public void write(ExportValidationReport report, long value) {
                                    report.nativeUiInteractionContractViolations = value;
                                }
                            })),
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_LAYOUTS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_SLOTS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_RECTS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_PRIMITIVES,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_MISSING_SURFACES,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_SLOT_BOUNDS_VIOLATIONS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_RECT_BOUNDS_VIOLATIONS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_PRIMITIVE_BOUNDS_VIOLATIONS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_BACKGROUND_BOUNDS_VIOLATIONS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_COORDINATE_CONTRACT_VIOLATIONS,
                    ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_INTERACTION_CONTRACT_VIOLATIONS);

    private ExportValidationNativeUiEvidenceCatalog() {}

    static void populateRawCounts(JsonObject counts, ExportValidationReport report) {
        if (counts == null) {
            throw new IllegalStateException("Native UI validation counts object must not be null");
        }
        if (report == null) {
            throw new IllegalStateException("Native UI validation report must not be null");
        }
        for (NativeUiRawCountDescriptor descriptor : RAW_COUNT_DESCRIPTORS) {
            descriptor.writer.write(
                    report,
                    ExportValidationJsonSupport.readLongMember(counts, descriptor.rawCountKey));
        }
    }

    static void collectWarnings(ExportValidationReport report) {
        if (report.nativeUiMissingSurfaces > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiMissingSurfacesWarning(
                    report.nativeUiMissingSurfaces));
        }
        if (hasGeometryBoundsViolations(report)) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiGeometryBoundsWarning(
                    report.nativeUiSlotBoundsViolations,
                    report.nativeUiRectBoundsViolations,
                    report.nativeUiPrimitiveBoundsViolations,
                    report.nativeUiBackgroundBoundsViolations));
        }
        if (report.nativeUiCoordinateContractViolations > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiCoordinateContractWarning(
                    report.nativeUiCoordinateContractViolations));
        }
        if (report.nativeUiInteractionContractViolations > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiInteractionContractWarning(
                    report.nativeUiInteractionContractViolations));
        }
    }

    static boolean isAbiBlocked(ExportValidationReport report) {
        return report.nativeUiLayouts > 0L
                && (report.nativeUiSlots == 0L
                        || report.nativeUiMissingSurfaces > 0L
                        || report.nativeUiSlotBoundsViolations > 0L
                        || report.nativeUiRectBoundsViolations > 0L
                        || report.nativeUiPrimitiveBoundsViolations > 0L
                        || report.nativeUiBackgroundBoundsViolations > 0L
                        || report.nativeUiCoordinateContractViolations > 0L
                        || report.nativeUiInteractionContractViolations > 0L);
    }

    private static boolean hasGeometryBoundsViolations(ExportValidationReport report) {
        return report.nativeUiSlotBoundsViolations > 0L
                || report.nativeUiRectBoundsViolations > 0L
                || report.nativeUiPrimitiveBoundsViolations > 0L
                || report.nativeUiBackgroundBoundsViolations > 0L;
    }

    private static List<NativeUiRawCountDescriptor> validateRawCountDescriptors(
            List<NativeUiRawCountDescriptor> descriptors,
            String... expectedKeys) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Native UI raw count descriptor catalog must not be empty");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedKeys));
        Set<String> seen = new LinkedHashSet<String>();
        List<NativeUiRawCountDescriptor> validated = new ArrayList<NativeUiRawCountDescriptor>();
        for (NativeUiRawCountDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Native UI raw count descriptor must not be null");
            }
            if (descriptor.rawCountKey == null || descriptor.rawCountKey.trim().isEmpty()) {
                throw new IllegalStateException("Native UI raw count key must be non-empty");
            }
            if (!expected.contains(descriptor.rawCountKey)) {
                throw new IllegalStateException(
                        "Unknown Native UI raw count descriptor: " + descriptor.rawCountKey);
            }
            if (!seen.add(descriptor.rawCountKey)) {
                throw new IllegalStateException(
                        "Duplicate Native UI raw count descriptor: " + descriptor.rawCountKey);
            }
            if (descriptor.writer == null) {
                throw new IllegalStateException(
                        "Native UI raw count writer must not be null: " + descriptor.rawCountKey);
            }
            validated.add(descriptor);
        }
        for (String expectedKey : expected) {
            if (!seen.contains(expectedKey)) {
                throw new IllegalStateException(
                        "Missing Native UI raw count descriptor: " + expectedKey);
            }
        }
        return Collections.unmodifiableList(validated);
    }

    private static NativeUiRawCountDescriptor rawCount(
            String rawCountKey,
            NativeUiRawCountWriter writer) {
        return new NativeUiRawCountDescriptor(rawCountKey, writer);
    }

    private interface NativeUiRawCountWriter {
        void write(ExportValidationReport report, long value);
    }

    private static final class NativeUiRawCountDescriptor {
        private final String rawCountKey;
        private final NativeUiRawCountWriter writer;

        private NativeUiRawCountDescriptor(String rawCountKey, NativeUiRawCountWriter writer) {
            this.rawCountKey = rawCountKey;
            this.writer = writer;
        }
    }
}
