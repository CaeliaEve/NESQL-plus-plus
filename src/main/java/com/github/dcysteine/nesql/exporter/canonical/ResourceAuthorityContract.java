package com.github.dcysteine.nesql.exporter.canonical;

/** Stable status vocabulary and count invariants for authoritative resource facts. */
public final class ResourceAuthorityContract {
    public static final String FACADE_RESOLVED = "resolved";
    public static final String FACADE_PARTIAL = "partial";
    public static final String FACADE_UNRESOLVED = "unresolved";

    public static final String ANIMATION_MATERIALIZED = "materialized";
    public static final String ANIMATION_STATIC = "static";
    public static final String ANIMATION_UNAVAILABLE = "unavailable";

    private ResourceAuthorityContract() {}

    public static String facadeStatus(int targetCount, int resolvedTargetCount) {
        requireNonNegative("facade target count", targetCount);
        requireNonNegative("resolved facade target count", resolvedTargetCount);
        if (resolvedTargetCount > targetCount) {
            throw new IllegalArgumentException(
                    "Resolved facade target count must not exceed target count");
        }
        if (targetCount > 0 && resolvedTargetCount == targetCount) {
            return FACADE_RESOLVED;
        }
        if (resolvedTargetCount > 0) {
            return FACADE_PARTIAL;
        }
        return FACADE_UNRESOLVED;
    }

    public static String animationStatus(int materializedFrameCount, int distinctFrameCount) {
        requireNonNegative("materialized frame count", materializedFrameCount);
        requireNonNegative("distinct frame count", distinctFrameCount);
        if (distinctFrameCount > materializedFrameCount) {
            throw new IllegalArgumentException(
                    "Distinct frame count must not exceed materialized frame count");
        }
        if (materializedFrameCount >= 2 && distinctFrameCount >= 2) {
            return ANIMATION_MATERIALIZED;
        }
        if (materializedFrameCount >= 1 && distinctFrameCount == 1) {
            return ANIMATION_STATIC;
        }
        if (materializedFrameCount == 0 && distinctFrameCount == 0) {
            return ANIMATION_UNAVAILABLE;
        }
        throw new IllegalArgumentException(
                "Animation materialization counts do not match a valid status");
    }

    public static boolean isNativeSpriteAnimation(String materializationStatus) {
        return ANIMATION_MATERIALIZED.equals(materializationStatus);
    }

    public static String canonicalItemId(String rawItemId) {
        if (rawItemId == null || rawItemId.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw item ID must be non-empty");
        }
        String normalized = rawItemId.trim();
        return normalized.startsWith("i~") ? normalized : "i~" + normalized;
    }

    public static String canonicalItemAssetId(String rawItemId) {
        return "nesqlpp:item/" + canonicalItemId(rawItemId);
    }

    private static void requireNonNegative(String label, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
    }
}
