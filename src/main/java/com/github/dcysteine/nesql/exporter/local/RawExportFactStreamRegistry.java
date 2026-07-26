package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class RawExportFactStreamRegistry {
    private static final String CAPABILITIES_EMPTY_MESSAGE =
            "Raw export fact stream provider capabilities must be non-empty";
    private static final String OUTPUT_FAMILIES_EMPTY_MESSAGE =
            "Raw export fact stream provider output families must be non-empty";

    private static final List<ProviderDescriptor> PROVIDER_DESCRIPTORS = validateDescriptorCatalog(Arrays.asList(
            providerDescriptor(
                    "raw.repository-facts",
                    RawRepositoryFactStreamProvider::new,
                    list("raw.items", "raw.fluids", "raw.recipes"),
                    list("facts/items", "facts/fluids", "facts/recipes")),
            providerDescriptor(
                    "raw.nei-facts",
                    RawNeiFactStreamProvider::new,
                    list("raw.nei.browser-order", "raw.nei.handler-metadata", "raw.native-ui.handler-layouts"),
                    list("facts/nei", "facts/native-ui", "validation/nei-browser-contract")),
            providerDescriptor(
                    "raw.render-asset-facts",
                    RawRenderAssetFactStreamProvider::new,
                    list(
                            "raw.render.textures",
                            "raw.render.facade-resolutions",
                            "raw.render.animations",
                            "raw.render.animation-frame-materializations",
                            "raw.render.browser-atlas-assets"),
                    list(
                            "facts/render-assets",
                            "assets/textures/facade-resolutions",
                            "assets/animations/frame-materializations",
                            "assets/browser-atlas")),
            providerDescriptor(
                    "raw.entity-render-backend-facts",
                    RawEntityAndRenderBackendFactStreamProvider::new,
                    list("raw.entities", "raw.render.angelica-backend", "raw.render.framebuffer-captures"),
                    list("models/entities", "models/multiblocks", "facts/render-backend"))));

    private static final RawExportFactStreamCatalog DEFAULT_CATALOG =
            instantiateAndFreeze(PROVIDER_DESCRIPTORS);

    private RawExportFactStreamRegistry() {}

    static RawExportFactStreamCatalog defaultCatalog() {
        return DEFAULT_CATALOG;
    }

    static List<RawExportFactStreamProvider> defaultProviders() {
        return DEFAULT_CATALOG.providers();
    }

    static List<RawExportFactStreamDescriptor> defaultDescriptors() {
        return DEFAULT_CATALOG.descriptors();
    }

    private static RawExportFactStreamCatalog instantiateAndFreeze(
            List<ProviderDescriptor> descriptors) {
        List<RawExportFactStreamProvider> providers = new ArrayList<RawExportFactStreamProvider>();
        List<RawExportFactStreamDescriptor> reports = new ArrayList<RawExportFactStreamDescriptor>();
        for (ProviderDescriptor descriptor : descriptors) {
            RawExportFactStreamProvider provider = descriptor.factory.build();
            validateProvider(provider, descriptor.id);
            providers.add(provider);
            reports.add(descriptor.toReportDescriptor());
        }
        return new RawExportFactStreamCatalog(providers, reports);
    }

    private static List<ProviderDescriptor> validateDescriptorCatalog(List<ProviderDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Raw export fact stream provider descriptor catalog must not be empty");
        }
        Set<String> ids = new LinkedHashSet<String>();
        List<ProviderDescriptor> validated = new ArrayList<ProviderDescriptor>();
        for (ProviderDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Raw export fact stream provider descriptor must be non-null");
            }
            if (descriptor.id == null || descriptor.id.trim().isEmpty()) {
                throw new IllegalStateException("Raw export fact stream provider descriptor id must be non-empty");
            }
            if (descriptor.factory == null) {
                throw new IllegalStateException(
                        "Raw export fact stream provider factory must be non-null: " + descriptor.id);
            }
            if (!ids.add(descriptor.id)) {
                throw new IllegalStateException(
                        "Duplicate raw export fact stream provider descriptor id: " + descriptor.id);
            }
            validateNonEmptyStringList(
                    "Raw export fact stream provider capabilities",
                    CAPABILITIES_EMPTY_MESSAGE,
                    descriptor.id,
                    descriptor.capabilities);
            validateNonEmptyStringList(
                    "Raw export fact stream provider output families",
                    OUTPUT_FAMILIES_EMPTY_MESSAGE,
                    descriptor.id,
                    descriptor.outputFamilies);
            validated.add(descriptor);
        }
        return Collections.unmodifiableList(validated);
    }

    private static void validateProvider(RawExportFactStreamProvider provider, String descriptorId) {
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Raw export fact stream provider factory returned null: " + descriptorId);
        }
    }

    private static void validateNonEmptyStringList(
            String label,
            String emptyMessage,
            String providerId,
            List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage + ": " + providerId);
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(label + " entry must be non-empty: " + providerId);
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(
                        "Duplicate " + label.toLowerCase() + ": " + providerId + ":" + value);
            }
        }
    }

    private static ProviderDescriptor providerDescriptor(
            String id,
            ProviderFactory factory,
            List<String> capabilities,
            List<String> outputFamilies) {
        return new ProviderDescriptor(id, factory, capabilities, outputFamilies);
    }

    private static List<String> list(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private interface ProviderFactory {
        RawExportFactStreamProvider build();
    }

    private static final class ProviderDescriptor {
        private final String id;
        private final ProviderFactory factory;
        private final List<String> capabilities;
        private final List<String> outputFamilies;

        private ProviderDescriptor(
                String id,
                ProviderFactory factory,
                List<String> capabilities,
                List<String> outputFamilies) {
            this.id = id;
            this.factory = factory;
            this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities));
            this.outputFamilies = Collections.unmodifiableList(new ArrayList<String>(outputFamilies));
        }

        private RawExportFactStreamDescriptor toReportDescriptor() {
            return new RawExportFactStreamDescriptor(id, capabilities, outputFamilies);
        }
    }
}
