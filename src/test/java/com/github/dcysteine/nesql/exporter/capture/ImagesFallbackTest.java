package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Classification checks for the conservative images-only native fallback. */
public final class ImagesFallbackTest {
    private ImagesFallbackTest() {}

    public static void main(String[] args) {
        run();
        System.out.println("Images fallback: audited refusal and animation fault classification passed");
    }

    public static void run() {
        require(Images.fallbackFault(new Jobs.Fault("target_artifact_unverified", "fixture")), "artifact audit refusal");
        require(Images.fallbackFault(new Jobs.Fault("target_family_unverified", "fixture")), "family audit refusal");
        require(Images.fallbackFault(new Jobs.Fault("animation_unsupported", "fixture")), "unsupported animation");
        require(Images.fallbackFault(new RuntimeException("outer", new Jobs.Fault("animation_changed", "fixture"))), "nested changed animation");
        require(Images.missingVisualFault(new Jobs.Fault("texture_missing", "fixture")), "missing texture");
        require(Images.missingVisualFault(new RuntimeException("outer", new Jobs.Fault("texture_missing", "fixture"))), "nested missing texture");
        require(!Images.missingVisualFault(new Jobs.Fault("texture_invalid", "fixture")), "invalid texture is not missing");
        require(!Images.fallbackFault(new Jobs.Fault("texture_invalid", "fixture")), "invalid texture must fail");
        require(!Images.fallbackFault(new Jobs.Fault("texture_limit", "fixture")), "texture limit must fail");
        require(!Images.fallbackFault(new Jobs.Fault("animation_invalid", "fixture")), "invalid animation must fail");
        require(!Images.fallbackFault(new Jobs.Fault("animation_limit", "fixture")), "animation limit must fail");
        NativeAuthorities authorities = new NativeAuthorities();
        Object owner = new Object();
        NativeAuthorities.Scope scope = authorities.observe();
        authorities.remember(owner, "temporary", () -> {});
        scope.rollback();
        scope.close();
        require(authorities.seal().size() == 0, "fallback rollback left native authority");
        authorities.close();
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
