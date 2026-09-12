package com.github.dcysteine.nesql.exporter.capture;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtnewhorizons.modularui.api.UIInfos;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.widget.IWidgetParent;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ProgressBar;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.api.recipe.maps.SpaceProjectFrontend;
import gregtech.api.util.GTRecipe;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager.FakeSpaceProjectRecipe;
import gregtech.common.misc.spaceprojects.interfaces.ISpaceProject;
import gregtech.nei.GTNEIDefaultHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.IntFunction;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native GT UI layers with bounded, discrete clipping tracks. The capture window owns its progress clock. */
final class Ui implements AutoCloseable {
    private final RecipeMapFrontend frontend;
    private final ModularWindow window;
    private final List<Layer> layers = new ArrayList<>();
    private final Map<String, JsonArray> layouts = new HashMap<>();
    private final String location;
    private int width, height;

    Ui(GTNEIDefaultHandler handler, String location) {
        Structures.version("modularui", "1.2.20");
        this.frontend = handler.getRecipeMap().getFrontend(); this.location = location;
        verify(frontend);
        BasicUIProperties properties = frontend.getUIProperties();
        Pos2d offset = ReflectionHelper.getPrivateValue(GTNEIDefaultHandler.class, handler, "WINDOW_OFFSET");
        float[] time = {0};
        // Fresh inventories and an injected clock avoid changing GT's global drawTicks or a live NEI window.
        window = frontend.createNEITemplate(new ItemStackHandler(properties.maxItemInputs), new ItemStackHandler(properties.maxItemOutputs),
                new ItemStackHandler(1), new ItemStackHandler(properties.maxFluidInputs), new ItemStackHandler(properties.maxFluidOutputs),
                () -> time[0], offset).build();
        try {
            UIInfos.initializeWindow(Minecraft.getMinecraft().thePlayer, window);
            if (window.getChildren().size() > 4096 || window.getAlpha() != 1) throw fault("Unsupported native UI layer state");
            List<Runnable> still = new ArrayList<>();
            Pos2d background = offset.add(frontend.getNEIProperties().recipeBackgroundOffset);
            for (IDrawable drawable : window.getBackground()) still.add(() -> at(background, () -> drawable.draw(Pos2d.ZERO, window.getSize(), 0)));
            for (Widget widget : window.getChildren()) {
                width = Math.max(width, widget.getPos().x + widget.getSize().width);
                height = Math.max(height, widget.getPos().y + widget.getSize().height);
                if (!(widget instanceof ProgressBar)) {
                    if (widget instanceof IWidgetParent) IWidgetParent.forEachByLayer((IWidgetParent) widget, child -> {
                        if (child instanceof ProgressBar) throw fault("Nested progress widget requires an explicit layout adapter");
                    });
                    still.add(() -> at(widget.getPos(), () -> { widget.drawBackground(0); widget.draw(0); }));
                    continue;
                }
                if (widget.getClass() != ProgressBar.class) throw fault("Unknown native progress widget: " + widget.getClass().getName());
                ProgressBar bar = (ProgressBar) widget;
                UITexture empty = ReflectionHelper.getPrivateValue(ProgressBar.class, bar, "emptyTexture");
                still.add(() -> at(bar.getPos(), () -> { bar.drawBackground(0); if (empty != null) empty.draw(Pos2d.ZERO, bar.getSize(), 0); }));
                flush(still);
                UITexture[] full = ReflectionHelper.getPrivateValue(ProgressBar.class, bar, "fullTexture");
                if (full[0] == null) continue;
                ProgressBar.Direction direction = ReflectionHelper.getPrivateValue(ProgressBar.class, bar, "direction");
                for (int part = 0; part < (direction == ProgressBar.Direction.CIRCULAR_CW ? 4 : 1); part++) {
                    if (full[part] == null || full[part].getClass() != UITexture.class) throw fault("Progress texture requires a drawable adapter");
                }
                JsonArray frames = motion(bar, time);
                layers.add(new Layer(bar.getPos().x, bar.getPos().y, bar.getSize().width, bar.getSize().height, frames, () -> {
                    if (direction != ProgressBar.Direction.CIRCULAR_CW) full[0].draw(Pos2d.ZERO, bar.getSize(), 0);
                    else {
                        float w = bar.getSize().width / 2f, h = bar.getSize().height / 2f;
                        full[0].draw(0, h, w, h); full[1].draw(0, 0, w, h); full[2].draw(w, 0, w, h); full[3].draw(w, h, w, h);
                    }
                }));
                if (layers.size() > 128) throw fault("Native UI exceeds its dynamic layer budget");
            }
            flush(still);
        } catch (RuntimeException | Error failure) { close(); throw failure; }
    }

    int width(int current) { return Math.max(current, width); }
    int height(int current) { return Math.max(current, height); }
    int foreground() { return layers.size() + 3; }

    void add(Facts facts, RecipeRow row, int width, int height, GTRecipe recipe) {
        String key = width + "/" + height + "/" + project(recipe);
        JsonArray layout = layouts.get(key);
        if (layout == null) {
            layout = new JsonArray();
            for (int index = 0; index < layers.size(); index++) {
                Layer layer = layers.get(index);
                int w = layer.frames == null ? width : layer.width, h = layer.frames == null ? height : layer.height;
                JsonObject element = object("kind", layer.frames == null ? "sprite" : "clip", "asset", null,
                        "x", layer.x, "y", layer.y, "width", w, "height", h, "z", index);
                if (layer.frames != null) element.addProperty("track", facts.track(layer.frames));
                facts.picture(new Facts.Picture(element, "asset", location + "/layout/" + key + "/" + index, w, h, () -> context(recipe, layer.draw)));
                layout.add(element);
            }
            layouts.put(key, layout);
        }
        for (JsonElement element : row.elements) {
            JsonObject record = element.getAsJsonObject(); record.addProperty("z", record.get("z").getAsInt() + layers.size());
        }
        for (JsonElement element : layout) row.elements.add(element);
    }

    void context(GTRecipe recipe, Runnable draw) {
        if (!(frontend instanceof SpaceProjectFrontend)) { draw.run(); return; }
        String name = project(recipe);
        ISpaceProject project = SpaceProjectManager.getProject(name);
        if (project == null) throw fault("Missing native space project: " + name);
        IDrawable previous = ReflectionHelper.getPrivateValue(SpaceProjectFrontend.class, (SpaceProjectFrontend) frontend, "projectTexture");
        try {
            ReflectionHelper.setPrivateValue(SpaceProjectFrontend.class, (SpaceProjectFrontend) frontend, project.getTexture(), "projectTexture");
            draw.run();
        } finally { ReflectionHelper.setPrivateValue(SpaceProjectFrontend.class, (SpaceProjectFrontend) frontend, previous, "projectTexture"); }
    }

    private String project(GTRecipe recipe) {
        if (!(frontend instanceof SpaceProjectFrontend)) return "";
        if (!(recipe instanceof FakeSpaceProjectRecipe)) throw fault("Space project UI has no project identity");
        return ((FakeSpaceProjectRecipe) recipe).projectName;
    }

    private void flush(List<Runnable> still) {
        if (still.isEmpty()) return;
        List<Runnable> captured = new ArrayList<>(still); still.clear();
        layers.add(new Layer(0, 0, 0, 0, null, () -> { for (Runnable draw : captured) draw.run(); }));
    }

    private static void at(Pos2d position, Runnable draw) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(position.x, position.y, 0); GL11.glColor4f(1, 1, 1, 1); GL11.glEnable(GL11.GL_BLEND);
            draw.run();
        } finally { GL11.glPopMatrix(); }
    }

    @SuppressWarnings("unchecked")
    static JsonArray motion(ProgressBar bar, float[] time) {
        if (bar.syncsToClient()) throw fault("Native progress depends on a server-synchronized value");
        Supplier<Float> progress = ReflectionHelper.getPrivateValue(ProgressBar.class, bar, "progress");
        ProgressBar.Direction direction = ReflectionHelper.getPrivateValue(ProgressBar.class, bar, "direction");
        if (progress == null || direction == null) throw fault("Native progress has no clock or direction");
        float previousTime = time[0];
        try {
            return frames(200, tick -> {
                time[0] = tick / 200f;
                Float value = progress.get();
                if (value == null || !Float.isFinite(value)) throw fault("Native progress returned an invalid value");
                return areas(bar, direction, value);
            });
        } finally { time[0] = previousTime; }
    }

    static JsonArray furnace(Facts facts, FurnaceRecipeHandler handler, String location) {
        JsonArray elements = new JsonArray();
        int[][] bars = {{51, 25, 176, 0, 14, 14, 48, 7}, {74, 23, 176, 14, 24, 16, 48, 0}};
        for (int[] bar : bars) {
            JsonObject element = object("kind", "clip", "asset", null, "x", bar[0], "y", bar[1], "width", bar[4], "height", bar[5],
                    "z", 0, "track", facts.track(progress(bar[4], bar[5], bar[6], bar[7])));
            facts.picture(new Facts.Picture(element, "asset", location + "/progress/" + bar[7], bar[4], bar[5], () -> {
                GuiDraw.changeTexture(handler.getGuiTexture()); GL11.glColor4f(1, 1, 1, 1);
                GuiDraw.drawTexturedModalRect(0, 0, bar[2], bar[3], bar[4], bar[5]);
            }));
            elements.add(element);
        }
        return elements;
    }

    static JsonArray progress(int width, int height, int ticks, int direction) {
        if (width <= 0 || height <= 0 || ticks <= 0 || ticks > 4096 || direction < 0 || direction > 7) throw fault("Invalid native NEI progress parameters");
        return frames(ticks, tick -> {
            float p = tick / (float) ticks;
            if (direction > 3) p = 1 - p;
            int axis = direction % 4, size = axis % 2 == 0 ? width : height;
            p = (int) (p * size) / (float) size;
            JsonArray areas = new JsonArray();
            switch (axis) {
                case 0: area(areas, 0, 0, p, 1); break;
                case 1: area(areas, 0, 0, 1, p); break;
                case 2: area(areas, 1 - p, 0, 1, 1); break;
                case 3: area(areas, 0, 1 - p, 1, 1); break;
                default: throw fault("Unknown native NEI progress direction");
            }
            return areas;
        });
    }

    private static JsonArray frames(int ticks, IntFunction<JsonArray> sample) {
        JsonArray frames = new JsonArray();
        for (int tick = 0; tick < ticks; tick++) {
            Jobs.checkpoint(); JsonArray areas = sample.apply(tick);
            JsonObject previous = frames.size() == 0 ? null : frames.get(frames.size() - 1).getAsJsonObject();
            if (previous != null && previous.get("areas").equals(areas)) previous.addProperty("ticks", previous.get("ticks").getAsInt() + 1);
            else frames.add(object("ticks", 1, "areas", areas));
        }
        return frames;
    }

    private static JsonArray areas(ProgressBar bar, ProgressBar.Direction direction, float progress) {
        JsonArray areas = new JsonArray();
        if (progress <= 0) return areas;
        if (direction == ProgressBar.Direction.CIRCULAR_CW) {
            float a = fraction(bar, progress * 4), b = fraction(bar, (progress - .25f) * 4);
            float c = fraction(bar, (progress - .5f) * 4), d = fraction(bar, (progress - .75f) * 4);
            area(areas, 0, 1 - a / 2, .5f, 1); area(areas, 0, 0, b / 2, .5f);
            area(areas, .5f, 0, 1, c / 2); area(areas, 1 - d / 2, .5f, 1, 1);
        } else {
            float p = progress >= 1 ? 1 : fraction(bar, progress);
            switch (direction) {
                case RIGHT: area(areas, 0, 0, p, 1); break;
                case LEFT: area(areas, 1 - p, 0, 1, 1); break;
                case DOWN: area(areas, 0, 0, 1, p); break;
                case UP: area(areas, 0, 1 - p, 1, 1); break;
                default: throw fault("Unknown native progress direction");
            }
        }
        return areas;
    }

    private static float fraction(ProgressBar bar, float value) {
        float result = bar.getProgressUV(Math.max(0, Math.min(1, value)));
        if (!Float.isFinite(result) || result < 0 || result > 1) throw fault("Native progress has an invalid pixel step");
        return result;
    }
    private static void area(JsonArray areas, float left, float top, float right, float bottom) {
        if (right > left && bottom > top) areas.add(array(Float.toString(left), Float.toString(top), Float.toString(right), Float.toString(bottom)));
    }

    private static void verify(RecipeMapFrontend frontend) {
        try {
            String factory = frontend.getClass().getMethod("createNEITemplate", IItemHandlerModifiable.class, IItemHandlerModifiable.class,
                    IItemHandlerModifiable.class, IItemHandlerModifiable.class, IItemHandlerModifiable.class, Supplier.class, Pos2d.class).getDeclaringClass().getName();
            String progress = frontend.getClass().getMethod("addProgressBar", ModularWindow.Builder.class, Supplier.class, Pos2d.class).getDeclaringClass().getName();
            if (!Arrays.asList("gregtech.api.recipe.RecipeMapFrontend", "gregtech.api.recipe.maps.PurificationUnitRecipeMapFrontend",
                    "gregtech.api.recipe.maps.SpaceProjectFrontend").contains(factory)
                    || !Arrays.asList("gregtech.api.recipe.RecipeMapFrontend", "gregtech.api.recipe.maps.PurificationUnitRecipeMapFrontend",
                    "gregtech.api.recipe.maps.SpaceProjectFrontend", "gregtech.api.recipe.maps.AssemblyLineFrontend",
                    "gtnhintergalactic.recipe.maps.SpaceAssemblerFrontend", "tectech.recipe.ResearchStationFrontend").contains(progress)) {
                throw fault("Native progress clock requires a UI adapter: " + frontend.getClass().getName());
            }
        } catch (NoSuchMethodException failure) { throw fault("Native UI API does not match the target version"); }
    }

    @Override public void close() {
        layouts.clear(); layers.clear();
        destroy(window);
    }
    static void destroy(ModularWindow window) {
        IWidgetParent.forEachByLayer(window, widget -> { if (window.isEnabled()) widget.onPause(); widget.onDestroy(); });
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("view_unsupported", message); }

    private static final class Layer {
        final int x, y, width, height;
        final JsonArray frames;
        final Runnable draw;
        Layer(int x, int y, int width, int height, JsonArray frames, Runnable draw) {
            this.x = x; this.y = y; this.width = width; this.height = height; this.frames = frames; this.draw = draw;
        }
    }
}
