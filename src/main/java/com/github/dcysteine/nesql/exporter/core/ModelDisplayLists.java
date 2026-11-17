package com.github.dcysteine.nesql.exporter.core;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.model.*;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/** Capabilities for the geometry actually compiled by native ModelRenderer.
 * Pose is applied outside the display list and deliberately is not sealed.
 * Weak keys/roots do not keep unused display models alive. No GL read occurs
 * here and unknown model families are ignored outside a capture. */
public final class ModelDisplayLists {
    private static final Map<ModelRenderer,Proof> proofs=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ModelRenderer,Pending> pending=Collections.synchronizedMap(new WeakHashMap<>());
    private ModelDisplayLists(){}
    public static void before(Object value,float scale){
        if(value==null||value.getClass()!=ModelRenderer.class)return;ModelRenderer part=(ModelRenderer)value;
        proofs.remove(part);pending.remove(part);
        try{Object model=base(part);if(model==null||!reviewed(model.getClass().getName()))return;
            pending.put(part,new Pending(part,model,scale,signature(part)));
        }catch(RuntimeException ignored){/* Native draw must complete normally. Admission will fail after it. */}
    }
    public static void after(Object value){
        if(value==null||value.getClass()!=ModelRenderer.class)return;ModelRenderer part=(ModelRenderer)value;Pending captured=pending.remove(part);if(captured==null)return;
        try{if(captured.thread!=Thread.currentThread()||captured.model.get()!=base(part)||captured.list.get()!=part.cubeList||!Arrays.equals(captured.geometry,signature(part))||!compiled(part))return;
            int id=id(part);if(id!=0)proofs.put(part,new Proof(captured,id));
        }catch(RuntimeException ignored){/* Invalid native cache is never made an authority. */}
    }
    private static boolean reviewed(String type){return type.equals("micdoodle8.mods.galacticraft.core.client.model.ModelKey")||ForestryTrace.model(type);}
    public static Authority bind(ModelRenderer part,float scale){
        Proof proof=proofs.get(part);if(proof==null||Float.floatToRawIntBits(scale)!=Float.floatToRawIntBits(proof.scale))throw invalid();
        Authority authority=new Authority(part,proof);authority.verify();return authority;
    }
    public static final class Authority {
        private final WeakReference<ModelRenderer> part;private final Proof proof;
        private Authority(ModelRenderer part,Proof proof){this.part=new WeakReference<>(part);this.proof=proof;}
        public void verify(){ModelRenderer value=part.get();if(value==null||proof.thread!=Thread.currentThread()||proofs.get(value)!=proof||proof.list.get()!=value.cubeList
                ||proof.model.get()==null||proof.model.get()!=base(value)||!compiled(value)||id(value)!=proof.id||!Arrays.equals(proof.geometry,signature(value)))throw invalid();}
        public int handle(){verify();return proof.id;}
    }
    private static final class Pending {
        final WeakReference<List<?>> list;final WeakReference<Object> model;final byte[] geometry;final float scale;final Thread thread=Thread.currentThread();
        Pending(ModelRenderer part,Object model,float scale,byte[] geometry){list=new WeakReference<>(part.cubeList);this.model=new WeakReference<>(model);this.scale=scale;this.geometry=geometry;}
    }
    private static final class Proof {
        final WeakReference<List<?>> list;final WeakReference<Object> model;final byte[] geometry;final float scale;final Thread thread;final int id;
        Proof(Pending value,int id){list=value.list;model=value.model;geometry=value.geometry;scale=value.scale;thread=value.thread;this.id=id;}
    }
    private static boolean compiled(ModelRenderer part){return ReflectionHelper.getPrivateValue(ModelRenderer.class,part,"compiled","field_78812_q");}
    private static Object base(ModelRenderer part){return ReflectionHelper.getPrivateValue(ModelRenderer.class,part,"baseModel","field_78810_s");}
    private static int id(ModelRenderer part){return ReflectionHelper.getPrivateValue(ModelRenderer.class,part,"displayList","field_78811_r");}
    private static byte[] signature(ModelRenderer part){
        if(part.getClass()!=ModelRenderer.class||part.cubeList==null||part.cubeList.getClass()!=ArrayList.class||part.cubeList.size()>64)throw invalid();
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");ByteBuffer word=ByteBuffer.allocate(8);integer(digest,word,part.cubeList.size());
            for(Object value:part.cubeList){
                if(value==null||value.getClass()!=ModelBox.class)throw invalid();
                TexturedQuad[] quads=ReflectionHelper.getPrivateValue(ModelBox.class,(ModelBox)value,"quadList","field_78254_i");if(quads==null||quads.length>64)throw invalid();integer(digest,word,quads.length);
                for(TexturedQuad quad:quads){
                    if(quad==null||quad.getClass()!=TexturedQuad.class||quad.nVertices!=4||quad.vertexPositions.length!=4)throw invalid();
                    boolean inverse=ReflectionHelper.getPrivateValue(TexturedQuad.class,quad,"invertNormal","field_78238_c");integer(digest,word,inverse?1:0);
                    for(PositionTextureVertex v:quad.vertexPositions){
                        if(v==null||v.getClass()!=PositionTextureVertex.class||v.vector3D==null)throw invalid();
                        for(double n:new double[]{v.vector3D.xCoord,v.vector3D.yCoord,v.vector3D.zCoord,v.texturePositionX,v.texturePositionY}){
                            if(!Double.isFinite(n))throw invalid();((java.nio.Buffer)word).clear();word.putLong(Double.doubleToRawLongBits(n));digest.update(word.array());
                        }
                    }
                }
            }return digest.digest();
        }catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}
    }
    private static void integer(MessageDigest digest,ByteBuffer word,int value){((java.nio.Buffer)word).clear();word.putLong(value);digest.update(word.array());}
    private static IllegalStateException invalid(){return new IllegalStateException("Native model geometry has no matching compiled display-list authority");}
}
