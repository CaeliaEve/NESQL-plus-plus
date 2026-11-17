package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryTrace;
import com.github.dcysteine.nesql.exporter.core.RendererClock;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.model.*;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.VisualValues.*;

/** Native inventory programs, separate from finite sprite timelines and recipe
 * programs. Shared geometry and MathHelper values are content addressed once. */
final class InventoryVisual {
    final VisualGraph.Node root;

    InventoryVisual(VisualGraph.Node root) { this.root = root; }

    static final class Selection {
        final IItemRenderer registered,renderer;
        Selection(IItemRenderer registered,IItemRenderer renderer){this.registered=registered;this.renderer=renderer;}
    }
    static boolean handles(Selection selection) {
        IItemRenderer renderer = selection.renderer;
        return renderer != null && (InventoryTrace.KEY.equals(renderer.getClass().getName())||CosmicShaders.forRenderer(renderer.getClass().getName())!=null
                ||com.github.dcysteine.nesql.exporter.core.ForestryTrace.item(renderer.getClass().getName()));
    }

    static Images.Image capture(Images images, Facts.Icon request, Selection selection) throws Exception {
        IItemRenderer selected=selection.renderer;
        if(selected==null)throw new Jobs.Fault("animation_changed","Native inventory renderer disappeared");
        if(com.github.dcysteine.nesql.exporter.core.ForestryTrace.item(selected.getClass().getName()))return ForestryInventory.capture(images,request,selection);
        if(!InventoryTrace.KEY.equals(selected.getClass().getName()))return CosmicInventory.capture(images,request,selection);
        TargetAdmission.require("InventoryVisual", "GalacticraftCore");
        if (!InventoryTrace.instrumented(InventoryTrace.KEY))
            throw new Jobs.Fault("animation_unsupported", "Galacticraft key requires its reviewed native inventory hook");
        Object renderer = selected;
        JsonObject[] entry = {null};
        boolean[] completed = {false};
        RuntimeException[] observationFailure = {null};
        long resolution = org.lwjgl.Sys.getTimerResolution();
        if (resolution <= 0) throw new Jobs.Fault("animation_invalid", "Invalid native Sys timer resolution");
        byte[] preview;
        try (InventoryTrace.Scope trace = InventoryTrace.observe(new InventoryTrace.Observer() {
             @Override public void enter(Object actual, Object type, Object stack) {
                 if (observationFailure[0] != null) return;
                 try {
                     if (actual != renderer || type != IItemRenderer.ItemRenderType.INVENTORY || stack != request.item || entry[0] != null)
                         throw new Jobs.Fault("animation_unsupported", "Unexpected nested key inventory renderer");
                     if(GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM)!=0)
                         throw new Jobs.Fault("animation_unsupported","Native key entered with an unreviewed shader program");
                     entry[0] = state();
                 } catch (RuntimeException failure) { observationFailure[0] = failure; }
             }
             @Override public void exit(Object actual) {
                 if (observationFailure[0] != null) return;
                 if (actual != renderer || entry[0] == null || completed[0]) {
                     observationFailure[0] = new Jobs.Fault("animation_unsupported", "Unexpected key renderer completion"); return;
                 }
                 completed[0] = true;
             }
             }); RendererClock.Scope clock = RendererClock.at(0, resolution);
                 com.github.dcysteine.nesql.exporter.core.RendererRandom.Scope random = images.random.scope()) {
            preview = images.render(request);
        }
        // Do not throw a collector admission error through NEI's drawItem: it
        // would mark a perfectly valid game item as a native rendering failure.
        if (observationFailure[0] != null) throw observationFailure[0];
        if (entry[0] == null || !completed[0]) throw new Jobs.Fault("animation_unsupported", "Native key renderer did not complete successfully");
        Object model = MagicApi.field(renderer, "keyModel");
        if (!model.getClass().getName().equals("micdoodle8.mods.galacticraft.core.client.model.ModelKey"))
            throw new Jobs.Fault("animation_unsupported", "Unreviewed Galacticraft key model");
        ModelRenderer[] parts = (ModelRenderer[]) MagicApi.field(model, "keyParts");
        if (parts.length != 5) throw new Jobs.Fault("animation_unsupported", "Native key part census changed");
        JsonArray mesh = new JsonArray();
        for (ModelRenderer part : parts) {
            mesh.add(images.inventoryModels.part(part,part(part)));
        }
        VisualGraph.Node geometry = VisualGraph.node(object("kind", "mesh", "parts", mesh));
        VisualGraph.Node sine = images.sine();
        ResourceLocation location = (ResourceLocation) MagicApi.field(renderer, "treasureChestTexture");
        NativeVisualSources.fieldIdentity(renderer,"keyModel",model);NativeVisualSources.fieldIdentity(renderer,"treasureChestTexture",location);
        NativeVisualSources.array(model,"keyParts",parts);
        VisualGraph.Node program = VisualGraph.node(object("kind", "galacticraftKey", "version", "3.3.13-GTNH",
                "resolution", Long.toString(resolution), "state", entry[0]))
                .link("mesh", geometry).link("trig", sine).link("sampler", NativeSamplers.full(location));
        InventoryVisual visual = new InventoryVisual(program);
        return new Images.Image(Images.decode(64, 64, preview), new JsonArray(), "capture", request.registry, false, visual);
    }

    static VisualGraph.Node randomResource(com.github.dcysteine.nesql.exporter.core.RendererRandom.Snapshot snapshot) {
        return VisualGraph.node(object("kind", "javaRandom", "stream", snapshot.stream, "seed", snapshot.seed,
                "haveGaussian", snapshot.haveGaussian, "gaussian", snapshot.gaussian));
    }
    static Selection select(Facts.Icon request){
        if(request.item==null)return new Selection(null,null);
        IItemRenderer renderer=MinecraftForgeClient.getItemRenderer(request.item,IItemRenderer.ItemRenderType.INVENTORY);
        IItemRenderer registered=renderer;
        if(renderer!=null&&renderer.getClass().getName().equals(InventoryTrace.GT_ITEMS)){
            MagicApi.version("InventoryVisual","gregtech_nh");
            renderer=(IItemRenderer)MagicApi.invoke(renderer.getClass(),renderer,"getRendererForItemStack",new Class<?>[]{net.minecraft.item.ItemStack.class},request.item);
        }
        return new Selection(registered,renderer);
    }

    /** Box vertices remain in inventory model coordinates. World-model y/z
     * inversion would reverse both the key and its lighting. */
    static JsonObject part(ModelRenderer part) {
        if (part == null || part.getClass() != ModelRenderer.class || part.isHidden || !part.showModel
                || part.childModels != null && !part.childModels.isEmpty() || part.cubeList.size() > 64)
            throw new Jobs.Fault("animation_unsupported", "Unreviewed inventory model part");
        JsonArray vertices = new JsonArray();
        for (Object value : part.cubeList) {
            if (value == null || value.getClass() != ModelBox.class) throw new Jobs.Fault("animation_unsupported", "Unreviewed inventory model box");
            TexturedQuad[] quads = ReflectionHelper.getPrivateValue(ModelBox.class, (ModelBox) value, "quadList", "field_78254_i");
            for (TexturedQuad quad : quads) {
                if (quad == null || quad.nVertices != 4 || quad.vertexPositions.length != 4)
                    throw new Jobs.Fault("animation_invalid", "Native inventory quad changed");
                Vec3 a = quad.vertexPositions[1].vector3D.subtract(quad.vertexPositions[0].vector3D);
                Vec3 b = quad.vertexPositions[1].vector3D.subtract(quad.vertexPositions[2].vector3D);
                Vec3 n = b.crossProduct(a).normalize();
                boolean invert = ReflectionHelper.getPrivateValue(TexturedQuad.class, quad, "invertNormal", "field_78238_c");
                float nx = packedNormal((float) n.xCoord * (invert ? -1 : 1));
                float ny = packedNormal((float) n.yCoord * (invert ? -1 : 1));
                float nz = packedNormal((float) n.zCoord * (invert ? -1 : 1));
                // Native GL_QUADS uses the same ordered two triangles.
                for (int index : new int[] {0, 1, 2, 0, 2, 3}) {
                    PositionTextureVertex vertex = quad.vertexPositions[index];
                    vertices.add(floats((float) vertex.vector3D.xCoord * 0.0625F, (float) vertex.vector3D.yCoord * 0.0625F,
                            (float) vertex.vector3D.zCoord * 0.0625F, nx, ny, nz, vertex.texturePositionX, vertex.texturePositionY));
                }
            }
        }
        return object("offset", floats(part.offsetX, part.offsetY, part.offsetZ),
                "pivot", floats(part.rotationPointX * 0.0625F, part.rotationPointY * 0.0625F, part.rotationPointZ * 0.0625F),
                "rotation", floats(part.rotateAngleZ, part.rotateAngleY, part.rotateAngleX), "vertices", vertices);
    }
    private static float packedNormal(float value) { return ((byte) (value * 127.0F)) / 127.0F; }

    static String sineBits() {
        float[] table = ReflectionHelper.getPrivateValue(MathHelper.class, null, "SIN_TABLE", "field_76144_a");
        if (table.length != 65536) throw new Jobs.Fault("animation_unsupported", "Native MathHelper lookup size changed");
        StringBuilder result = new StringBuilder(table.length * 8);
        for (float value : table) result.append(bits(value));
        return result.toString();
    }
    private static JsonArray vector(int name, int count) {
        FloatBuffer data = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(name, data);
        float[] values = new float[count]; data.get(values); return floats(values);
    }
    private static JsonArray material(int name) {
        FloatBuffer data = BufferUtils.createFloatBuffer(4); GL11.glGetMaterial(GL11.GL_FRONT, name, data);
        float[] values = new float[4]; data.get(values); return floats(values);
    }
    private static JsonArray light(int light, int name) {
        FloatBuffer data = BufferUtils.createFloatBuffer(4); GL11.glGetLight(light, name, data);
        float[] values = new float[4]; data.get(values); return floats(values);
    }
    static JsonObject state() { return state(false); }
    static JsonObject state(boolean lightmap) {
        if (GL11.glIsEnabled(GL11.GL_FOG) || GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
                || GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL) || GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP)
                || GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_S) || GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_T))
            throw new Jobs.Fault("animation_unsupported", "Unreviewed key inventory fixed-function effect");
        int active = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        if (active != net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit)
            throw new Jobs.Fault("animation_unsupported", "Native key entered on a non-default texture unit");
        net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
        try {
            if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D) && !lightmap)
                throw new Jobs.Fault("animation_unsupported", "Native key lightmap requires a separate sampled lighting resource");
        } finally { net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(active); }
        FloatBuffer textureMatrix = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, textureMatrix);
        for (int i = 0; i < 16; i++) if (textureMatrix.get(i) != (i % 5 == 0 ? 1.0F : 0.0F))
            throw new Jobs.Fault("animation_unsupported", "Native key texture matrix requires a reviewed program");
        if (GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL)
                && (GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_PARAMETER) != GL11.GL_AMBIENT_AND_DIFFUSE
                    || GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_FACE) != GL11.GL_FRONT_AND_BACK))
            throw new Jobs.Fault("animation_unsupported", "Unreviewed inventory color material mode");
        if (GL11.glGetInteger(GL14.GL_BLEND_EQUATION) != GL14.GL_FUNC_ADD)
            throw new Jobs.Fault("animation_unsupported", "Unreviewed inventory blend equation");
        JsonArray lights = new JsonArray();
        for (int i = 0; i < 8; i++) if (GL11.glIsEnabled(GL11.GL_LIGHT0 + i)) {
            int id = GL11.GL_LIGHT0 + i;
            lights.add(object("position", light(id, GL11.GL_POSITION), "ambient", light(id, GL11.GL_AMBIENT),
                    "diffuse", light(id, GL11.GL_DIFFUSE), "specular", light(id, GL11.GL_SPECULAR)));
        }
        return object("modelView", vector(GL11.GL_MODELVIEW_MATRIX, 16), "projection", vector(GL11.GL_PROJECTION_MATRIX, 16),
                "color", vector(GL11.GL_CURRENT_COLOR, 4), "ambient", vector(GL11.GL_LIGHT_MODEL_AMBIENT, 4),
                "materialAmbient", material(GL11.GL_AMBIENT), "materialDiffuse", material(GL11.GL_DIFFUSE),
                "materialSpecular", material(GL11.GL_SPECULAR), "emission", material(GL11.GL_EMISSION),
                "lighting", GL11.glIsEnabled(GL11.GL_LIGHTING), "colorMaterial", GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL),
                "normalize", GL11.glIsEnabled(GL11.GL_NORMALIZE), "rescaleNormal", GL11.glIsEnabled(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL),
                "lights", lights, "depth", GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "depthWrite", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                "depthFunc", GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "blend", GL11.glIsEnabled(GL11.GL_BLEND),
                "blendSrcRgb", GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "blendDstRgb", GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                "blendSrcAlpha", GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), "blendDstAlpha", GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                "alphaTest", GL11.glIsEnabled(GL11.GL_ALPHA_TEST), "alphaFunc", GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),
                "alphaRef", bits(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)), "cull", GL11.glIsEnabled(GL11.GL_CULL_FACE),
                "cullFace", GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "frontFace", GL11.glGetInteger(GL11.GL_FRONT_FACE));
    }

}
