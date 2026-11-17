package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.*;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.VisualValues.*;

/** Uploaded native mip levels, kept separate from icon previews. Sprite reads
 * touch only their used rectangle; no whole stitched atlas is downloaded.
 * Interpolated uploads are evaluated with the installed Java double operation
 * order, not browser blending, generated mipmaps or premultiplied canvas reads. */
final class NativeSamplers {
    /** Read the observed binding, without re-binding an expected texture and
     * thereby hiding a renderer/resource-pack binding mismatch. */
    static VisualGraph.Node observed(ResourceLocation location){
        net.minecraft.client.renderer.texture.ITextureObject texture=Minecraft.getMinecraft().getTextureManager().getTexture(location);
        if(texture==null||texture.getClass()!=net.minecraft.client.renderer.texture.SimpleTexture.class
                ||ReflectionHelper.<Integer,net.minecraft.client.renderer.texture.AbstractTexture>getPrivateValue(
                        net.minecraft.client.renderer.texture.AbstractTexture.class,(net.minecraft.client.renderer.texture.AbstractTexture)texture,"glTextureId","field_110553_a")!=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D))
            throw fault("Observed native model texture does not match its registered SimpleTexture");
        NativeVisualSources.texture(location);
        try(GlState ignored=new GlState(location.toString())){return bound(location.toString());}
    }
    static VisualGraph.Node full(ResourceLocation location) {
        try (GlState ignored = new GlState(location.toString())) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            Minecraft.getMinecraft().getTextureManager().bindTexture(location);
            NativeVisualSources.texture(location);
            return bound(location.toString());
        }
    }
    private static VisualGraph.Node bound(String location){
            int width=dimension(0,GL11.GL_TEXTURE_WIDTH),height=dimension(0,GL11.GL_TEXTURE_HEIGHT);
            if(width<1||height<1||width>4096||height>4096)throw fault("Invalid native standalone texture dimensions");
            Sampling sampling=sampling(width,height);
            List<int[]> levels=new ArrayList<>();
            long pixels=0;
            for(int level=0;level<=sampling.maxLevel;level++){
                int w=Math.max(1,width>>level),h=Math.max(1,height>>level);
                pixels=Math.addExact(pixels,(long)w*h);if(pixels>16L*1024*1024)throw fault("Native mip chain exceeds pixel budget");
                levels.add(read(level,0,0,w,h));
            }
            Packed packed=pack(width,height,Collections.singletonList(levels.toArray(new int[levels.size()][])),new int[][]{{0,1}},false,location+"#uploaded");
            return resource(width,height,width,height,0,0,sampling,packed,0);
    }
    static Images.Image lightmap(String location){
        if(dimension(0,GL11.GL_TEXTURE_WIDTH)!=16||dimension(0,GL11.GL_TEXTURE_HEIGHT)!=16)throw fault("Native lightmap must retain its uploaded 16 by 16 pixels");
        int[] pixels=read(0,0,0,16,16);BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,16,16,pixels,0,16);
        return new Images.Image(image,new JsonArray(),"capture",location);
    }

    static VisualGraph.Node sprite(ResourceLocation atlas,TextureAtlasSprite sprite) {
        if(sprite.getClass()!=TextureAtlasSprite.class&&!sprite.getClass().getName().equals("jss.notfine.render.InterpolatedIcon"))
            throw fault("Native sprite callback requires a dedicated adapter: "+sprite.getClass().getName());
        if(sprite.getClass()!=TextureAtlasSprite.class)MagicApi.version("NativeSamplers","angelica");
        NativeVisualSources.sprite(atlas,sprite);
        try(GlState ignored=new GlState(sprite.getIconName())){
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            Minecraft.getMinecraft().getTextureManager().bindTexture(atlas);
            int aw=dimension(0,GL11.GL_TEXTURE_WIDTH),ah=dimension(0,GL11.GL_TEXTURE_HEIGHT);
            int width=sprite.getIconWidth(),height=sprite.getIconHeight(),x=sprite.getOriginX(),y=sprite.getOriginY();
            if(width<1||height<1||width>4096||height>4096||x<0||y<0||(long)x+width>aw||(long)y+height>ah)
                throw fault("Invalid native sprite region");
            Sampling sampling=sampling(aw,ah);
            // Nearest within each native level never samples a neighbour. A
            // blurred atlas needs an explicit neighbour/gutter dependency;
            // clamping it silently would alter the renderer's content pixels.
            int min=sampling.filter.get("min").getAsInt(),mag=sampling.filter.get("mag").getAsInt();
            if(mag!=GL11.GL_NEAREST||!(min==GL11.GL_NEAREST||min==GL11.GL_NEAREST_MIPMAP_NEAREST||min==GL11.GL_NEAREST_MIPMAP_LINEAR))
                throw fault("Blurred native sprite requires its atlas-neighbour region");
            int alignment=1<<sampling.maxLevel;
            if(width<alignment||height<alignment||x%alignment!=0||y%alignment!=0||width%alignment!=0||height%alignment!=0)
                throw fault("Native sprite mip footprint crosses another atlas region");
            List<int[][]> data=new ArrayList<>();int[][] timeline;int phase=0;
            if(sprite.getFrameCount()>1){
                String type=sprite.getClass().getName();
                boolean interpolated=Images.interpolated(sprite);
                if(sprite.getClass()!=TextureAtlasSprite.class&&!type.equals("jss.notfine.render.InterpolatedIcon"))
                    throw fault("Unreviewed dynamic sprite callback: "+type);
                timeline=Images.timeline(sprite);
                int frame=ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"frameCounter","field_110973_g");
                int tick=ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"tickCounter","field_110983_h");
                phase=phase(timeline,frame,tick);
                Map<Integer,Integer> positions=new LinkedHashMap<>();
                int[][] remapped=new int[timeline.length][2];
                for(int step=0;step<timeline.length;step++){
                    int index=timeline[step][0];Integer position=positions.get(index);
                    if(position==null){
                        if((long)(positions.size()+1)*width*height>16L*1024*1024)throw fault("Native sprite source frames exceed pixel budget");
                        position=positions.size();positions.put(index,position);
                        int[][] nativeLevels=sprite.getFrameTextureData(index),copy=new int[sampling.maxLevel+1][];
                        for(int level=0;level<copy.length;level++){
                            int length=(width>>level)*(height>>level);
                            if(nativeLevels==null||level>=nativeLevels.length||nativeLevels[level]==null||nativeLevels[level].length!=length)
                                throw fault("Native sprite mip frame is missing");
                            copy[level]=nativeLevels[level].clone();
                        }
                        data.add(copy);
                    }
                    remapped[step]=new int[]{position,timeline[step][1]};
                }
                int[][] uploaded=new int[sampling.maxLevel+1][];
                for(int level=0;level<uploaded.length;level++)uploaded[level]=read(level,x>>level,y>>level,width>>level,height>>level);
                int hold=nextUpload(remapped,phase,interpolated);
                if(hold==0)return resource(width,height,aw,ah,x,y,sampling,
                        pack(width,height,Collections.singletonList(uploaded),new int[][]{{0,1}},false,atlas+"#"+sprite.getIconName()),0);
                Packed packed=pack(width,height,data,remapped,interpolated,atlas+"#"+sprite.getIconName(),uploaded,hold);
                return resource(width,height,aw,ah,x,y,sampling,packed,phase);
            }
            int[][] levels=new int[sampling.maxLevel+1][];
            for(int level=0;level<levels.length;level++)levels[level]=read(level,x>>level,y>>level,width>>level,height>>level);
            data.add(levels);timeline=new int[][]{{0,1}};
            return resource(width,height,aw,ah,x,y,sampling,pack(width,height,data,timeline,false,atlas+"#"+sprite.getIconName()),0);
        }
    }

    private static VisualGraph.Node resource(int width,int height,int aw,int ah,int x,int y,Sampling sampling,Packed packed,int phase){
        JsonObject data=object("kind","nativeSampler","size",array(width,height),"atlas",array(aw,ah),"origin",array(x,y),
                "filter",sampling.filter,"lod",sampling.lod,"frames",packed.frames,"columns",packed.columns,
                "timeline",packed.timeline,"phase",phase);
        if(packed.anchor!=null)data.add("anchor",packed.anchor);
        return VisualGraph.node(data).images("levels",packed.levels);
    }

    static final class Packed {
        final List<Images.Image> levels;final JsonArray timeline;final int frames,columns;final JsonObject anchor;
        Packed(List<Images.Image> levels,JsonArray timeline,int frames,int columns,JsonObject anchor){this.levels=levels;this.timeline=timeline;this.frames=frames;this.columns=columns;this.anchor=anchor;}
    }
    static Packed pack(int width,int height,List<int[][]> data,int[][] steps,boolean interpolation,String location){
        return pack(width,height,data,steps,interpolation,location,null,0);
    }
    private static Packed pack(int width,int height,List<int[][]> data,int[][] steps,boolean interpolation,String location,int[][] uploaded,int hold){
        if(width<1||height<1||width>4096||height>4096||data.isEmpty()||data.size()>4096||steps.length<1||steps.length>4096)
            throw fault("Native sampler frame census exceeds budget");
        int mipCount=data.get(0).length,cycle=0;
        if(mipCount<1||mipCount>13)throw fault("Invalid native sampler mip census");
        for(int[] step:steps){
            if(step==null||step.length!=2||step[0]<0||step[0]>=data.size()||step[1]<1||step[1]>72000)throw fault("Invalid native sampler timeline");
            cycle=Math.addExact(cycle,step[1]);
        }
        if(cycle>72000||interpolation&&cycle>4096)throw fault("Native sampler timeline exceeds its tick budget");
        int count=(interpolation?cycle:data.size())+(uploaded==null?0:1),columns=Math.min(count,Math.max(1,4096/width)),rows=(count+columns-1)/columns;
        if((long)rows*height>4096)throw fault("Native sampler frame sheet exceeds dimensions");
        long pixels=0;List<Images.Image> images=new ArrayList<>();
        for(int level=0;level<mipCount;level++){
            int w=Math.max(1,width>>level),h=Math.max(1,height>>level);
            pixels=Math.addExact(pixels,(long)w*h*columns*rows);if(pixels>16L*1024*1024)throw fault("Native sampler mip sheets exceed pixel budget");
            images.add(new Images.Image(new BufferedImage(w*columns,h*rows,BufferedImage.TYPE_INT_ARGB),new JsonArray(),"capture",location+"#mip="+level));
        }
        for(int[][] frame:data){
            if(frame==null||frame.length!=mipCount)throw fault("Native sampler frame lost mip levels");
            for(int level=0;level<mipCount;level++)if(frame[level]==null||frame[level].length!=Math.max(1,width>>level)*Math.max(1,height>>level))
                throw fault("Native sampler mip dimensions changed");
        }
        JsonArray timeline=new JsonArray();
        if(interpolation){
            int target=0;
            for(int i=0;i<steps.length;i++)for(int tick=0;tick<steps[i][1];tick++){
                Jobs.checkpoint();int[][] first=data.get(steps[i][0]),next=data.get(steps[(i+1)%steps.length][0]);
                for(int level=0;level<mipCount;level++){
                    int[] colors=first[level].clone();
                    if(tick!=0&&steps[i][0]!=steps[(i+1)%steps.length][0])
                        for(int p=0;p<colors.length;p++)colors[p]=interpolate(colors[p],next[level][p],tick,steps[i][1]);
                    place(images.get(level).pixels,width,height,level,columns,target,colors);
                }
                timeline.add(object("frame",target++,"ticks",1));
            }
        }else{
            for(int index=0;index<data.size();index++)for(int level=0;level<mipCount;level++)
                place(images.get(level).pixels,width,height,level,columns,index,data.get(index)[level]);
            for(int[] step:steps)timeline.add(object("frame",step[0],"ticks",step[1]));
        }
        if(uploaded!=null){
            if(uploaded.length!=mipCount||hold<1||hold>cycle)throw fault("Invalid native texture upload anchor");
            for(int level=0;level<mipCount;level++){
                if(uploaded[level]==null||uploaded[level].length!=Math.max(1,width>>level)*Math.max(1,height>>level))throw fault("Native texture anchor lost a mip");
                place(images.get(level).pixels,width,height,level,columns,count-1,uploaded[level]);
            }
        }
        return new Packed(images,timeline,count,columns,uploaded==null?null:object("frame",count-1,"ticks",hold));
    }
    private static void place(BufferedImage sheet,int width,int height,int level,int columns,int frame,int[] colors){
        int w=Math.max(1,width>>level),h=Math.max(1,height>>level);
        sheet.setRGB(frame%columns*w,frame/columns*h,w,h,colors,0,w);
    }
    /** Installed InterpolatedIcon's exact operation order; alpha is not mixed. */
    static int interpolate(int first,int next,int tick,int duration){
        if(tick<0||duration<1||tick>=duration)throw new IllegalArgumentException("Invalid native interpolation phase");
        double amount=1.0D-tick/(double)duration;
        int r=(int)(((first>>>16)&255)*amount+((next>>>16)&255)*(1.0D-amount));
        int g=(int)(((first>>>8)&255)*amount+((next>>>8)&255)*(1.0D-amount));
        int b=(int)((first&255)*amount+(next&255)*(1.0D-amount));
        return (first&0xff000000)|r<<16|g<<8|b;
    }
    static int phase(int[][] timeline,int frame,int tick){
        if(frame<0||frame>=timeline.length||tick<0||tick>=timeline[frame][1])throw new IllegalArgumentException("Invalid native sampler phase");
        int value=tick;for(int i=0;i<frame;i++)value=Math.addExact(value,timeline[i][1]);return value;
    }
    /** Optimised texture animation can advance counters without uploading an
     * unused sprite. Keep its actual GPU pixels until the next native upload;
     * the first visible frame must not be replaced with an inferred CPU frame. */
    static int nextUpload(int[][] timeline,int phase,boolean interpolation){
        int cycle=0;for(int[] step:timeline)cycle=Math.addExact(cycle,step[1]);
        if(phase<0||phase>=cycle)throw new IllegalArgumentException("Invalid native upload phase");
        int index=0,tick=phase;
        while(tick>=timeline[index][1])tick-=timeline[index++][1];
        for(int elapsed=1;elapsed<=cycle;elapsed++){
            int before=timeline[index][0];
            if(++tick>=timeline[index][1]){
                tick=0;index=(index+1)%timeline.length;
                if(timeline[index][0]!=before)return elapsed;
            }
            if(interpolation&&timeline[index][0]!=timeline[(index+1)%timeline.length][0])return elapsed;
        }
        return 0;
    }

    private static final class Sampling {
        final JsonObject filter,lod;final int maxLevel;
        Sampling(JsonObject filter,JsonObject lod,int maxLevel){this.filter=filter;this.lod=lod;this.maxLevel=maxLevel;}
    }
    private static Sampling sampling(int width,int height){
        int base=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL);
        int limit=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL);
        if(base!=0||limit<0)throw fault("Native sampler base level needs a separate adapter");
        if(GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic&&GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT)!=1.0F)
            throw fault("Native anisotropic sampling needs a reviewed filter program");
        int min=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER),max=0;
        if(min!=GL11.GL_NEAREST&&min!=GL11.GL_LINEAR){
            for(int level=1;level<=Math.min(limit,12);level++){
                int w=dimension(level,GL11.GL_TEXTURE_WIDTH),h=dimension(level,GL11.GL_TEXTURE_HEIGHT);
                if(w==0||h==0)break;
                if(w!=Math.max(1,width>>level)||h!=Math.max(1,height>>level))throw fault("Native mip dimensions changed");
                max=level;if(w==1&&h==1)break;
            }
            if(max<Math.min(limit,31-Integer.numberOfLeadingZeros(Math.max(width,height))))
                throw fault("Native mipmapped texture is incomplete");
        }
        float bias=GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_LOD_BIAS)
                +GL11.glGetTexEnvf(GL14.GL_TEXTURE_FILTER_CONTROL,GL14.GL_TEXTURE_LOD_BIAS);
        float bound=GL11.glGetFloat(GL14.GL_MAX_TEXTURE_LOD_BIAS);bias=Math.max(-bound,Math.min(bound,bias));
        return new Sampling(object("min",min,"mag",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER),
                "s",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S),"t",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T)),
                object("min",bits(GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD)),
                        "max",bits(GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD)),"bias",bits(bias)),max);
    }
    private static int dimension(int level,int parameter){return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,parameter);}

    /** Readback rows follow native texture coordinates, never framebuffer-flip. */
    private static int[] read(int level,int x,int y,int width,int height){
        Jobs.checkpoint();int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),fbo=GL30.glGenFramebuffers();
        try{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,level);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw fault("Cannot read native mip region");
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            ByteBuffer bytes=BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width,height),4));
            GL11.glReadPixels(x,y,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
            int[] pixels=new int[width*height];
            for(int p=0;p<pixels.length;p++){int i=p*4;pixels[p]=(bytes.get(i+3)&255)<<24|(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|bytes.get(i+2)&255;}
            return pixels;
        }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);GL30.glDeleteFramebuffers(fbo);}
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("animation_unsupported",message);}
}
