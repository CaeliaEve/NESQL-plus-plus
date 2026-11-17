package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryDrawTrace;
import com.github.dcysteine.nesql.exporter.core.RendererRandom;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Semantic vertex capture for reviewed inventory renderers. Angelica's raw
 * buffer width/extra attributes never determine our offsets. Admission failures
 * are deferred until after NEI's renderer returns, so they do not poison its
 * renderingErrorItems collection. */
final class InventoryDraws implements InventoryDrawTrace.Observer {
    interface Listener {
        boolean active();
        void draw(Draw draw);
        void gaussian(Random source,RendererRandom.Snapshot initial,long draw,double value);
    }
    static final class Draw {
        final List<float[]> vertices;
        final int flags;
        Draw(List<float[]> vertices,int flags){this.vertices=vertices;this.flags=flags;}
    }
    private final Listener listener;
    private final List<Vertex> vertices=new ArrayList<>();
    private Object tessellator;
    private int count;
    private RuntimeException failure;
    InventoryDraws(Listener listener){this.listener=listener;}
    void verify(){if(failure!=null)throw failure;if(tessellator!=null)throw fault("Native inventory tessellation did not finish");}
    @Override public void begin(Object actual,int mode){safe(()->{
        if(!listener.active())return;
        if(tessellator!=null||actual!=Tessellator.instance||actual.getClass()!=Tessellator.class||mode!=GL11.GL_QUADS)
            throw fault("Native inventory primitive requires a dedicated adapter");
        tessellator=actual;vertices.clear();
    });}
    @Override public void vertex(Object actual,double x,double y,double z){safe(()->{
        if(!listener.active())return;
        if(actual!=tessellator||++count>24576)throw fault("Native inventory vertex ownership/budget changed");
        Tessellator tess=(Tessellator)actual;
        int flags=(flag(tess,"hasTexture","field_78400_o")?1:0)|(flag(tess,"hasColor","field_78399_n")?2:0)
                |(flag(tess,"hasNormals","field_78413_q")?4:0)|(flag(tess,"hasBrightness","field_78414_p")?8:0);
        if((flags&1)==0||!vertices.isEmpty()&&vertices.get(0).flags!=flags)
            throw fault("Native inventory enables a vertex array after writing its first vertex");
        double ox=field(tess,"xOffset","field_78408_v"),oy=field(tess,"yOffset","field_78407_w"),oz=field(tess,"zOffset","field_78417_x");
        float[] data=new float[12];data[0]=(float)(x+ox);data[1]=(float)(y+oy);data[2]=(float)(z+oz);
        if((flags&4)!=0){
            int normal=ReflectionHelper.getPrivateValue(Tessellator.class,tess,"normal","field_78416_y");
            for(int i=0;i<3;i++)data[3+i]=Math.max(-1.0F,((byte)(normal>>>(i*8)))/127.0F);
        }
        data[6]=(float)field(tess,"textureU","field_78403_j");data[7]=(float)field(tess,"textureV","field_78404_k");
        if((flags&2)!=0){
            int color=ReflectionHelper.getPrivateValue(Tessellator.class,tess,"color","field_78402_m");
            boolean little=ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN;
            for(int i=0;i<4;i++)data[8+i]=((color>>>(little?i*8:(3-i)*8))&255)/255.0F;
        }
        for(float value:data)VisualValues.bits(value);
        vertices.add(new Vertex(data,flags));
    });}
    @Override public void draw(Object actual){safe(()->{
        if(!listener.active())return;
        if(actual!=tessellator||vertices.isEmpty()||vertices.size()%4!=0)throw fault("Native inventory lost an ordered quad");
        Tessellator tess=(Tessellator)actual;
        int nativeCount=ReflectionHelper.getPrivateValue(Tessellator.class,tess,"vertexCount","field_78406_i");
        int mode=ReflectionHelper.getPrivateValue(Tessellator.class,tess,"drawMode","field_78409_u");
        int flags=(flag(tess,"hasTexture","field_78400_o")?1:0)|(flag(tess,"hasColor","field_78399_n")?2:0)
                |(flag(tess,"hasNormals","field_78413_q")?4:0)|(flag(tess,"hasBrightness","field_78414_p")?8:0);
        if(nativeCount!=vertices.size()||mode!=GL11.GL_QUADS||flags!=vertices.get(0).flags)throw fault("Native tessellation changed after semantic vertices were observed");
        float[] normal=vector(GL11.GL_CURRENT_NORMAL,3),color=vector(GL11.GL_CURRENT_COLOR,4);
        List<float[]> result=new ArrayList<>();
        for(Vertex vertex:vertices){
            float[] data=vertex.data.clone();
            if((vertex.flags&4)==0)System.arraycopy(normal,0,data,3,3);
            if((vertex.flags&2)==0)System.arraycopy(color,0,data,8,4);
            result.add(data);
        }
        tessellator=null;vertices.clear();listener.draw(new Draw(result,flags));
    });}
    @Override public void gaussian(Random source,RendererRandom.Snapshot initial,long draw,double value){
        safe(()->{if(listener.active())listener.gaussian(source,initial,draw,value);});
    }
    private void safe(Runnable action){
        if(failure!=null)return;
        try{Jobs.checkpoint();action.run();}catch(RuntimeException error){failure=error;}
    }
    private static boolean flag(Tessellator tess,String... names){return ReflectionHelper.getPrivateValue(Tessellator.class,tess,names);}
    private static double field(Tessellator tess,String... names){return ReflectionHelper.getPrivateValue(Tessellator.class,tess,names);}
    private static float[] vector(int parameter,int count){FloatBuffer buffer=BufferUtils.createFloatBuffer(4);GL11.glGetFloat(parameter,buffer);float[] data=new float[count];buffer.get(data);return data;}
    private static final class Vertex {final float[] data;final int flags;Vertex(float[] data,int flags){this.data=data;this.flags=flags;}}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("animation_unsupported",message);}
}
