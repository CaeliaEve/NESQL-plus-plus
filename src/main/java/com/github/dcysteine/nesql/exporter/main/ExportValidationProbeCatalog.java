package com.github.dcysteine.nesql.exporter.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Owns validation probe ordering, identity checks, and probe capability descriptors. */
final class ExportValidationProbeCatalog {
    private static final List<ProbeCatalogDescriptor> DEFAULT_PROBE_DESCRIPTORS =
            validateProbeDescriptors(Arrays.asList(
                    probeDescriptor(
                            "validation.repository-summary",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationRepositoryProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.raw-counts",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationRawCountsValidationProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.browser-layout",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationBrowserValidationProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.render-assets",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationRenderAssetValidationProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.semantic",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationSemanticValidationProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.path-hygiene",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationPathValidationProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.derived-metrics",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationDerivedMetricsProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.previous-delta",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationPreviousDeltaProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.health-policy",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationHealthPolicyProbe();
                                }
                            }),
                    probeDescriptor(
                            "validation.health-sections",
                            new ProbeFactory() {
                                @Override
                                public ExportValidationProbe build() {
                                    return new ExportValidationHealthSectionProbe();
                                }
                            })),
            "validation.repository-summary",
            "validation.raw-counts",
            "validation.browser-layout",
            "validation.render-assets",
            "validation.semantic",
            "validation.path-hygiene",
            "validation.derived-metrics",
            "validation.previous-delta",
            "validation.health-policy",
            "validation.health-sections");

    private static final List<ExportValidationProbe> DEFAULT_PROBES =
            instantiateAndFreeze(DEFAULT_PROBE_DESCRIPTORS);

    private ExportValidationProbeCatalog() {}

    static List<ExportValidationProbe> defaultProbes() {
        return DEFAULT_PROBES;
    }

    static List<ExportValidationProbeDescriptor> descriptors() {
        List<ExportValidationProbeDescriptor> descriptors = new ArrayList<ExportValidationProbeDescriptor>();
        for (ExportValidationProbe probe : DEFAULT_PROBES) {
            descriptors.add(new ExportValidationProbeDescriptor(probe));
        }
        return Collections.unmodifiableList(descriptors);
    }

    private static List<ExportValidationProbe> instantiateAndFreeze(
            List<ProbeCatalogDescriptor> descriptors) {
        Set<String> ids = new LinkedHashSet<String>();
        List<ExportValidationProbe> probes = new ArrayList<ExportValidationProbe>();
        for (ProbeCatalogDescriptor descriptor : descriptors) {
            ExportValidationProbe probe = descriptor.factory.build();
            if (probe == null) {
                throw new IllegalStateException(
                        "Export validation probe factory returned null: " + descriptor.id);
            }
            String id = probe.id();
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalStateException("Export validation probe id must be non-empty");
            }
            if (!descriptor.id.equals(id)) {
                throw new IllegalStateException(
                        "Export validation probe id does not match descriptor: "
                                + descriptor.id
                                + " != "
                                + id);
            }
            if (!ids.add(id)) {
                throw new IllegalStateException("Duplicate export validation probe id: " + id);
            }
            validateProbeCapabilities(probe);
            probes.add(probe);
        }
        return Collections.unmodifiableList(probes);
    }

    private static List<ProbeCatalogDescriptor> validateProbeDescriptors(
            List<ProbeCatalogDescriptor> descriptors,
            String... expectedIds) {
        if (descriptors == null) {
            throw new IllegalStateException("Export validation probe descriptors must not be null");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedIds));
        Set<String> seen = new LinkedHashSet<String>();
        for (ProbeCatalogDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Export validation probe descriptor must not be null");
            }
            if (descriptor.id == null || descriptor.id.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Export validation probe descriptor id must be non-empty");
            }
            if (!expected.contains(descriptor.id)) {
                throw new IllegalStateException(
                        "Unknown export validation probe descriptor: " + descriptor.id);
            }
            if (!seen.add(descriptor.id)) {
                throw new IllegalStateException(
                        "Duplicate export validation probe descriptor: " + descriptor.id);
            }
            if (descriptor.factory == null) {
                throw new IllegalStateException(
                        "Export validation probe factory must not be null: " + descriptor.id);
            }
        }
        for (String id : expected) {
            if (!seen.contains(id)) {
                throw new IllegalStateException("Missing export validation probe descriptor: " + id);
            }
        }
        return Collections.unmodifiableList(new ArrayList<ProbeCatalogDescriptor>(descriptors));
    }

    private static void validateProbeCapabilities(ExportValidationProbe probe) {
        List<String> capabilities = probe.capabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalStateException(
                    "Export validation probe capabilities must be non-empty: " + probe.id());
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (String capability : capabilities) {
            if (capability == null || capability.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Export validation probe capability must be non-empty: " + probe.id());
            }
            if (!seen.add(capability)) {
                throw new IllegalStateException(
                        "Duplicate export validation probe capability: "
                                + probe.id()
                                + ":"
                                + capability);
            }
        }
    }

    private static ProbeCatalogDescriptor probeDescriptor(String id, ProbeFactory factory) {
        return new ProbeCatalogDescriptor(id, factory);
    }

    private interface ProbeFactory {
        ExportValidationProbe build();
    }

    private static final class ProbeCatalogDescriptor {
        final String id;
        final ProbeFactory factory;

        ProbeCatalogDescriptor(String id, ProbeFactory factory) {
            this.id = id;
            this.factory = factory;
        }
    }
}
