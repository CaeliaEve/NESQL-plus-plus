package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryTrace;
import com.github.dcysteine.nesql.exporter.core.InventoryDrawTrace;
import com.github.dcysteine.nesql.exporter.core.RendererClock;
import com.github.dcysteine.nesql.exporter.core.RendererRandom;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.*;

import static com.github.dcysteine.nesql.exporter.capture.VisualValues.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Ordered native base/halo/pulse/shader draws. These are item-content programs,
 * never GUI captures and never a finite animation inferred from screenshots. */
final class CosmicInventory implements InventoryTrace.Observer,InventoryDraws.Listener {
    private final Images images;
    private final Facts.Icon request;
    private final IItemRenderer renderer;
    private final IItemRenderer registered;
    private final CosmicShaders.Profile profile;
    private final List<Object> calls=new ArrayList<>();
    private final List<VisualGraph.Node> layers=new ArrayList<>();
    private final List<CosmicShaders.Snapshot> shaders=new ArrayList<>();
    private final Random random;
    private Pulse pulse;
    private RuntimeException failure;
    private boolean entered,completed;
    private CosmicInventory(Images images,Facts.Icon request,InventoryVisual.Selection selection,CosmicShaders.Profile profile){
        this.images=images;this.request=request;this.renderer=selection.renderer;this.registered=selection.registered;this.profile=profile;
        random=profile==CosmicShaders.Profile.ETERNAL?(Random)MagicApi.field(renderer,"rand"):null;
        if(random!=null){NativeVisualSources.random(random,renderer);images.random.snapshot(random);} // Admission precedes NEI's exception-catching draw.
    }

    static Images.Image capture(Images images,Facts.Icon request,InventoryVisual.Selection selection) throws Exception {
        IItemRenderer renderer=selection.renderer;
        String type=renderer.getClass().getName();CosmicShaders.Profile profile=CosmicShaders.forRenderer(type);
        if(profile==null||!InventoryTrace.instrumented(type)||Tessellator.instance==null||!InventoryDrawTrace.instrumented())
            throw fault("Native cosmic inventory hooks are not available");
        CosmicShaders.admit(profile);
        if(!images.inventoryTextures.prepare())return null;
        CosmicInventory scene=new CosmicInventory(images,request,selection,profile);
        InventoryDraws draws=new InventoryDraws(scene);
        RenderItem item=RenderItem.getInstance();boolean color=ReflectionHelper.getPrivateValue(RenderItem.class,item,"renderWithColor","field_77024_a");
        byte[] preview;
        try(CosmicShaders.Globals globals=new CosmicShaders.Globals(profile);
            RendererClock.Scope clock=RendererClock.at(0,1000);RendererRandom.Scope random=images.random.scope();
            InventoryTrace.Scope entry=InventoryTrace.observe(scene);InventoryDrawTrace.Scope trace=InventoryDrawTrace.observe(draws)){
            preview=images.render(request);
        }finally{ReflectionHelper.setPrivateValue(RenderItem.class,item,color,"renderWithColor","field_77024_a");}
        draws.verify();if(scene.failure!=null)throw scene.failure;
        if(!scene.entered||!scene.completed||!scene.calls.isEmpty()||scene.layers.isEmpty()||scene.pulse!=null||scene.shaders.isEmpty())
            throw fault("Native cosmic inventory draw did not finish its complete scene");
        for(CosmicShaders.Snapshot shader:scene.shaders){shader.verify();NativeAuthorities.current().remember(shader.profile,"shader",shader::verify);}
        String version=type.equals(InventoryTrace.UNIVERSIUM)?"5.09.51.482":profile.version;
        VisualGraph.Node root=VisualGraph.node(object("kind","cosmicInventory","renderer",type,"version",version)).links("draws",scene.layers);
        return new Images.Image(Images.decode(64,64,preview),new JsonArray(),"capture",request.registry,false,new InventoryVisual(root));
    }

    @Override public void enter(Object actual,Object type,Object stack){safe(()->{
        if(type!=IItemRenderer.ItemRenderType.INVENTORY||stack!=request.item||!InventoryTrace.reviewed(actual.getClass().getName()))
            throw fault("Unexpected nested cosmic inventory renderer");
        if(calls.isEmpty()){
            if(entered||actual!=registered)
                throw fault("Native cosmic inventory root identity changed");
            entered=true;
        }else if(actual!=renderer)throw fault("Native cosmic inventory delegate identity changed");
        if(calls.size()>8)throw fault("Native cosmic inventory nesting exceeds budget");calls.add(actual);
    });}
    @Override public void exit(Object actual){safe(()->{
        if(calls.isEmpty()||calls.remove(calls.size()-1)!=actual)throw fault("Native cosmic inventory call did not balance");
        if(calls.isEmpty())completed=true;
    });}
    @Override public boolean active(){return failure==null&&!calls.isEmpty();}
    @Override public void gaussian(Random actual,RendererRandom.Snapshot initial,long draw,double value){
        if(actual!=random||pulse!=null||!Double.isFinite(value)||draw<1)throw fault("Unexpected native cosmic random draw");
        pulse=new Pulse(initial,value,matrix());
    }
    @Override public void draw(InventoryDraws.Draw draw){
        if(layers.size()>=64)throw fault("Native inventory draw count exceeds budget");
        int current=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);JsonObject state=InventoryVisual.state();
        JsonArray vertices=new JsonArray();
        if(current==0){
            Pulse next=pulse;pulse=null;
            if(next!=null)canonicalPulse(draw.vertices,next.value);
            for(float[] vertex:draw.vertices)vertices.add(floats(vertex));
            JsonObject data=object("kind","inventoryFlat","vertices",vertices,"state",state);
            if(next!=null)data.add("pulseBasis",next.basis);
            VisualGraph.Node node=VisualGraph.node(data).link("sampler",sampler(draw.vertices));
            if(next!=null)node.link("random",InventoryVisual.randomResource(next.initial));
            layers.add(node);
        }else{
            if(pulse!=null)throw fault("Native pulse unexpectedly uses a shader");
            CosmicShaders.Snapshot shader=CosmicShaders.capture(profile,draw.flags);shaders.add(shader);
            if(shader.values.get("textureUnit").getAsInt()!=0)throw fault("Native cosmic shader uses an unreviewed texture unit");
            List<VisualGraph.Node> samplers=new ArrayList<>();samplers.add(sampler(draw.vertices));
            JsonArray uv=shader.values.getAsJsonArray("cosmicuvs");
            for(int i=0;i<10;i++)samplers.add(images.inventoryTextures.sampler(number(uv,i*4),number(uv,i*4+2),number(uv,i*4+1),number(uv,i*4+3)));
            for(float[] vertex:draw.vertices)vertices.add(floats(vertex));
            layers.add(VisualGraph.node(object("kind","inventoryCosmic","vertices",vertices,"state",state,"uniforms",shader.values))
                    .link("shader",VisualGraph.node(profile.resource().getAsJsonObject("data"))).links("samplers",samplers));
        }
    }
    private VisualGraph.Node sampler(List<float[]> vertices){
        float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,minV=minU,maxV=maxU;
        for(float[] vertex:vertices){minU=Math.min(minU,vertex[6]);maxU=Math.max(maxU,vertex[6]);minV=Math.min(minV,vertex[7]);maxV=Math.max(maxV,vertex[7]);}
        return images.inventoryTextures.sampler(minU,maxU,minV,maxV);
    }
    /** Preserve both native pulse transforms and its offset-expanded quad. */
    private static void canonicalPulse(List<float[]> vertices,double gaussian){
        double scale=gaussian*0.15+0.95,offset=(1-scale)/2;
        if(vertices.size()!=4)throw fault("Native Eternal pulse is not one ordered quad");
        for(int i=0;i<4;i++){
            boolean right=i>=2,bottom=i==1||i==2;float[] vertex=vertices.get(i);
            float x=(float)(right?16+offset:-offset),y=(float)(bottom?16+offset:-offset);
            if(Float.floatToRawIntBits(vertex[0])!=Float.floatToRawIntBits(x)||Float.floatToRawIntBits(vertex[1])!=Float.floatToRawIntBits(y)||vertex[2]!=0)
                throw fault("Native Eternal pulse geometry changed");
            vertex[0]=right?16:0;vertex[1]=bottom?16:0;
        }
    }
    private static float number(JsonArray data,int index){return Float.intBitsToFloat((int)Long.parseLong(data.get(index).getAsString(),16));}
    private static JsonArray matrix(){FloatBuffer values=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,values);float[] data=new float[16];values.get(data);return floats(data);}
    private void safe(Runnable action){if(failure!=null)return;try{action.run();}catch(RuntimeException error){failure=error;}}
    private static final class Pulse {
        final RendererRandom.Snapshot initial;final double value;final JsonArray basis;
        Pulse(RendererRandom.Snapshot initial,double value,JsonArray basis){this.initial=initial;this.value=value;this.basis=basis;}
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("animation_unsupported",message);}
}
