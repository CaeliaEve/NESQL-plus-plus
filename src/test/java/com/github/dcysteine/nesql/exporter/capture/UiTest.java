package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtnewhorizons.modularui.common.widget.ProgressBar;
import com.gtnewhorizons.modularui.config.Config;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.util.ArrayList;
import java.util.List;

import static com.github.dcysteine.nesql.exporter.source.Json.array;

/** Native progress functions are checked without a GUI or OpenGL context. */
public final class UiTest {
    private UiTest() {}

    public static void run() {
        windows();
        JsonArray flame = Ui.progress(14, 14, 48, 7), arrow = Ui.progress(24, 16, 48, 0);
        require(Ui.progress(14, 14, 100, 11).equals(Ui.progress(14, 14, 100, 7)),
                "NEI direction 11 must retain its native reversed direction-3 alias");
        require(duration(flame) == 48 && state(flame, 0).equals(array(array("0.0", "0.0", "1.0", "1.0")))
                && state(flame, 47).size() == 0, "Native furnace flame did not shrink to empty");
        require(state(arrow, 0).size() == 0 && state(arrow, 24).equals(array(array("0.0", "0.0", "0.5", "1.0"))), "Native furnace arrow has the wrong pixel step");
        for(int direction=0;direction<8;direction++) {
            JsonArray longCycle=Ui.progress(43,18,72000,direction);
            require(duration(longCycle)==72000&&longCycle.size()<=44,"Long native progress was truncated or expanded per tick");
            int at=0;
            for(JsonElement frame:longCycle) {
                int span=frame.getAsJsonObject().get("ticks").getAsInt();
                for(int tick:new int[]{at,at+span-1}) {
                    float p=tick/72000f;if(direction>3)p=1-p;
                    int size=direction%2==0?43:18, pixels=(int)(p*size);
                    JsonArray areas=state(longCycle,tick);
                    if(pixels==0)require(areas.size()==0,"Native empty progress has pixels");
                    else {
                        JsonArray rect=areas.get(0).getAsJsonArray();
                        float extent=direction%2==0?rect.get(2).getAsFloat()-rect.get(0).getAsFloat():rect.get(3).getAsFloat()-rect.get(1).getAsFloat();
                        require(Math.abs(extent*size-pixels)<.0001f,"Long progress changed native float rounding");
                    }
                }
                at+=span;
            }
        }
        for(int invalid:new int[]{0,-5,72001})try{Ui.progress(43,18,invalid,0);throw new AssertionError("Unrepresentable native period accepted");}
            catch(Jobs.Fault expected){require(expected.code.equals("view_unsupported"),"Wrong invalid-period error");}
        boolean smooth = Config.smoothProgressbar;
        float[] time = {.3f};
        try {
            Config.smoothProgressbar = false;
            ProgressBar bar = new ProgressBar().setProgress(() -> time[0]).setTexture(null, null, 20);
            bar.setSynced(false, false);
            JsonArray right = Ui.motion(bar, time);
            require(right.size() == 20 && duration(right) == 200 && time[0] == .3f, "Native progress was not compressed or its clock was changed");
            require(state(right, 0).size() == 0, "Empty progress acquired a visible region");
            require(state(right, 50).equals(array(array("0.0", "0.0", "0.25", "1.0"))), "Right progress crop is incorrect");
            bar.setDirection(ProgressBar.Direction.LEFT);
            require(state(Ui.motion(bar, time), 50).equals(array(array("0.75", "0.0", "1.0", "1.0"))), "Left progress crop is incorrect");
            bar.setDirection(ProgressBar.Direction.UP);
            require(state(Ui.motion(bar, time), 50).equals(array(array("0.0", "0.75", "1.0", "1.0"))), "Up progress crop is incorrect");
            bar.setDirection(ProgressBar.Direction.DOWN);
            require(state(Ui.motion(bar, time), 50).equals(array(array("0.0", "0.0", "1.0", "0.25"))), "Down progress crop is incorrect");
            bar.setDirection(ProgressBar.Direction.CIRCULAR_CW);
            require(state(Ui.motion(bar, time), 125).equals(array(array("0.0", "0.5", "0.5", "1.0"),
                    array("0.0", "0.0", "0.5", "0.5"), array("0.5", "0.0", "1.0", "0.25"))), "Circular progress lost its quadrant order");
            bar.setDirection(ProgressBar.Direction.RIGHT).setProgress(() -> (time[0] - .25f) * 2);
            JsonArray split = Ui.motion(bar, time);
            require(state(split, 40).size() == 0 && state(split, 160).equals(array(array("0.0", "0.0", "1.0", "1.0"))), "Split progress was not clamped like the native widget");
            Config.smoothProgressbar = true;
            bar.setProgress(() -> time[0]);
            require(Ui.motion(bar, time).size() == 200, "Smooth native steps were quantized");
            bar.setProgress(() -> { throw new IllegalArgumentException("failed native supplier"); });
            try { Ui.motion(bar, time); throw new AssertionError("Supplier failure was ignored"); }
            catch (IllegalArgumentException expected) { require(time[0] == .3f, "Supplier failure left the capture clock advanced"); }
            bar.setProgress(() -> Float.NaN);
            try { Ui.motion(bar, time); throw new AssertionError("Invalid progress was accepted"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("view_unsupported") && time[0] == .3f, "Wrong UI failure or unrestored clock"); }
        } finally { Config.smoothProgressbar = smooth; }
    }

    private static void windows() {
        List<Widget> widgets = new ArrayList<>();
        ModularWindow window = new ModularWindow(new Size(176, 90), widgets);
        require(window.getAlpha() == 255, "Pinned native default must be opaque byte alpha");
        Ui.verifyWindow(window);
        for (int alpha : new int[] {0, 1, 128, 254, 256, -1}) {
            ReflectionHelper.setPrivateValue(ModularWindow.class, window, alpha, "alpha");
            try { Ui.verifyWindow(window); throw new AssertionError("Unsupported window alpha accepted: " + alpha); }
            catch (Jobs.Fault expected) {
                require(expected.code.equals("view_unsupported") && expected.getMessage().contains("alpha=" + alpha), "Missing observed alpha");
            }
            require(window.getAlpha() == alpha, "Validation changed native alpha");
        }
        ReflectionHelper.setPrivateValue(ModularWindow.class, window, 255, "alpha");
        for (int i = 0; i < 4096; i++) widgets.add(new TextWidget(""));
        Ui.verifyWindow(window);
        widgets.add(new TextWidget(""));
        try { Ui.verifyWindow(window); throw new AssertionError("Window budget ignored"); }
        catch (Jobs.Fault expected) {
            require(expected.code.equals("view_unsupported") && expected.getMessage().contains("4097"), "Missing observed widget count");
        }
        System.out.println("Native UI windows: byte alpha, rejected translucency and widget budget passed");
    }

    private static int duration(JsonArray frames) {
        int ticks = 0;
        for (JsonElement frame : frames) ticks += frame.getAsJsonObject().get("ticks").getAsInt();
        return ticks;
    }
    private static JsonArray state(JsonArray frames, int tick) {
        for (JsonElement value : frames) {
            JsonObject frame = value.getAsJsonObject();
            if (tick < frame.get("ticks").getAsInt()) return frame.getAsJsonArray("areas");
            tick -= frame.get("ticks").getAsInt();
        }
        throw new AssertionError("Missing native UI tick");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
