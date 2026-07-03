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
            providerDescriptor("raw.repository-facts", RawRepositoryFactStreamProvider::new),
            providerDescriptor("raw.nei-facts", RawNeiFactStreamProvider::new),
            providerDescriptor("raw.render-asset-facts", RawRenderAssetFactStreamProvider::new),
            providerDescriptor("raw.entity-render-backend-facts", RawEntityAndRenderBackendFactStreamProvider::new)));

    private static final List<RawExportFactStreamProvider> DEFAULT_PROVIDERS =
            instantiateAndFreeze(PROVIDER_DESCRIPTORS);

    private RawExportFactStreamRegistry() {}

    static List<RawExportFactStreamProvider> defaultProviders() {
        return DEFAULT_PROVIDERS;
    }

    static List<RawExportFactStreamDescriptor> describe(List<RawExportFactStreamProvider> providers) {
        List<RawExportFactStreamDescriptor> descriptors = new ArrayList<RawExportFactStreamDescriptor>();
        for (RawExportFactStreamProvider provider : validateAndFreeze(providers)) {
            descriptors.add(new RawExportFactStreamDescriptor(
                    provider.id(),
                    provider.capabilities(),
                    provider.outputFamilies()));
        }
        return Collections.unmodifiableList(descriptors);
    }

    private static List<RawExportFactStreamProvider> instantiateAndFreeze(
            List<ProviderDescriptor> descriptors) {
        List<RawExportFactStreamProvider> providers = new ArrayList<RawExportFactStreamProvider>();
        for (ProviderDescriptor descriptor : descriptors) {
            RawExportFactStreamProvider provider = descriptor.factory.build();
            validateProvider(provider, descriptor.id);
            providers.add(provider);
        }
        return Collections.unmodifiableList(providers);
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
            validated.add(descriptor);
        }
        return Collections.unmodifiableList(validated);
    }

    private static List<RawExportFactStreamProvider> validateAndFreeze(List<RawExportFactStreamProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Raw export fact stream provider catalog must not be empty");
        }
        Set<String> ids = new LinkedHashSet<String>();
        List<RawExportFactStreamProvider> validated = new ArrayList<RawExportFactStreamProvider>();
        for (RawExportFactStreamProvider provider : providers) {
            String id = validateProvider(provider, null);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate raw export fact stream provider id: " + id);
            }
            validated.add(provider);
        }
        return Collections.unmodifiableList(validated);
    }

    private static String validateProvider(RawExportFactStreamProvider provider, String expectedId) {
        if (provider == null) {
            throw new IllegalArgumentException("Raw export fact stream provider must be non-null");
        }
        String id = provider.id();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw export fact stream provider id must be non-empty");
        }
        if (expectedId != null && !expectedId.equals(id)) {
            throw new IllegalStateException(
                    "Raw export fact stream provider id does not match descriptor: "
                            + expectedId
                            + " != "
                            + id);
        }
        validateNonEmptyStringList(
                "Raw export fact stream provider capabilities",
                CAPABILITIES_EMPTY_MESSAGE,
                id,
                provider.capabilities());
        validateNonEmptyStringList(
                "Raw export fact stream provider output families",
                OUTPUT_FAMILIES_EMPTY_MESSAGE,
                id,
                provider.outputFamilies());
        return id;
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

    private static ProviderDescriptor providerDescriptor(String id, ProviderFactory factory) {
        return new ProviderDescriptor(id, factory);
    }

    private interface ProviderFactory {
        RawExportFactStreamProvider build();
    }

    private static final class ProviderDescriptor {
        private final String id;
        private final ProviderFactory factory;

        private ProviderDescriptor(String id, ProviderFactory factory) {
            this.id = id;
            this.factory = factory;
        }
    }
}
