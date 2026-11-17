package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.item.Item;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Closed read-only getter shapes used by the target items; arbitrary callbacks remain rejected. */
final class NativeItemSubtypes {
    private static final Map<Method,MethodNode> BODIES=new HashMap<>();
    private NativeItemSubtypes() {}
    static boolean read(Item item){return read(item,Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>()));}
    private static boolean read(Item item,Set<Item> seen){
        if(item==null||seen.size()>=8||!seen.add(item))throw fault("Cyclic or missing subtype item dependency");
        try{
            Method getter=ItemCallbacks.method(item,"getHasSubtypes","func_77614_k");
            if(getter.getDeclaringClass()==Item.class)return item.getHasSubtypes();
            List<AbstractInsnNode> code=code(body(getter));int size=code.size();
            if(size==2&&booleanConstant(code.get(0))&&op(code.get(1),Opcodes.IRETURN))return op(code.get(0),Opcodes.ICONST_1);
            if(size==3&&receiver(code.get(0))&&code.get(1) instanceof FieldInsnNode&&op(code.get(2),Opcodes.IRETURN)){
                FieldInsnNode field=(FieldInsnNode)code.get(1);
                if(op(field,Opcodes.GETFIELD)&&field.desc.equals("Z"))return (Boolean)field(item,field);
            }
            if(size==4&&receiver(code.get(0))&&code.get(1) instanceof FieldInsnNode&&code.get(2) instanceof MethodInsnNode&&op(code.get(3),Opcodes.IRETURN)){
                FieldInsnNode field=(FieldInsnNode)code.get(1);MethodInsnNode call=(MethodInsnNode)code.get(2);
                if(op(field,Opcodes.GETFIELD)&&op(call,Opcodes.INVOKEVIRTUAL)&&call.desc.equals("()Z")
                        &&(call.name.equals("getHasSubtypes")||call.name.equals("func_77614_k"))){
                    Object dependency=field(item,field);if(dependency instanceof Item)return read((Item)dependency,seen);
                }
            }
            // int field/getter compared with zero or a literal, followed by two boolean exits.
            int jump=size==7?2:size==8?3:-1;
            if(jump>0&&receiver(code.get(0))&&integerSource(item,code.get(1))
                    &&(jump==2||integerConstant(code.get(2)))&&code.get(jump) instanceof JumpInsnNode){
                JumpInsnNode condition=(JumpInsnNode)code.get(jump),end=code.get(jump+2) instanceof JumpInsnNode?(JumpInsnNode)code.get(jump+2):null;
                int first=jump==2?Opcodes.IFEQ:Opcodes.IF_ICMPEQ,last=jump==2?Opcodes.IFLE:Opcodes.IF_ICMPLE;
                if(condition.getOpcode()>=first&&condition.getOpcode()<=last&&end!=null&&op(end,Opcodes.GOTO)
                        &&booleanConstant(code.get(jump+1))&&booleanConstant(code.get(jump+3))&&op(code.get(jump+4),Opcodes.IRETURN)
                        &&next(condition.label)==code.get(jump+3)&&next(end.label)==code.get(jump+4))return item.getHasSubtypes();
            }
            throw fault("Unadapted subtype predicate: "+getter.getDeclaringClass().getName()+"#"+getter.getName());
        }finally{seen.remove(item);}
    }
    private static boolean integerSource(Item item,AbstractInsnNode instruction){
        if(instruction instanceof FieldInsnNode){FieldInsnNode field=(FieldInsnNode)instruction;return op(field,Opcodes.GETFIELD)&&field.desc.equals("I")&&field(item,field) instanceof Integer;}
        if(!(instruction instanceof MethodInsnNode))return false;
        MethodInsnNode call=(MethodInsnNode)instruction;if(!op(call,Opcodes.INVOKEVIRTUAL)||!call.desc.equals("()I"))return false;
        try{
            List<AbstractInsnNode> code=code(body(item.getClass().getMethod(call.name)));
            if(code.size()==2&&integerConstant(code.get(0))&&op(code.get(1),Opcodes.IRETURN))return true;
            if(code.size()==3&&receiver(code.get(0))&&code.get(1) instanceof FieldInsnNode&&op(code.get(2),Opcodes.IRETURN)){
                FieldInsnNode field=(FieldInsnNode)code.get(1);return op(field,Opcodes.GETFIELD)&&field.desc.equals("I")&&field(item,field) instanceof Integer;
            }
            return false;
        }catch(NoSuchMethodException error){throw fault("Missing native subtype integer property");}
    }
    private static Object field(Item item,FieldInsnNode field){
        Class<?> owner=MagicApi.type(field.owner.replace('/','.'));
        if(!owner.isInstance(item))throw fault("Native subtype field owner differs");
        return MagicApi.field(owner,item,field.name);
    }
    private static boolean receiver(AbstractInsnNode node){return node instanceof VarInsnNode&&op(node,Opcodes.ALOAD)&&((VarInsnNode)node).var==0;}
    private static boolean booleanConstant(AbstractInsnNode node){return op(node,Opcodes.ICONST_0)||op(node,Opcodes.ICONST_1);}
    private static boolean integerConstant(AbstractInsnNode node){return node instanceof InsnNode&&node.getOpcode()>=Opcodes.ICONST_M1&&node.getOpcode()<=Opcodes.ICONST_5||node instanceof IntInsnNode&&(op(node,Opcodes.BIPUSH)||op(node,Opcodes.SIPUSH));}
    private static boolean op(AbstractInsnNode node,int opcode){return node.getOpcode()==opcode;}
    private static AbstractInsnNode next(AbstractInsnNode node){while(node!=null&&node.getOpcode()<0)node=node.getNext();return node;}
    private static List<AbstractInsnNode> code(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode node:method.instructions.toArray())if(node.getOpcode()>=0)result.add(node);return result;}
    private static synchronized MethodNode body(Method getter){
        MethodNode known=BODIES.get(getter);if(known!=null)return known;
        if(BODIES.size()>=1024)throw fault("Subtype property budget exceeded");
        Class<?> owner=getter.getDeclaringClass();
        try(InputStream input=owner.getResourceAsStream("/"+owner.getName().replace('.','/')+".class");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(input==null)throw fault("Missing subtype source: "+owner.getName());
            byte[] buffer=new byte[8192];int size;
            while((size=input.read(buffer))!=-1){if(bytes.size()+size>2*1024*1024)throw fault("Subtype source exceeds budget");bytes.write(buffer,0,size);}
            ClassNode source=new ClassNode();new ClassReader(bytes.toByteArray()).accept(source,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            if(!source.name.equals(owner.getName().replace('.','/')))throw fault("Subtype source class differs");
            for(Object value:source.methods){MethodNode method=(MethodNode)value;
                if(!method.name.equals(getter.getName())||!method.desc.equals(Type.getMethodDescriptor(getter)))continue;
                if((method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_SYNCHRONIZED))!=0||!method.tryCatchBlocks.isEmpty()||code(method).size()>16)break;
                BODIES.put(getter,method);return method;
            }
        }catch(IOException error){throw new IllegalStateException("Cannot read native subtype source",error);}
        throw fault("Unadapted subtype property body: "+owner.getName()+"#"+getter.getName());
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
