package com.github.dcysteine.nesql.exporter.capture;

import bartworks.util.BWColorUtil;
import com.github.dcysteine.nesql.exporter.task.Jobs;

import java.util.Arrays;

/** Checks packed material color against the target's native channel correction. */
public final class ColorsTest {
    private ColorsTest() {}

    public static void run() {
        for (short value : new short[] {Short.MIN_VALUE, -256, -128, -1, 0, 1, 127, 254, 255, 256, 1024, Short.MAX_VALUE}) {
            for (int channel = 0; channel < 4; channel++) {
                short[] rgba = {16, 32, 64, 128};
                rgba[channel] = value;
                short[] original = rgba.clone();
                short[] nativeColor = BWColorUtil.correctCorlorArray(rgba.clone());
                long expected = ((long) nativeColor[3] << 24) | ((long) nativeColor[0] << 16)
                        | ((long) nativeColor[1] << 8) | nativeColor[2];
                require(Colors.material("fixture", rgba) == expected, "Material color differs from native correction");
                require(Colors.adjusted(rgba) == (value < 0 || value > 255), "Wrong color adjustment diagnostic");
                require(Arrays.equals(rgba, original), "Color capture changed the game material");
            }
        }
        require(Colors.material("white", new short[] {255, 255, 255, 255}) == 0xffffffffL, "ARGB became a signed value");
        require(Colors.material("default", new short[] {255, 255, 255, 0}) == 0x00ffffffL, "Existing alpha semantics changed");
        require(Colors.material("overflow", new short[] {300, -1, 511, 400}) == 0xffff00ffL, "Channels wrapped instead of clamping");
        for (short[] invalid : new short[][] {null, {}, {1, 2, 3}, {1, 2, 3, 4, 5}}) {
            try { Colors.material("BrokenMaterial", invalid); throw new AssertionError("Accepted malformed material color"); }
            catch (Jobs.Fault error) {
                require(error.code.equals("material_color") && error.getMessage().contains("BrokenMaterial"), "Missing material error context");
            }
        }
        System.out.println("Material colors: native clamping, signed limits, alpha, diagnostics and unchanged inputs passed");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
