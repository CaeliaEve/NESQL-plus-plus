package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryTrace;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.MinecraftForgeClient;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Native roots for inventory programs. Clocks, current entity poses and the
 * DynamicTexture lightmap's time-dependent pixels are capture-time values;
 * immutable source frames and their owners must still match at reuse. */
final class NativeVisualSources {
    static void renderer(Facts.Icon request,InventoryVisual.Selection selected){
        Item item=request.item.getItem();Object root=ReflectionHelper.getPrivateValue(MinecraftForgeClient.class,null,"customItemRenderers");
        if(root==null||root.getClass()!=IdentityHashMap.class||((IdentityHashMap<?,?>)root).get(item)!=selected.registered)throw changed();
        int damage=((Number)field(request.item,"itemDamage")).intValue();String role="renderer"+Integer.toHexString(damage);
        NativeAuthorities.current().remember(item,role,()->{
            if(ReflectionHelper.getPrivateValue(MinecraftForgeClient.class,null,"customItemRenderers")!=root||((IdentityHashMap<?,?>)root).get(item)!=selected.registered)throw changed();
        });
        if(selected.registered!=selected.renderer){
            if(!selected.registered.getClass().getName().equals(InventoryTrace.GT_ITEMS))throw changed();
            String gtRole="gtRenderer"+Integer.toHexString(damage);
            if(!NativeAuthorities.current().contains(item,gtRole))NativeAuthorities.current().remember(item,gtRole,new GtVisualBinding(request.item,selected.registered,selected.renderer));
        }
    }
    static void random(java.util.Random source,Object renderer){
        if(source==null||source.getClass()!=java.util.Random.class||!renderer.getClass().getName().equals(InventoryTrace.ETERNAL)||field(renderer,"rand")!=source)throw changed();
        NativeAuthorities.current().remember(source,"random",()->{if(field(renderer,"rand")!=source)throw changed();});
        NativeAuthorities.current().remember(renderer,"randomSource",()->{if(field(renderer,"rand")!=source)throw changed();});
    }
    static void fieldIdentity(Object owner,String field,Object expected){
        if(owner==null||expected==null||field(owner,field)!=expected)throw changed();
        NativeAuthorities.current().remember(owner,field,()->{if(field(owner,field)!=expected)throw changed();});
    }
    static void array(Object owner,String field,Object[] values){
        if(values==null||values.length>128||field(owner,field)!=values)throw changed();Object[] expected=values.clone();
        NativeAuthorities.current().remember(owner,"array"+field,()->{
            if(field(owner,field)!=values||values.length!=expected.length)throw changed();for(int i=0;i<values.length;i++)if(values[i]!=expected[i])throw changed();
        });
    }
    static void texture(ResourceLocation location){
        ITextureObject owner=Minecraft.getMinecraft().getTextureManager().getTexture(location);
        if(owner==null||!(owner.getClass()==SimpleTexture.class||owner.getClass()==TextureMap.class))throw changed();int handle=id((AbstractTexture)owner);
        String role="texture"+com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(location.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,48);
        NativeAuthorities.current().remember(owner,role,()->{
            if(Minecraft.getMinecraft().getTextureManager().getTexture(location)!=owner||id((AbstractTexture)owner)!=handle||!org.lwjgl.opengl.GL11.glIsTexture(handle))throw changed();
        });
    }
    static void sprite(ResourceLocation atlas,TextureAtlasSprite sprite){
        ITextureObject texture=Minecraft.getMinecraft().getTextureManager().getTexture(atlas);
        if(texture==null||texture.getClass()!=TextureMap.class)throw changed();TextureMap owner=(TextureMap)texture;
        Map<String,TextureAtlasSprite> table=ReflectionHelper.getPrivateValue(TextureMap.class,owner,"mapUploadedSprites","field_94252_e");
        String name=sprite.getIconName();if(table.get(name)!=sprite)throw changed();texture(atlas);
        if(NativeAuthorities.current().contains(sprite,"sprite"))return;
        Object frames=ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"framesTextureData","field_110976_a");
        Object metadata=ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"animationMetadata","field_110982_k");
        if(frames==null||frames.getClass()!=ArrayList.class||metadata!=null&&metadata.getClass()!=net.minecraft.client.resources.data.AnimationMetadataSection.class)throw changed();
        String initial=spriteHash(sprite);
        NativeAuthorities.current().remember(sprite,"sprite",()->{
            if(ReflectionHelper.<Map<String,TextureAtlasSprite>,TextureMap>getPrivateValue(TextureMap.class,owner,"mapUploadedSprites","field_94252_e")!=table
                    ||table.get(name)!=sprite||ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"framesTextureData","field_110976_a")!=frames
                    ||ReflectionHelper.getPrivateValue(TextureAtlasSprite.class,sprite,"animationMetadata","field_110982_k")!=metadata||!initial.equals(spriteHash(sprite)))throw changed();
        });
    }
    static void lightmap(net.minecraft.client.renderer.EntityRenderer renderer,DynamicTexture texture){
        int handle=id(texture);NativeAuthorities.current().remember(texture,"lightmap",()->{
            if(Minecraft.getMinecraft().entityRenderer!=renderer||ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.EntityRenderer.class,renderer,"lightmapTexture","field_78513_d")!=texture
                    ||id(texture)!=handle||!org.lwjgl.opengl.GL11.glIsTexture(handle))throw changed();
        });
    }
    static VisualGraph.Node sine(){
        float[] table=ReflectionHelper.getPrivateValue(MathHelper.class,null,"SIN_TABLE","field_76144_a");String initial=sineHash(table);
        NativeAuthorities.current().remember(MathHelper.class,"sine",()->{
            if(ReflectionHelper.getPrivateValue(MathHelper.class,null,"SIN_TABLE","field_76144_a")!=table||!initial.equals(sineHash(table)))throw changed();
        });
        return VisualGraph.node(com.github.dcysteine.nesql.exporter.source.Json.object("kind","mathHelper","bits",InventoryVisual.sineBits()));
    }
    private static String sineHash(float[] table){
        if(table==null||table.length!=65536)throw changed();
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");ByteBuffer word=ByteBuffer.allocate(16384);
            for(float value:table)number(digest,word,Float.floatToRawIntBits(value));flush(digest,word);return com.github.dcysteine.nesql.exporter.source.CanonicalJson.hex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private static String spriteHash(TextureAtlasSprite sprite){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");ByteBuffer word=ByteBuffer.allocate(16384);
            int count=sprite.getFrameCount();if(count<0||count>4096)throw changed();long pixels=0;
            for(int value:new int[]{sprite.getIconWidth(),sprite.getIconHeight(),sprite.getOriginX(),sprite.getOriginY(),count})number(digest,word,value);
            for(String[] uv:new String[][]{{"minU","field_110979_l"},{"maxU","field_110980_m"},{"minV","field_110977_n"},{"maxV","field_110978_o"}})
                number(digest,word,Float.floatToRawIntBits(ReflectionHelper.<Float,TextureAtlasSprite>getPrivateValue(TextureAtlasSprite.class,sprite,uv)));
            for(int i=0;i<count;i++){
                int[][] levels=sprite.getFrameTextureData(i);number(digest,word,levels==null?-1:levels.length);if(levels==null)continue;
                if(levels.length>13)throw changed();for(int[] level:levels){number(digest,word,level==null?-1:level.length);if(level==null)continue;
                    pixels+=level.length;if(pixels>32L*1024*1024)throw changed();for(int pixel:level)number(digest,word,pixel);
                }
            }
            if(count>1){for(int[] step:Images.timeline(sprite)){number(digest,word,step[0]);number(digest,word,step[1]);}number(digest,word,Images.interpolated(sprite)?1:0);}
            flush(digest,word);return com.github.dcysteine.nesql.exporter.source.CanonicalJson.hex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private static void number(MessageDigest digest,ByteBuffer word,int number){if(word.remaining()<4)flush(digest,word);word.putInt(number);}
    private static void flush(MessageDigest digest,ByteBuffer word){digest.update(word.array(),0,word.position());((java.nio.Buffer)word).clear();}
    private static int id(AbstractTexture texture){return ReflectionHelper.getPrivateValue(AbstractTexture.class,texture,"glTextureId","field_110553_a");}
    private static Jobs.Fault changed(){return new Jobs.Fault("animation_changed","Native inventory visual source authority changed");}
}
