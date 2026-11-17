package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryTrace;
import com.github.dcysteine.nesql.exporter.core.RendererClock;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.VisualValues.*;

/** Read the actually linked native program, not the latest GTNHLib helper or
 * a source file which may differ from what OpenGL compiled in this session. */
final class CosmicShaders {
    private static final String VERTEX = "31a4078eeb6694f0625813f2d88aa152ddd84a38763da03e1ebdd81c0a476824";
    enum Profile {
        AVARITIA("avaritia", "Avaritia", "1.77", "fox.spiteful.avaritia.render.ShaderHelper",
                "fox.spiteful.avaritia.render.CosmicRenderShenanigans$1", "275a574e59940cf966078b38e66aa0de00e1dbee280013b47692f4264943f34d"),
        ETERNAL("eternal", "eternalsingularity", "1.2.3", "singulariteam.eternalsingularity.render.ShaderHelper",
                "singulariteam.eternalsingularity.render.CosmicRenderStuffs$1", "c074f2a43a76204d5a31ea7aeba4cbd06dda5000997c5bd5119913f532d148b4");
        final String id, mod, version, helper, callback, fragment;
        Profile(String id, String mod, String version, String helper, String callback, String fragment) {
            this.id=id; this.mod=mod; this.version=version; this.helper=helper; this.callback=callback; this.fragment=fragment;
        }
        JsonObject resource() { return record(object("kind", "cosmicShader", "profile", id, "version", version, "vertex", VERTEX, "fragment", fragment)); }
        String runtime(){return this==AVARITIA?"fox.spiteful.avaritia.render.CosmicRenderShenanigans":"singulariteam.eternalsingularity.render.CosmicRenderStuffs";}
    }

    static void admit(Profile profile){
        version(profile.mod);
        if(!RendererClock.instrumented(profile.callback)||!MagicApi.field(MagicApi.type(profile.runtime()),null,"shaderCallback").getClass().getName().equals(profile.callback))
            throw new Jobs.Fault("animation_unsupported","Native cosmic callback was replaced or not instrumented");
    }

    static Profile forRenderer(String type) {
        if (InventoryTrace.COSMIC.equals(type) || InventoryTrace.COSMIC_BOW.equals(type)) return Profile.AVARITIA;
        if (InventoryTrace.ETERNAL.equals(type)) return Profile.ETERNAL;
        if (InventoryTrace.UNIVERSIUM.equals(type)) { version("gregtech_nh"); return Profile.AVARITIA; }
        return null;
    }

    static Snapshot capture(Profile profile,int vertexFlags) {
        if (profile == null || !RendererClock.active()) throw new Jobs.Fault("animation_unsupported", "Native cosmic capture requires an active reviewed clock");
        version(profile.mod);
        if (!RendererClock.instrumented(profile.callback)) throw new Jobs.Fault("animation_unsupported", "Missing native cosmic tick hook");
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int owned = ((Number) MagicApi.field(MagicApi.type(profile.helper), null, "cosmicShader")).intValue();
        if (program == 0 || program != owned || GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            throw new Jobs.Fault("animation_unsupported", "Native cosmic shader is disabled, unlinked or replaced");
        String[] sources = sources(program);
        if (!VERTEX.equals(sources[0]) || !profile.fragment.equals(sources[1]))
            throw new Jobs.Fault("animation_unsupported", "Linked cosmic shader differs from the reviewed program");
        // Read each mat2 array element separately: glGetUniform on element zero
        // does not promise to return all ten elements of a GLSL uniform array.
        JsonArray uv = new JsonArray();
        for (int i = 0; i < 10; i++) for (float value : uniform(program, "cosmicuvs["+i+"]", 4)) uv.add(value(bits(value)));
        float time = uniform(program, "time2", 1)[0];
        if (Float.floatToRawIntBits(time) != Float.floatToRawIntBits((float) RendererClock.entityTick(0)))
            throw new Jobs.Fault("animation_unsupported", "Native cosmic shader did not consume the capture tick");
        IntBuffer scalar = BufferUtils.createIntBuffer(16);
        GL20.glGetUniform(program, location(program, "texture0"), scalar);
        int textureUnit = scalar.get(0);
        if (textureUnit < 0 || textureUnit > 31) throw new Jobs.Fault("animation_invalid", "Native cosmic texture unit exceeds budget");
        int attribute = GL20.glGetAttribLocation(program, "activelights");
        if (attribute < 0) throw new Jobs.Fault("animation_unsupported", "Native cosmic lighting attribute is missing");
        // Tessellator enables its conventional client arrays after this entry
        // observation. Those alias generic slots 0/2/3/8/9 in compatibility GL.
        if(attribute==0||attribute==2&&(vertexFlags&4)!=0||attribute==3&&(vertexFlags&2)!=0
                ||attribute==8&&(vertexFlags&1)!=0||attribute==9&&(vertexFlags&8)!=0)
            throw new Jobs.Fault("animation_unsupported","Native cosmic lighting aliases a per-vertex client array");
        ((java.nio.Buffer)scalar).clear(); GL20.glGetVertexAttrib(attribute, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, scalar);
        if (scalar.get(0) != GL11.GL_FALSE)
            throw new Jobs.Fault("animation_unsupported", "Native cosmic lighting requires a per-vertex attribute adapter");
        FloatBuffer light = BufferUtils.createFloatBuffer(4); GL20.glGetVertexAttrib(attribute, GL20.GL_CURRENT_VERTEX_ATTRIB, light);
        JsonObject bindings = object("time", bits(time), "yaw", bits(uniform(program,"yaw",1)[0]),
                "pitch", bits(uniform(program,"pitch",1)[0]), "externalScale", bits(uniform(program,"externalScale",1)[0]),
                "lightmix", bits(uniform(program,"lightmix",1)[0]), "opacity", bits(uniform(program,"opacity",1)[0]),
                "lightlevel", floats(uniform(program,"lightlevel",3)), "cosmicuvs", uv, "activeLights", bits(light.get(0)),
                "textureUnit", textureUnit);
        float[] ambient=material(GL11.GL_AMBIENT),diffuse=material(GL11.GL_DIFFUSE),emission=material(GL11.GL_EMISSION),model=global(GL11.GL_LIGHT_MODEL_AMBIENT,4);
        bindings.add("sceneColor",floats(emission[0]+ambient[0]*model[0],emission[1]+ambient[1]*model[1],emission[2]+ambient[2]*model[2],diffuse[3]));
        JsonArray lights=new JsonArray();
        for(int index=0;index<2;index++){
            int id=GL11.GL_LIGHT0+index;float cutoff=light(id,GL11.GL_SPOT_CUTOFF,1)[0];
            lights.add(object("position",floats(light(id,GL11.GL_POSITION,4)),"ambient",floats(light(id,GL11.GL_AMBIENT,4)),
                    "diffuse",floats(light(id,GL11.GL_DIFFUSE,4)),"attenuation",floats(light(id,GL11.GL_CONSTANT_ATTENUATION,1)[0],
                            light(id,GL11.GL_LINEAR_ATTENUATION,1)[0],light(id,GL11.GL_QUADRATIC_ATTENUATION,1)[0]),
                    "spotDirection",floats(light(id,GL11.GL_SPOT_DIRECTION,3)),"spotCutoff",bits(cutoff),
                    "spotCosCutoff",bits(cutoff==180?-1.0F:(float)Math.cos(cutoff*Math.PI/180.0)),"spotExponent",bits(light(id,GL11.GL_SPOT_EXPONENT,1)[0])));
        }
        bindings.add("lights",lights);
        return new Snapshot(profile, program, bindings);
    }
    /** Restore the renderer's temporary Java globals and pre-existing shader
     * uniforms even when collection is cancelled or the native draw fails. */
    static final class Globals implements AutoCloseable {
        private final Field inventory,opacity,lightlevel;
        private final Object oldInventory,oldOpacity;private final float[] oldLight,colors;
        private final int program,attribute,textureUnit;private final float[] active;
        private final java.util.Map<String,float[]> uniforms=new java.util.LinkedHashMap<>();
        Globals(Profile profile){
            try{
                Class<?> type=MagicApi.type(profile.runtime());inventory=field(type,"inventoryRender");opacity=field(type,"cosmicOpacity");lightlevel=field(type,"lightlevel");
                oldInventory=inventory.get(null);oldOpacity=opacity.get(null);oldLight=(float[])lightlevel.get(null);
                if(oldLight==null||oldLight.length!=3)throw new Jobs.Fault("animation_unsupported","Native cosmic light array changed");
                colors=oldLight.clone();program=((Number)MagicApi.field(MagicApi.type(profile.helper),null,"cosmicShader")).intValue();
                if(program!=0&&GL20.glIsProgram(program)&&GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0){
                    IntBuffer sampler=BufferUtils.createIntBuffer(16);GL20.glGetUniform(program,location(program,"texture0"),sampler);textureUnit=sampler.get(0);
                    for(String name:new String[]{"time2","yaw","pitch","externalScale","lightmix","opacity"})uniforms.put(name,uniform(program,name,1));
                    uniforms.put("lightlevel",uniform(program,"lightlevel",3));
                    for(int i=0;i<10;i++)uniforms.put("cosmicuvs["+i+"]",uniform(program,"cosmicuvs["+i+"]",4));
                    attribute=GL20.glGetAttribLocation(program,"activelights");
                    FloatBuffer values=BufferUtils.createFloatBuffer(4);if(attribute>=0)GL20.glGetVertexAttrib(attribute,GL20.GL_CURRENT_VERTEX_ATTRIB,values);
                    active=new float[4];values.get(active);
                }else{attribute=-1;active=null;textureUnit=0;}
            }catch(IllegalAccessException failure){throw new Jobs.Fault("animation_unsupported",failure.toString());}
        }
        @Override public void close(){
            int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            try{
                inventory.set(null,oldInventory);opacity.set(null,oldOpacity);lightlevel.set(null,oldLight);System.arraycopy(colors,0,oldLight,0,3);
                if(!uniforms.isEmpty()){
                    if(!GL20.glIsProgram(program))throw new Jobs.Fault("animation_changed","Native cosmic shader was deleted during capture");
                    GL20.glUseProgram(program);
                    GL20.glUniform1i(location(program,"texture0"),textureUnit);
                    for(java.util.Map.Entry<String,float[]> entry:uniforms.entrySet()){
                        int location=location(program,entry.getKey());float[] value=entry.getValue();
                        if(value.length==1)GL20.glUniform1f(location,value[0]);
                        else if(value.length==3)GL20.glUniform3f(location,value[0],value[1],value[2]);
                        else{FloatBuffer buffer=BufferUtils.createFloatBuffer(4);buffer.put(value);((java.nio.Buffer)buffer).flip();GL20.glUniformMatrix2(location,false,buffer);}
                    }
                    if(attribute>=0)GL20.glVertexAttrib4f(attribute,active[0],active[1],active[2],active[3]);
                }
            }catch(IllegalAccessException failure){throw new Jobs.Fault("animation_changed",failure.toString());}
            finally{GL20.glUseProgram(previous);}
        }
        private static Field field(Class<?> owner,String name){try{Field result=owner.getDeclaredField(name);result.setAccessible(true);return result;}catch(NoSuchFieldException error){throw new Jobs.Fault("animation_unsupported",error.toString());}}
    }
    private static float[] global(int name,int count){FloatBuffer data=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(name,data);float[] values=new float[count];data.get(values);return values;}
    private static float[] material(int name){FloatBuffer data=BufferUtils.createFloatBuffer(4);GL11.glGetMaterial(GL11.GL_FRONT,name,data);float[] values=new float[4];data.get(values);return values;}
    private static float[] light(int id,int name,int count){FloatBuffer data=BufferUtils.createFloatBuffer(4);GL11.glGetLight(id,name,data);float[] values=new float[count];data.get(values);return values;}

    static final class Snapshot {
        final Profile profile; final int program; final JsonObject values;
        private final Object callback;
        Snapshot(Profile profile, int program, JsonObject values) { this.profile=profile; this.program=program; this.values=values;
            callback=MagicApi.field(MagicApi.type(profile.runtime()),null,"shaderCallback");}
        void verify() {
            int owned = ((Number) MagicApi.field(MagicApi.type(profile.helper), null, "cosmicShader")).intValue();
            if (program != owned || !GL20.glIsProgram(program)||GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0||!cpw.mods.fml.relauncher.ReflectionHelper.<Boolean,net.minecraft.client.renderer.OpenGlHelper>getPrivateValue(
                    net.minecraft.client.renderer.OpenGlHelper.class,null,"shadersSupported","field_148824_g")
                    ||MagicApi.field(MagicApi.type(profile.runtime()),null,"shaderCallback")!=callback)
                throw new Jobs.Fault("animation_changed", "Native cosmic program authority changed before capture seal");
            String[] actual = sources(program);
            if (!VERTEX.equals(actual[0]) || !profile.fragment.equals(actual[1]))
                throw new Jobs.Fault("animation_changed", "Native cosmic program changed before capture seal");
        }
    }
    private static String[] sources(int program) {
        if (GL20.glGetProgrami(program, GL20.GL_ATTACHED_SHADERS) != 2)
            throw new Jobs.Fault("animation_unsupported", "Native cosmic shader attachment census changed");
        IntBuffer count = BufferUtils.createIntBuffer(1), shaders = BufferUtils.createIntBuffer(2);
        GL20.glGetAttachedShaders(program, count, shaders);
        if (count.get(0) != 2) throw new Jobs.Fault("animation_changed", "Native cosmic shader attachments changed");
        String[] result = {null,null};
        for (int i=0;i<2;i++) {
            int shader=shaders.get(i), type=GL20.glGetShaderi(shader,GL20.GL_SHADER_TYPE), length=GL20.glGetShaderi(shader,GL20.GL_SHADER_SOURCE_LENGTH);
            if (length < 1 || length > 65536) throw new Jobs.Fault("animation_limit", "Native cosmic shader source exceeds its budget");
            int slot=type==GL20.GL_VERTEX_SHADER?0:type==GL20.GL_FRAGMENT_SHADER?1:-1;
            if (slot < 0 || result[slot] != null) throw new Jobs.Fault("animation_unsupported", "Unreviewed native cosmic shader stage");
            result[slot]=CanonicalJson.digest(GL20.glGetShaderSource(shader,length).getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }
    private static int location(int program,String name) {
        int value=GL20.glGetUniformLocation(program,name);
        if (value<0) throw new Jobs.Fault("animation_unsupported", "Missing native cosmic uniform: "+name);return value;
    }
    private static float[] uniform(int program,String name,int size) {
        FloatBuffer data=BufferUtils.createFloatBuffer(16);GL20.glGetUniform(program,location(program,name),data);
        float[] result=new float[size];data.get(result);for(float value:result)bits(value);return result;
    }
    private static void version(String id) { TargetAdmission.require("CosmicShaders", id); }
}
