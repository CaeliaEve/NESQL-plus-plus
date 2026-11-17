package com.github.dcysteine.nesql.exporter.capture;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Fixture-only resource guards must not carry approval across loader identities. */
public final class TargetClassLoaderTest {
    public static void main(String[] arguments) {
        SqueezerCallbackClasses guard = new SqueezerCallbackClasses();
        if (!guard.pinned(loader("abc"), "test.Callback")) throw new AssertionError("fixture resource should match");
        if (guard.pinned(loader("xyz"), "test.Callback")) throw new AssertionError("different loader reused approval");
        System.out.println("TargetClassLoaderTest: resource hash admission is scoped to actual loader identity");
    }
    private static ClassLoader loader(final String bytes) {
        return new ClassLoader() {
            @Override public InputStream getResourceAsStream(String name) {
                return "test/Callback.class".equals(name) ? new ByteArrayInputStream(bytes.getBytes(StandardCharsets.UTF_8)) : null;
            }
        };
    }
}
