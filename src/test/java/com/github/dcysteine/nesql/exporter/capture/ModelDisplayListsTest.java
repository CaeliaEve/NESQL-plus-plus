package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.ModelDisplayLists;
import com.github.dcysteine.nesql.exporter.core.BufferUploads;
import com.github.dcysteine.nesql.exporter.core.RendererTransformer;
import net.minecraft.client.model.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import org.objectweb.asm.*;

/** A geometry edit after native list compilation must invalidate its authority;
 * a dynamic pose edit must not. No graphics context or GL mock is involved. */
final class ModelDisplayListsTest {
    static void run() throws Exception {
        String name="micdoodle8.mods.galacticraft.core.client.model.ModelKey";
        ClassWriter writer=new ClassWriter(0);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name.replace('.','/'),null,"net/minecraft/client/model/ModelBase",null);
        MethodVisitor init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/model/ModelBase","<init>","()V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(1,1);init.visitEnd();writer.visitEnd();
        byte[] bytes=writer.toByteArray();Class<?> type=new ClassLoader(ModelDisplayListsTest.class.getClassLoader()){Class<?> define(){return defineClass(name,bytes,0,bytes.length);}}.define();
        ModelRenderer part=new ModelRenderer((ModelBase)type.newInstance(),0,0);part.addBox(0,0,0,1,1,1);
        ModelDisplayLists.before(part,0.0625F);
        ReflectionHelper.setPrivateValue(ModelRenderer.class,part,true,"compiled","field_78812_q");
        ReflectionHelper.setPrivateValue(ModelRenderer.class,part,17,"displayList","field_78811_r");ModelDisplayLists.after(part);
        ModelDisplayLists.Authority proof=ModelDisplayLists.bind(part,0.0625F);proof.verify();part.rotateAngleZ=0.7F;proof.verify();
        TexturedQuad[] quads=ReflectionHelper.getPrivateValue(ModelBox.class,(ModelBox)part.cubeList.get(0),"quadList","field_78254_i");
        quads[0].vertexPositions[0].vector3D.xCoord+=1;
        try{proof.verify();throw new AssertionError("A changed model was accepted for its stale display list");}catch(IllegalStateException expected){}
        bufferUploads();
    }
    private static void bufferUploads() throws Exception {
        String name="com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer";ClassWriter writer=new ClassWriter(0);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name.replace('.','/'),null,"java/lang/Object",null);
        MethodVisitor init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(1,1);init.visitEnd();
        for(String[] method:new String[][]{{"upload","(Ljava/nio/ByteBuffer;I)V"},{"close","()V"}}){MethodVisitor code=writer.visitMethod(Opcodes.ACC_PUBLIC,method[0],method[1],null,null);
            code.visitCode();code.visitInsn(Opcodes.RETURN);code.visitMaxs(0,3);code.visitEnd();}writer.visitEnd();
        RendererTransformer transformer=new RendererTransformer();byte[] bytes=transformer.transform(name,name,writer.toByteArray());
        if(!java.util.Arrays.equals(bytes,transformer.transform(name,name,bytes)))throw new AssertionError("VBO upload hook duplicated");
        Class<?> type=new ClassLoader(ModelDisplayListsTest.class.getClassLoader()){Class<?> define(){return defineClass(name,bytes,0,bytes.length);}}.define();Object buffer=type.newInstance();
        type.getMethod("upload",java.nio.ByteBuffer.class,int.class).invoke(buffer,java.nio.ByteBuffer.allocate(24),1);Object first=BufferUploads.revision(buffer);
        type.getMethod("upload",java.nio.ByteBuffer.class,int.class).invoke(buffer,java.nio.ByteBuffer.allocate(24),1);
        if(first==BufferUploads.revision(buffer))throw new AssertionError("A new native VBO upload retained the stale cache epoch");
        Object second=BufferUploads.revision(buffer);type.getMethod("close").invoke(buffer);
        if(second==BufferUploads.revision(buffer))throw new AssertionError("Closing a VBO retained its live epoch");
        java.nio.ByteBuffer vertices=java.nio.ByteBuffer.allocate(96).order(java.nio.ByteOrder.nativeOrder());
        for(int i=0;i<4;i++){vertices.putFloat(i+0.25F).putFloat(2).putFloat(3).putFloat(0.5F).putFloat(0.75F).put((byte)127).put((byte)0).put((byte)-127).put((byte)0);}vertices.flip();
        com.google.gson.JsonArray triangles=NativeModelBuffers.vertices(vertices,4);
        if(triangles.size()!=6||!triangles.get(3).equals(triangles.get(0))||!triangles.get(4).equals(triangles.get(2))
                ||!triangles.get(5).getAsJsonArray().get(0).getAsString().equals("40500000")
                ||!triangles.get(0).getAsJsonArray().get(5).getAsString().equals("bf800000"))throw new AssertionError("Native VBO positions, signed normals or quad order changed");
    }
}
