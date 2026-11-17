package com.github.dcysteine.nesql.exporter.capture;

/** Focused contract for profile-specific capture stages. */
public final class CaptureProfileTest {
    private CaptureProfileTest() {}

    public static void main(String[] args) {
        run();
        System.out.println("Capture profiles: images skip ore groups while full/data retain them");
    }

    static void run() {
        require(!Capture.capturesOreGroups("images"), "images profile must skip ore groups");
        require(Capture.capturesOreGroups("full"), "full profile must capture ore groups");
        require(Capture.capturesOreGroups("data"), "data profile must capture ore groups");
        require(Capture.capturesOreGroups(null), "unknown/null profiles remain conservative");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
