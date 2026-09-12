package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Geometry;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.render.BaseMetaTileEntityRenderer;
import gregtech.common.render.IMTERenderer;
import gregtech.common.render.LaserRenderer;
import gregtech.common.tileentities.render.TileEntityLaser;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityEnderChestRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Explicit entity-renderer adapters. Models are newly constructed, never the game's shared mutable render models. */
final class Entities {
    private Entities() {}

    static void capture(TileEntity tile, TileEntitySpecialRenderer renderer, Models.Draft result) {
        if (emptyMachine(renderer, tile instanceof IGregTechTileEntity ? ((IGregTechTileEntity) tile).getMetaTileEntity() : null)) return;
        if (renderer.getClass() == LaserRenderer.class && tile.getClass() == TileEntityLaser.class) {
            laser((TileEntityLaser) tile, (LaserRenderer) renderer, result); return;
        }
        boolean chestRenderer = renderer.getClass() == TileEntityChestRenderer.class && tile.getClass() == TileEntityChest.class;
        boolean enderRenderer = renderer.getClass() == TileEntityEnderChestRenderer.class && tile.getClass() == TileEntityEnderChest.class;
        if (!chestRenderer && !enderRenderer) throw fault("Block entity requires a model adapter: " + renderer.getClass().getName());
        int facing = tile.getBlockMetadata();
        if (!tile.hasWorldObj() || (chestRenderer && facing == 0)) throw fault("Chest facing has not settled after construction");
        ModelChest model;
        ResourceLocation texture;
        float opening;
        Geometry pose = new Geometry().move(0, 1, 1).scale(1, -1, -1).move(.5f, .5f, .5f);
        if (chestRenderer) {
            TileEntityChest chest = (TileEntityChest) tile;
            chest.checkForAdjacentChests();
            // The vanilla renderer draws a double chest once, from the block without a negative neighbour.
            if (chest.adjacentChestZNeg != null || chest.adjacentChestXNeg != null) return;
            boolean large = chest.adjacentChestXPos != null || chest.adjacentChestZPos != null;
            boolean trapped = chest.func_145980_j() == 1;
            boolean holiday = ReflectionHelper.getPrivateValue(TileEntityChestRenderer.class, (TileEntityChestRenderer) renderer, "field_147509_j");
            String field = large ? (trapped ? "field_147507_b" : holiday ? "field_147508_c" : "field_147505_d")
                    : trapped ? "field_147506_e" : holiday ? "field_147503_f" : "field_147504_g";
            texture = ReflectionHelper.getPrivateValue(TileEntityChestRenderer.class, null, field);
            model = large ? new ModelLargeChest() : new ModelChest();
            if (facing == 2 && chest.adjacentChestXPos != null) pose.move(1, 0, 0);
            if (facing == 5 && chest.adjacentChestZPos != null) pose.move(0, 0, -1);
            opening = chest.prevLidAngle;
        } else if (enderRenderer) {
            texture = ReflectionHelper.getPrivateValue(TileEntityEnderChestRenderer.class, null, "field_147520_b");
            model = new ModelChest(); opening = ((TileEntityEnderChest) tile).field_145975_i;
        } else throw fault("Block entity requires a model adapter: " + renderer.getClass().getName());
        if (texture == null || !Float.isFinite(opening) || opening < 0 || opening > 1) throw fault("Invalid block entity model state");
        pose.turn(facing == 2 ? 180 : facing == 4 ? 90 : facing == 5 ? -90 : 0, 0, 1, 0).move(-.5f, -.5f, -.5f);
        float closed = 1 - opening, eased = 1 - closed * closed * closed;
        model.chestLid.rotateAngleX = -(eased * (float) Math.PI / 2);
        model.chestKnob.rotateAngleX = model.chestLid.rotateAngleX;
        try {
            JsonArray faces = Geometry.boxes(Arrays.asList(model.chestLid, model.chestKnob, model.chestBelow), pose, 1f / 16);
            Models.Paint paint = new Models.Paint(texture);
            for (JsonElement face : faces) result.faces.add(new Models.Face(paint, object("texture", null, "pass", "solid", "vertices", face)));
            if (result.faces.isEmpty()) throw fault("Block entity model has no visible parts");
        } catch (IllegalArgumentException failure) { throw fault(failure.getMessage()); }
    }

    static boolean emptyMachine(TileEntitySpecialRenderer renderer, Object machine) {
        if (renderer.getClass() != BaseMetaTileEntityRenderer.class) return false;
        if (machine instanceof IMTERenderer) throw fault("Machine entity renderer requires a model adapter: " + machine.getClass().getName());
        return true;
    }

    private static void laser(TileEntityLaser tile, LaserRenderer renderer, Models.Draft result) {
        if (!tile.getShouldRender()) return;
        // The native renderer draws with these offsets, then changes them and the tile's counter. Read only the draw state.
        double x = ReflectionHelper.getPrivateValue(LaserRenderer.class, renderer, "xOffset");
        double z = ReflectionHelper.getPrivateValue(LaserRenderer.class, renderer, "zOffset");
        long color = ((long) channel(tile.getRed()) << 24) | ((long) channel(tile.getGreen()) << 16)
                | ((long) channel(tile.getBlue()) << 8) | (int) (.7f * 255);
        try {
            Geometry pose = new Geometry().move(.5f, .5f, .5f)
                    .turn((float) tile.rotationAngle, (float) tile.rotAxisX, (float) tile.rotAxisY, (float) tile.rotAxisZ).move(-.5f, -.5f, -.5f);
            double topX = tile.realism ? x : .5, topZ = tile.realism ? z : .5;
            Models.Paint paint = Models.Paint.PLAIN;
            for (int axis = 0; axis < 2; axis++) {
                double dx = axis == 0 ? .03 : 0, dz = axis == 1 ? .03 : 0;
                JsonArray vertices = array(pose.vertex(topX - dx, 4, topZ - dz, 0, 0, color),
                        pose.vertex(topX + dx, 4, topZ + dz, 0, 0, color),
                        pose.vertex(x + dx, .5, z + dz, 0, 0, color), pose.vertex(x - dx, .5, z - dz, 0, 0, color));
                result.faces.add(new Models.Face(paint, object("texture", null, "pass", "blend", "vertices", vertices)));
                // Native lasers disable face culling. Opposite winding preserves both sides in the shared face format.
                result.faces.add(new Models.Face(paint, object("texture", null, "pass", "blend",
                        "vertices", array(vertices.get(3), vertices.get(2), vertices.get(1), vertices.get(0)))));
            }
        } catch (IllegalArgumentException failure) { throw fault(failure.getMessage()); }
    }

    private static int channel(float value) {
        if (!Float.isFinite(value)) throw fault("Invalid entity model color");
        return Math.max(0, Math.min(255, (int) (value * 255)));
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("model_unsupported", message); }
}
