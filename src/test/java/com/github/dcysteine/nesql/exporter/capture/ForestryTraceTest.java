package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.ForestryTrace;
import com.github.dcysteine.nesql.exporter.core.RendererTransformer;
import org.objectweb.asm.*;
import java.util.*;

/** A model entry must preserve all six native float arguments and balance its
 * observation without running a Forestry getter or requiring an OpenGL mock. */
final class ForestryTraceTest {
    static void run() throws Exception {
        String name=ForestryTrace.BUTTERFLY_MODEL;
        ClassWriter writer=new ClassWriter(0);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name.replace('.','/'),null,"java/lang/Object",null);
        writer.visitField(Opcodes.ACC_PUBLIC,"part","Lnet/minecraft/client/model/ModelRenderer;",null,null).visitEnd();
        MethodVisitor init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(1,1);init.visitEnd();
        MethodVisitor render=writer.visitMethod(Opcodes.ACC_PUBLIC,"render","(Lnet/minecraft/entity/Entity;FFFFFF)V",null,null);
        render.visitCode();render.visitVarInsn(Opcodes.ALOAD,0);render.visitFieldInsn(Opcodes.GETFIELD,name.replace('.','/'),"part","Lnet/minecraft/client/model/ModelRenderer;");
        render.visitVarInsn(Opcodes.FLOAD,7);render.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/client/model/ModelRenderer","render","(F)V",false);
        render.visitInsn(Opcodes.RETURN);render.visitMaxs(2,8);render.visitEnd();writer.visitEnd();
        RendererTransformer transformer=new RendererTransformer();byte[] original=writer.toByteArray(),bytes=transformer.transform(name,name,original);
        require(!Arrays.equals(original,bytes)&&Arrays.equals(bytes,transformer.transform(name,name,bytes)),"Forestry model hook is missing or duplicated");
        Class<?> type=new ClassLoader(ForestryTraceTest.class.getClassLoader()){Class<?> define(){return defineClass(name,bytes,0,bytes.length);}}.define();
        Object model=type.newInstance();List<String> events=new ArrayList<>();
        net.minecraft.client.model.ModelRenderer part=new net.minecraft.client.model.ModelRenderer(new net.minecraft.client.model.ModelBase(){}){
            @Override public void render(float scale){require(scale==0.0625F,"Native part scale changed");events.add("native");}
        };
        type.getField("part").set(model,part);
        ForestryTrace.Observer observer=new ForestryTrace.Observer(){
            @Override public void model(Object actual,Object entity,float limb,float amount,float swing,float yaw,float pitch,float scale){
                require(actual==model&&entity==null&&limb==1&&amount==2&&swing==3&&yaw==4&&pitch==5&&scale==0.0625F,"Native model arguments were substituted");events.add("model");
            }
            @Override public void part(Object actual,float scale){require(actual==part&&scale==0.0625F,"Native part operands changed");events.add("part");}
            @Override public void drawn(Object actual){require(actual==part&&events.get(events.size()-1).equals("native"),"Model authority was checked before the native draw");events.add("drawn");}
            @Override public void end(Object actual){require(actual==model,"Wrong model completed");events.add("end");}
        };
        java.lang.reflect.Method method=type.getMethod("render",net.minecraft.entity.Entity.class,float.class,float.class,float.class,float.class,float.class,float.class);
        try(ForestryTrace.Scope scope=ForestryTrace.observe(observer)){
            method.invoke(model,null,1F,2F,3F,4F,5F,0.0625F);
            Thread other=new Thread(()->ForestryTrace.end(model));other.start();other.join();
        }
        ForestryTrace.end(model);require(events.equals(Arrays.asList("model","part","native","drawn","end")),"Forestry observer escaped its client scope");
        ForestryTrace.Scope outer=ForestryTrace.observe(observer),inner=ForestryTrace.observe(observer);
        try{outer.close();throw new AssertionError("Out-of-order Forestry scope was accepted");}catch(IllegalStateException expected){}
        inner.close();outer.close();
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
