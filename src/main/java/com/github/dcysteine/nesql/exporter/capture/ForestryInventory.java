package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.VisualValues.*;

/** Native Forestry models rather than moving a baked/illuminated screenshot.
 * ModelRenderer display lists may already exist; observe their call sites and
 * copy the actual ModelBox data, pose, binding and lightmap before each call. */
final class ForestryInventory implements InventoryTrace.Observer,ForestryTrace.Observer {
    private final Images images;private final Facts.Icon request;private final IItemRenderer renderer;private final boolean bee;
    private final List<VisualGraph.Node> draws=new ArrayList<>();
    private final List<String> order=new ArrayList<>();private List<String> expected;
    private final Map<ModelRenderer,String> parts=new IdentityHashMap<>();
    private Object individual,entityRenderer,model;private Entity entity;
    private Boolean alive;private Integer hash;private Float partial,flap;
    private JsonObject clock;private ResourceLocation texture;private VisualGraph.Node sampler,lightmap;
    private JsonObject lightmapData;private boolean entered,completed,modelEnded;private RuntimeException failure;
    private net.minecraft.client.renderer.texture.ITextureObject textureOwner;
    private DynamicTexture lightmapOwner;private int lightmapHandle;
    private ModelRenderer pendingPart;
    private JsonObject pendingGeometry,pendingState;private int pendingWing;private VisualGraph.Node pendingLightmap;
    private ForestryInventory(Images images,Facts.Icon request,IItemRenderer renderer){this.images=images;this.request=request;this.renderer=renderer;bee=renderer.getClass().getName().equals(ForestryTrace.BEE_ITEM);}

    static Images.Image capture(Images images,Facts.Icon request,InventoryVisual.Selection selection)throws Exception{
        MagicApi.version("ForestryInventory","Forestry");String type=selection.renderer.getClass().getName();boolean bee=type.equals(ForestryTrace.BEE_ITEM);
        if(selection.registered!=selection.renderer||!InventoryTrace.instrumented(type))throw fault("Forestry inventory renderer binding is not observed");
        for(String hook:new String[]{type,bee?ForestryTrace.BEE_MODEL:ForestryTrace.BUTTERFLY_MODEL,
                bee?ForestryTrace.BEE_ENTITY:ForestryTrace.BUTTERFLY_ENTITY}){
            // Resolve only the selected optional family so its transformed
            // model/entity classes are available before entering NEI's catch.
            Class.forName(hook,false,selection.renderer.getClass().getClassLoader());
            if(!ForestryTrace.instrumented(hook))throw fault("Forestry native hook is unavailable: "+hook);
        }
        if(!bee){Class.forName(ForestryTrace.BUTTERFLY_STATE,false,selection.renderer.getClass().getClassLoader());
            if(!ForestryTrace.instrumented(ForestryTrace.BUTTERFLY_STATE))throw fault("Native butterfly state clock is not observed");}
        ForestryInventory scene=new ForestryInventory(images,request,selection.renderer);
        RenderItem item=RenderItem.getInstance();boolean color=ReflectionHelper.getPrivateValue(RenderItem.class,item,"renderWithColor","field_77024_a");
        byte[] preview;
        try(RendererClock.Scope clock=RendererClock.at(0,1000);RendererRandom.Scope random=images.random.scope();
            InventoryTrace.Scope entry=InventoryTrace.observe(scene);ForestryTrace.Scope trace=ForestryTrace.observe(scene)){
            preview=images.render(request);
        }finally{ReflectionHelper.setPrivateValue(RenderItem.class,item,color,"renderWithColor","field_77024_a");}
        if(scene.failure!=null)throw scene.failure;
        if(!scene.entered||!scene.completed||!scene.modelEnded||scene.clock==null||!scene.order.equals(scene.expected)||scene.draws.isEmpty())
            throw fault("Forestry native inventory did not complete its model and ordered parts");
        VisualGraph.Node root=VisualGraph.node(object("kind","forestryInventory","version","4.10.17","profile",bee?"bee":"butterfly","clock",scene.clock))
                .link("trig",images.sine()).links("draws",scene.draws);
        return new Images.Image(Images.decode(64,64,preview),new JsonArray(),"capture",request.registry,false,new InventoryVisual(root));
    }
    @Override public void enter(Object actual,Object type,Object stack){safe(()->{
        if(entered||actual!=renderer||type!=IItemRenderer.ItemRenderType.INVENTORY||stack!=request.item)throw fault("Unexpected Forestry inventory entry");entered=true;
    });}
    @Override public void exit(Object actual){safe(()->{if(actual!=renderer||!entered||completed||!modelEnded)throw fault("Unexpected Forestry inventory completion");completed=true;});}
    @Override public void individual(Object value){safe(()->{active();if(individual!=null||value==null)throw fault("Forestry individual clock was repeated");individual=value;});}
    @Override public void alive(boolean value){safe(()->{active();if(individual==null||alive!=null)throw fault("Forestry life-state callback was not observed once");alive=value;});}
    @Override public void hash(int value){safe(()->{active();if(!Boolean.TRUE.equals(alive)||hash!=null)throw fault("Unexpected Forestry item identity hash");hash=value;});}
    @Override public void partial(float value){safe(()->{
        active();if(partial!=null||alive==null||alive&&hash==null||!Float.isFinite(value)||!alive&&value!=1F)throw fault("Forestry item clock did not complete");partial=value;
    });}
    @Override public void entity(Object actual,Object value){safe(()->{
        active();if(entityRenderer!=null||!actual.getClass().getName().equals(bee?ForestryTrace.BEE_ENTITY:ForestryTrace.BUTTERFLY_ENTITY)
                ||!(value instanceof Entity)||value!=MagicApi.field(renderer,"entity"))throw fault("Forestry display entity renderer changed");
        entityRenderer=actual;entity=(Entity)value;
        String name=bee?"forestry.apiculture.entities.EntityBee":"forestry.lepidopterology.entities.EntityButterfly";
        if(!entity.getClass().getName().equals(name))throw fault("Unreviewed Forestry display entity");
    });}
    @Override public void texture(Object value){safe(()->{
        active();if(entityRenderer==null||!(value instanceof ResourceLocation)||texture!=null&&!texture.equals(value))throw fault("Forestry entity texture callback changed");texture=(ResourceLocation)value;
    });}
    @Override public void butterfly(Object state,Object value,long stateHash,float nativePartial){safe(()->{
        active();if(bee||value!=entity||partial==null||Float.floatToRawIntBits(nativePartial)!=Float.floatToRawIntBits(partial)||clock!=null
                ||state==null||!state.getClass().getName().equals(ForestryTrace.BUTTERFLY_STATE)||stateHash!=(int)stateHash)
            throw fault("Butterfly entity clock does not bind the observed item clock");
        clock=clock();clock.addProperty("state",((Enum<?>)state).name().toLowerCase(Locale.ROOT));clock.addProperty("stateHash",(int)stateHash);
    });}
    @Override public void flap(float value){safe(()->{active();if(bee||clock==null||flap!=null||!Float.isFinite(value))throw fault("Unexpected butterfly flap result");flap=value;});}
    @Override public void model(Object actual,Object value,float limb,float amount,float swing,float yaw,float pitch,float scale){safe(()->{
        active();if(model!=null||entity==null||value!=entity||partial==null||texture==null||scale!=0.0625F
                ||!actual.getClass().getName().equals(bee?ForestryTrace.BEE_MODEL:ForestryTrace.BUTTERFLY_MODEL)
                ||actual!=MagicApi.field(entityRenderer,bee?"beeModel":"butterflyModel")
                ||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=0)throw fault("Unreviewed Forestry model entry");
        if(bee){clock=clock();if(Float.floatToRawIntBits(swing)!=Float.floatToRawIntBits((float)entity.ticksExisted+partial))throw fault("Bee model clock changed");}
        else if(flap==null||Float.floatToRawIntBits(swing)!=Float.floatToRawIntBits(flap))throw fault("Butterfly model clock changed");
        model=actual;expected=new ArrayList<>(bee?Arrays.asList("snout","torsoWing","rump","tail","tailEnd","bridge","eyeRight","eyeLeft","wingLeft","wingRight"):
                Arrays.asList("wingRight","eyeRight","eyeLeft","wingLeft","body"));
        if(bee){Object type=MagicApi.field(model,"type");String role=type==null?"":((Enum<?>)type).name();if(role.equals("PRINCESS"))expected.add("crownPrincess");else if(role.equals("QUEEN"))expected.add("crownQueen");}
        for(String name:expected){Object part=MagicApi.field(model,name);if(!(part instanceof ModelRenderer)||parts.put((ModelRenderer)part,name)!=null)throw fault("Forestry model part identities changed");}
        NativeVisualSources.fieldIdentity(entityRenderer,bee?"beeModel":"butterflyModel",model);
        for(String name:expected)NativeVisualSources.fieldIdentity(model,name,MagicApi.field(model,name));
    });}
    private JsonObject clock(){return object("alive",alive,"itemHash",hash==null?0:hash,"ticks",entity.ticksExisted);}
    @Override public void part(Object value,float scale){safe(()->{
        active();if(model==null||modelEnded||pendingPart!=null||!(value instanceof ModelRenderer)||scale!=0.0625F||order.size()>=expected.size())throw fault("Unexpected Forestry part draw");
        ModelRenderer part=(ModelRenderer)value;String name=parts.get(part);
        pendingPart=part;
        if(!expected.get(order.size()).equals(name))throw fault("Forestry native model draw order changed");order.add(name);
        JsonObject nativePart=InventoryVisual.part(part);int wing=name.equals("wingRight")?1:name.equals("wingLeft")?-1:0;
        if(wing!=0){float swing=bee?(float)entity.ticksExisted+partial:flap;
            float angle=MathHelper.cos(swing*1.3F)*(float)Math.PI*0.25F;if(wing<0)angle=-angle;
            if(Float.floatToRawIntBits(part.rotateAngleZ)!=Float.floatToRawIntBits(angle))throw fault("Forestry wing pose differs from its native clock");
            nativePart.add("rotation",floats(0F,part.rotateAngleY,part.rotateAngleX));
        }
        if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=0||GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE)!=GL11.GL_MODULATE)
            throw fault("Forestry model uses an unreviewed shader or texture environment");
        JsonObject state=InventoryVisual.state(true);
        VisualGraph.Node currentLightmap=lightmap();
        verifyTexture();if(sampler==null)sampler=NativeSamplers.observed(texture);
        pendingGeometry=nativePart;pendingState=state;pendingWing=wing;pendingLightmap=currentLightmap;
    });}
    @Override public void drawn(Object value){safe(()->{
        active();if(value!=pendingPart||pendingPart==null)throw fault("Forestry model part did not return in order");
        JsonObject geometry=images.inventoryModels.part(pendingPart,pendingGeometry);
        VisualGraph.Node draw=VisualGraph.node(object("kind","forestryPart","wing",pendingWing,"state",pendingState))
                .link("mesh",VisualGraph.node(object("kind","mesh","parts",array(geometry)))).link("sampler",sampler);
        if(pendingLightmap!=null)draw.link("lightmap",pendingLightmap);draws.add(draw);
        pendingPart=null;pendingGeometry=null;pendingState=null;pendingLightmap=null;
    });}
    private void verifyTexture(){
        net.minecraft.client.renderer.texture.ITextureObject actual=Minecraft.getMinecraft().getTextureManager().getTexture(texture);
        if(actual==null||actual.getClass()!=net.minecraft.client.renderer.texture.SimpleTexture.class||textureOwner!=null&&textureOwner!=actual
                ||ReflectionHelper.<Integer,AbstractTexture>getPrivateValue(AbstractTexture.class,(AbstractTexture)actual,"glTextureId","field_110553_a")!=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D))
            throw fault("Forestry model texture binding changed between parts");
        textureOwner=actual;
    }
    /** Lightmap is a distinct constant-coordinate stage, not a sprite alias.
     * Preserve GL_CLAMP's real border behaviour instead of substituting EDGE. */
    private VisualGraph.Node lightmap(){
        int unit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);if(!GL11.glIsEnabled(GL11.GL_TEXTURE_2D)){
                if(lightmapData!=null)throw fault("Forestry lightmap was disabled between parts");return null;
            }
            DynamicTexture owner=ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.EntityRenderer.class,Minecraft.getMinecraft().entityRenderer,"lightmapTexture","field_78513_d");
            int handle=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if(owner==null||owner.getClass()!=DynamicTexture.class||lightmapOwner!=null&&(lightmapOwner!=owner||lightmapHandle!=handle)
                    ||ReflectionHelper.<Integer,AbstractTexture>getPrivateValue(AbstractTexture.class,owner,"glTextureId","field_110553_a")!=handle
                    ||GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE)!=GL11.GL_MODULATE
                    ||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)!=GL11.GL_LINEAR
                    ||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)!=GL11.GL_LINEAR)
                throw fault("Forestry lightmap does not bind the native linear DynamicTexture");
            NativeVisualSources.lightmap(Minecraft.getMinecraft().entityRenderer,owner);
            int s=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S),t=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T);
            if((s!=GL11.GL_CLAMP&&s!=GL12.GL_CLAMP_TO_EDGE)||(t!=GL11.GL_CLAMP&&t!=GL12.GL_CLAMP_TO_EDGE))throw fault("Unreviewed native lightmap wrap");
            JsonObject data=object("kind","inventoryLightmap","matrix",vector(GL11.GL_TEXTURE_MATRIX,16),"coordinate",vector(GL11.GL_CURRENT_TEXTURE_COORDS,4),
                    "border",border(),"wrap",array(s,t));
            if(lightmapData!=null){if(!lightmapData.equals(data))throw fault("Native lightmap state changed between model parts");return lightmap;}
            try(GlState ignored=new GlState("Forestry inventory lightmap")){lightmap=VisualGraph.node(data).image("pixels",NativeSamplers.lightmap("minecraft#inventory-lightmap"));}
            lightmapData=data;lightmapOwner=owner;lightmapHandle=handle;return lightmap;
        }finally{OpenGlHelper.setActiveTexture(unit);}
    }
    private static JsonArray vector(int name,int count){FloatBuffer b=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(name,b);float[] values=new float[count];b.get(values);return floats(values);}
    private static JsonArray border(){FloatBuffer b=BufferUtils.createFloatBuffer(4);GL11.glGetTexParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_BORDER_COLOR,b);float[] values=new float[4];b.get(values);return floats(values);}
    @Override public void end(Object actual){safe(()->{active();if(actual!=model||modelEnded||pendingPart!=null||!order.equals(expected))throw fault("Forestry model did not finish its ordered parts");modelEnded=true;});}
    private void active(){if(!entered||completed)throw fault("Forestry observation outside the selected inventory call");}
    private void safe(Runnable work){if(failure!=null)return;try{work.run();}catch(RuntimeException problem){failure=problem;}}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("animation_unsupported",message);}
}
