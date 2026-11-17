package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryDrawTrace;
import com.github.dcysteine.nesql.exporter.core.RendererTransformer;
import org.objectweb.asm.*;
import java.util.ArrayList;
import java.util.List;

/** Executable semantic hooks, without a rawBuffer layout or an OpenGL mock. */
public final class InventoryDrawTraceTest {
    public static void run() throws Exception {
        String name="net.minecraft.client.renderer.Tessellator";ClassWriter writer=new ClassWriter(0);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name.replace('.','/'),null,"java/lang/Object",null);
        MethodVisitor init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(1,1);init.visitEnd();
        for(String[] method:new String[][]{{"startDrawing","(I)V"},{"addVertex","(DDD)V"},{"draw","()I"}}){
            MethodVisitor code=writer.visitMethod(Opcodes.ACC_PUBLIC,method[0],method[1],null,null);code.visitCode();
            if(method[0].equals("draw")){code.visitIntInsn(Opcodes.BIPUSH,123);code.visitInsn(Opcodes.IRETURN);}else code.visitInsn(Opcodes.RETURN);
            code.visitMaxs(1,method[0].equals("addVertex")?7:2);code.visitEnd();
        }
        writer.visitEnd();RendererTransformer transformer=new RendererTransformer();byte[] original=writer.toByteArray(),bytes=transformer.transform(name,name,original);
        require(!java.util.Arrays.equals(original,bytes)&&java.util.Arrays.equals(bytes,transformer.transform(name,name,bytes)),"Semantic hook transform is absent or not idempotent");
        Class<?> type=new ClassLoader(InventoryDrawTraceTest.class.getClassLoader()){Class<?> define(){return defineClass(name,bytes,0,bytes.length);}}.define();
        Object tess=type.newInstance();List<String> events=new ArrayList<>();
        InventoryDrawTrace.Observer observer=new InventoryDrawTrace.Observer(){
            @Override public void begin(Object actual,int mode){require(actual==tess&&mode==7,"Native draw mode changed");events.add("begin");}
            @Override public void vertex(Object actual,double x,double y,double z){require(actual==tess&&x==1.25&&y==-2.5&&z==3,"Native coordinates were rounded before observation");events.add("vertex");}
            @Override public void draw(Object actual){require(actual==tess,"Native tessellator identity changed");events.add("draw");}
        };
        try(InventoryDrawTrace.Scope scope=InventoryDrawTrace.observe(observer)){
            type.getMethod("startDrawing",int.class).invoke(tess,7);type.getMethod("addVertex",double.class,double.class,double.class).invoke(tess,1.25,-2.5,3.0);
            require((int)type.getMethod("draw").invoke(tess)==123,"Native draw return value was replaced");
            Thread other=new Thread(()->InventoryDrawTrace.begin(tess,7));other.start();other.join();
        }
        InventoryDrawTrace.begin(tess,7);require(events.equals(java.util.Arrays.asList("begin","vertex","draw")),"Observer leaked outside its client scope");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
