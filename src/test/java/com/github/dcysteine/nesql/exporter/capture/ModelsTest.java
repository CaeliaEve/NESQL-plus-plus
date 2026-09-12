package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.common.render.BaseMetaTileEntityRenderer;
import gregtech.common.render.IMTERenderer;
import gregtech.common.render.LaserRenderer;
import gregtech.common.tileentities.render.TileEntityLaser;
import net.minecraft.util.ResourceLocation;

/** Headless adapter checks invoked by the existing source behavior suite. */
public final class ModelsTest {
    private ModelsTest() {}

    public static void run() {
        require(Models.world(0, false, true), "Entity renderer suppressed a standard world model");
        require(Models.world(100, true, true), "Entity renderer suppressed a registered world model");
        require(!Models.world(22, false, true) && !Models.world(-1, false, true), "Entity-only block required a world model");
        require(Models.world(22, true, true), "Custom chest world renderer was ignored");
        unsupported(() -> Models.world(22, false, false));
        unsupported(() -> Models.world(-1, false, false));
        BaseMetaTileEntityRenderer machine = new BaseMetaTileEntityRenderer();
        require(Entities.emptyMachine(machine, new Object()), "Ordinary GT machine was rejected by its shared entity dispatcher");
        require(Entities.emptyMachine(machine, null), "Empty GT machine acquired entity geometry");
        IMTERenderer custom = (x, y, z, time) -> { throw new AssertionError("Unknown machine renderer was invoked"); };
        unsupported(() -> Entities.emptyMachine(machine, custom));
        require(!Entities.emptyMachine(new BaseMetaTileEntityRenderer() {}, null), "Unknown dispatcher override was accepted");

        TileEntityLaser tile = new TileEntityLaser();
        LaserRenderer laser = new LaserRenderer();
        Models.Draft model = new Models.Draft(new JsonObject());
        Entities.capture(tile, laser, model);
        require(model.faces.isEmpty(), "Disabled laser emitted entity faces");

        Models.Face body = new Models.Face(new Models.Paint(new ResourceLocation("test", "body")), new JsonObject());
        model.faces.add(body);
        Entities.capture(tile, laser, model);
        require(model.faces.size() == 1 && model.faces.get(0) == body, "Empty entity capture discarded the world model");

        tile.shouldRender = true; tile.counter = 17; tile.red = 1; tile.green = .5f; tile.blue = 0;
        ReflectionHelper.setPrivateValue(LaserRenderer.class, laser, .25d, "xOffset");
        ReflectionHelper.setPrivateValue(LaserRenderer.class, laser, .75d, "zOffset");
        Entities.capture(tile, laser, model);
        require(model.faces.size() == 5 && model.faces.get(0) == body, "Laser capture did not add both sides to the body");
        JsonArray front = model.faces.get(1).record.getAsJsonArray("vertices");
        JsonArray back = model.faces.get(2).record.getAsJsonArray("vertices");
        point(front, 0, .47f, -3, .5f); point(front, 2, .28f, .5f, .25f);
        for (int i = 0; i < 4; i++) {
            require(front.get(i).equals(back.get(3 - i)), "Laser back face has the wrong winding");
            require(front.get(i).getAsJsonObject().get("color").getAsLong() == 0xff7f00b2L, "Laser vertex lost its color or opacity");
        }
        require(model.faces.get(1).record.get("pass").getAsString().equals("blend"), "Laser lost its transparent pass");
        require(model.faces.get(1).paint == Models.Paint.PLAIN, "Laser did not use the neutral texture");
        Images.Image plain = Images.plain();
        require(plain.pixels.getWidth() == 1 && plain.pixels.getHeight() == 1 && plain.pixels.getRGB(0, 0) == -1,
                "Untextured geometry acquired a resource-pack tint");

        tile.realism = true;
        model.faces.clear(); Entities.capture(tile, laser, model);
        point(model.faces.get(0).record.getAsJsonArray("vertices"), 0, .22f, -3, .25f);
        tile.realism = false; tile.rotationAngle = 90; tile.rotAxisZ = 2;
        model.faces.clear(); Entities.capture(tile, laser, model);
        point(model.faces.get(0).record.getAsJsonArray("vertices"), 0, -3, .53f, .5f);
        require(tile.counter == 17 && tile.rotationAngle == 90 && tile.rotAxisZ == 2, "Capture changed native laser state");
        require((double) ReflectionHelper.getPrivateValue(LaserRenderer.class, laser, "xOffset") == .25
                && (double) ReflectionHelper.getPrivateValue(LaserRenderer.class, laser, "zOffset") == .75, "Capture advanced shared laser offsets");
        tile.rotationAngle = Double.NaN;
        unsupported(() -> Entities.capture(tile, laser, new Models.Draft(new JsonObject())));
        unsupported(() -> Entities.capture(new TileEntityLaser() {}, laser, new Models.Draft(new JsonObject())));
    }

    private static void point(JsonArray vertices, int index, float x, float y, float z) {
        JsonArray at = vertices.get(index).getAsJsonObject().getAsJsonArray("at");
        float[] expected = {x, y, z};
        for (int axis = 0; axis < 3; axis++) require(Math.abs(at.get(axis).getAsFloat() - expected[axis]) < .00001f, "Wrong laser geometry at axis " + axis);
    }

    private static void unsupported(Runnable action) {
        try { action.run(); throw new AssertionError("Unknown entity geometry was accepted"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("model_unsupported"), "Wrong model failure"); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
