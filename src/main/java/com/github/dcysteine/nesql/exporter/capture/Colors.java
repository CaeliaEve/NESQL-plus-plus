package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;

import java.util.Arrays;

/** Encodes GT material modulation using the target's clamped channel semantics. */
final class Colors {
    private Colors() {}

    static long material(String name, short[] rgba) {
        if (rgba == null || rgba.length != 4) throw new Jobs.Fault("material_color",
                "Material '" + name + "' requires four color channels: "
                        + (rgba == null ? "null" : Arrays.toString(Arrays.copyOf(rgba, Math.min(rgba.length, 8))) + " (length " + rgba.length + ")"));
        // BWColorUtil.correctCorlorArray and GT's floating-point modulation clamp
        // channels rather than interpreting negative shorts as unsigned bytes.
        return ((long) channel(rgba[3]) << 24) | ((long) channel(rgba[0]) << 16)
                | ((long) channel(rgba[1]) << 8) | channel(rgba[2]);
    }

    static boolean adjusted(short[] rgba) {
        for (short value : rgba) if (value < 0 || value > 255) return true;
        return false;
    }

    private static int channel(short value) { return Math.max(0, Math.min(255, value)); }
}
