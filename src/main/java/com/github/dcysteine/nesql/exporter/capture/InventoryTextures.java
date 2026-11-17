package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.task.Slice;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.*;

/** One bounded UV index per capture session. Reads native UV fields rather
 * than calling every sprite's getMinU, which marks animations active in Angelica. */
final class InventoryTextures implements AutoCloseable {
    private final List<Atlas> atlases=new ArrayList<>();
    private boolean started,closed;
    boolean prepare() throws Exception {
        if(closed)throw fault("Inventory texture context is closed");
        if(!started){started=true;for(ResourceLocation location:new ResourceLocation[]{TextureMap.locationBlocksTexture,TextureMap.locationItemsTexture})atlases.add(new Atlas(location));}
        for(Atlas atlas:atlases)if(!atlas.prepare())return false;
        return true;
    }
    VisualGraph.Node sampler(float minU,float maxU,float minV,float maxV){
        if(closed||!started)throw fault("Inventory texture context is not ready");
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Bounds bounds=new Bounds(minU,maxU,minV,maxV);
        for(Atlas atlas:atlases)if(atlas.texture==texture)return atlas.sampler(bounds);
        throw fault("Inventory draw uses an unreviewed non-atlas sampler");
    }
    @Override public void close(){if(closed)return;closed=true;for(Atlas atlas:atlases)atlas.index.clear();atlases.clear();}
    private static final class Atlas {
        final ResourceLocation location;final TextureMap nativeAtlas;final Map<String,TextureAtlasSprite> source;
        final List<Entry> entries=new ArrayList<>();final Map<Bounds,List<Entry>> index=new HashMap<>();final int texture;int next;
        Atlas(ResourceLocation location){
            this.location=location;ITextureObject object=Minecraft.getMinecraft().getTextureManager().getTexture(location);
            if(object==null||object.getClass()!=TextureMap.class)throw fault("Native inventory atlas owner changed");
            nativeAtlas=(TextureMap)object;texture=ReflectionHelper.getPrivateValue(AbstractTexture.class,nativeAtlas,"glTextureId","field_110553_a");
            source=ReflectionHelper.getPrivateValue(TextureMap.class,nativeAtlas,"mapUploadedSprites","field_94252_e");
            if(texture<1||source==null||source.size()>100000)throw fault("Native inventory atlas is missing or exceeds sprite budget");
            for(Map.Entry<String,TextureAtlasSprite> entry:source.entrySet())entries.add(new Entry(entry.getKey(),entry.getValue()));
        }
        int chunks(){return (entries.size()+15)/16;}
        boolean prepare() throws Exception {
            check();next=Slice.run(next,chunks(),position->{
                for(int i=position*16;i<Math.min(entries.size(),position*16+16);i++){
                    Entry entry=entries.get(i);if(entry.sprite==null)throw fault("Native atlas contains a null sprite");
                    Bounds bounds=bounds(entry.sprite);List<Entry> same=index.computeIfAbsent(bounds,ignored->new ArrayList<>());
                    if(same.stream().noneMatch(value->value.sprite==entry.sprite))same.add(entry);
                }
                return 16*64;
            });return next==chunks();
        }
        VisualGraph.Node sampler(Bounds key){
            check();if(next!=chunks())throw fault("Native inventory atlas index is incomplete");
            List<Entry> matches=index.get(key);
            if(matches==null||matches.size()!=1)throw fault("Inventory UVs do not bind one native sprite");
            Entry entry=matches.get(0);
            if(source.get(entry.name)!=entry.sprite||!key.equals(bounds(entry.sprite)))throw fault("Native inventory sprite changed after indexing");
            return NativeSamplers.sprite(location,entry.sprite);
        }
        void check(){
            if(Minecraft.getMinecraft().getTextureManager().getTexture(location)!=nativeAtlas
                    ||ReflectionHelper.<Map<String,TextureAtlasSprite>,TextureMap>getPrivateValue(TextureMap.class,nativeAtlas,"mapUploadedSprites","field_94252_e")!=source
                    ||source.size()!=entries.size()||ReflectionHelper.<Integer,AbstractTexture>getPrivateValue(AbstractTexture.class,nativeAtlas,"glTextureId","field_110553_a")!=texture)
                throw fault("Native inventory atlas authority changed");
        }
    }
    private static Bounds bounds(TextureAtlasSprite sprite){
        return new Bounds(ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"minU","field_110979_l"),
                ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"maxU","field_110980_m"),
                ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"minV","field_110977_n"),
                ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"maxV","field_110978_o"));
    }
    private static final class Entry {final String name;final TextureAtlasSprite sprite;Entry(String name,TextureAtlasSprite sprite){this.name=name;this.sprite=sprite;}}
    private static final class Bounds {
        final int[] values;
        Bounds(float minU,float maxU,float minV,float maxV){values=new int[]{Float.floatToRawIntBits(minU),Float.floatToRawIntBits(maxU),Float.floatToRawIntBits(minV),Float.floatToRawIntBits(maxV)};}
        @Override public int hashCode(){return Arrays.hashCode(values);}
        @Override public boolean equals(Object other){return other instanceof Bounds&&Arrays.equals(values,((Bounds)other).values);}
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("animation_unsupported",message);}
}
