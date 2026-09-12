package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** CPU model-box transforms following ModelRenderer.render; never compiles or calls OpenGL display lists. */
public final class Geometry {
    private final Matrix4f matrix;

    public Geometry() { matrix = new Matrix4f(); }
    private Geometry(Matrix4f matrix) { this.matrix = new Matrix4f(matrix); }
    public Geometry copy() { return new Geometry(matrix); }
    public Geometry move(float x, float y, float z) { matrix.translate(new Vector3f(x, y, z)); return this; }
    public Geometry scale(float x, float y, float z) { matrix.scale(new Vector3f(x, y, z)); return this; }
    public Geometry turn(float degrees, float x, float y, float z) {
        if (!Float.isFinite(degrees) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) throw new IllegalArgumentException("Invalid model rotation");
        if (degrees == 0) return this;
        double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        if (length == 0) throw new IllegalArgumentException("Model rotation has no axis");
        x /= length; y /= length; z /= length;
        matrix.rotate((float) (degrees * Math.PI / 180), new Vector3f(x, y, z)); return this;
    }

    public JsonObject vertex(double x, double y, double z, float u, float v, long color) {
        Vector4f point = Matrix4f.transform(matrix, new Vector4f((float) x, (float) y, (float) z, 1), null);
        if (!Float.isFinite(u) || !Float.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1) throw new IllegalArgumentException("Model texture coordinates outside bounds");
        if (color < 0 || color > 0xffffffffL) throw new IllegalArgumentException("Invalid model vertex color");
        return object("at", array(coordinate(point.x), coordinate(1 - point.y), coordinate(1 - point.z)),
                "uv", array(Float.toString(u), Float.toString(v)), "color", uint(color));
    }

    public static JsonArray boxes(Iterable<ModelRenderer> parts, Geometry pose, float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid model scale");
        JsonArray faces = new JsonArray();
        Set<ModelRenderer> path = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelRenderer part : parts) boxes(part, pose, scale, faces, path, 0);
        return faces;
    }

    private static void boxes(ModelRenderer part, Geometry parent, float scale, JsonArray faces, Set<ModelRenderer> path, int depth) {
        if (part == null || part.getClass() != ModelRenderer.class || depth > 64 || !path.add(part)) throw new IllegalArgumentException("Unsupported or cyclic model part");
        try {
            if (!part.showModel || part.isHidden) return;
            Geometry pose = parent.copy().move(part.offsetX, part.offsetY, part.offsetZ)
                    .move(part.rotationPointX * scale, part.rotationPointY * scale, part.rotationPointZ * scale)
                    .turn(part.rotateAngleZ * 57.295776f, 0, 0, 1)
                    .turn(part.rotateAngleY * 57.295776f, 0, 1, 0)
                    .turn(part.rotateAngleX * 57.295776f, 1, 0, 0);
            for (Object value : part.cubeList) {
                if (value == null || value.getClass() != ModelBox.class) throw new IllegalArgumentException("Unsupported model box implementation");
                TexturedQuad[] quads = ReflectionHelper.getPrivateValue(ModelBox.class, (ModelBox) value, "quadList", "field_78254_i");
                for (TexturedQuad quad : quads) {
                    if (quad == null || quad.nVertices != 4 || quad.vertexPositions.length != 4 || faces.size() >= 1024) throw new IllegalArgumentException("Invalid model box geometry");
                    JsonArray vertices = new JsonArray();
                    for (PositionTextureVertex vertex : quad.vertexPositions) {
                        vertices.add(pose.vertex(vertex.vector3D.xCoord * scale, vertex.vector3D.yCoord * scale, vertex.vector3D.zCoord * scale,
                                vertex.texturePositionX, vertex.texturePositionY, 0xffffffffL));
                    }
                    faces.add(vertices);
                }
            }
            if (part.childModels != null) for (Object child : part.childModels) {
                if (!(child instanceof ModelRenderer)) throw new IllegalArgumentException("Invalid model child");
                boxes((ModelRenderer) child, pose, scale, faces, path, depth + 1);
            }
        } finally { path.remove(part); }
    }

    private static String coordinate(float value) {
        if (!Float.isFinite(value) || Math.abs(value) > 256) throw new IllegalArgumentException("Model geometry exceeds local bounds");
        return Float.toString(value);
    }
}
