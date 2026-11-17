package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** OC's reviewed default trait bridges and ink registry lookup, without arbitrary delegate calls. */
final class NativeOcContainers {
  private static final String BASE="li.cil.oc.common.item.";
  private static final Map<Method,Boolean> BRIDGES=new HashMap<>();
  private NativeOcContainers() {}
  static boolean supports(Item item) { return item.getClass().getName().equals("li.cil.oc.common.init.Items$$anon$1"); }
  static ItemStack container(ItemStack input, boolean ignoreMeta, boolean direct) {
    MagicApi.version("NativeOcContainers","OpenComputers"); NativeStackAudit.auditRaw(input);
    Item item=input.getItem();Class<?> owner=type(BASE+"Delegator");
    if(ignoreMeta||item.getClass()!=type(item.getClass().getName())
        ||ItemCallbacks.method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass()!=owner
        ||ItemCallbacks.method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass()!=owner
        ||ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
      throw fault("OC container dispatch changed");
    List<Object> delegates=NativeScala.values(field(owner,item,"subItems"),65536);
    int metadata=Items.feather.getDamage(input);
    if(metadata<0||metadata>=delegates.size()){
      Object returned=field(Item.class,item,"containerItem");
      if(returned==null)return null;
      if(!(returned instanceof Item))throw fault("OC fallback container item differs");
      return new ItemStack((Item)returned);
    }
    Object delegate=delegates.get(metadata);
    if(delegate==null)throw fault("OC null item delegate");
    if(delegate.getClass()==type(BASE+"InkCartridge")){
      Object api=NativeOcQueries.api("items","li.cil.oc.common.init.Items$");
      Object descriptors=field(api,"descriptors");
      Object name=lookup(field(api,"names"),delegate);
      if(name!=null&&!(name instanceof String))throw fault("OC delegate registry name differs");
      Object registered=name==null?null:lookup(descriptors,name);
      if(registered!=lookup(descriptors,"inkCartridge"))return null;
      Object empty=lookup(descriptors,"inkCartridgeEmpty");
      if(empty==null||empty.getClass()!=type("li.cil.oc.common.init.Items$$anon$6"))
        throw fault("OC empty ink descriptor is unavailable");
      Object target=field(empty,"delegate$1");
      if(target==null||target.getClass()!=type(BASE+"InkCartridgeEmpty"))throw fault("OC empty ink delegate differs");
      Object parent=field(target,"parent"),id=field(target,"itemId");
      if(!(parent instanceof Item)||!(id instanceof Integer)||!supports((Item)parent))
        throw fault("OC empty ink item identity differs");
      return new ItemStack((Item)parent,1,(Integer)id);
    }
    defaultBridge(delegate,"hasContainerItem",Opcodes.IRETURN);
    defaultBridge(delegate,"getContainerItem",Opcodes.ARETURN);
    return null;
  }
  private static Object lookup(Object map,Object key){
    for(Object pair:NativeScala.values(map,262144)){
      Object candidate=NativeScala.pair(pair,1);
      if(key instanceof String?key.equals(candidate):key==candidate)return NativeScala.pair(pair,2);
    }
    return null;
  }
  private static synchronized void defaultBridge(Object delegate,String name,int exit){
    try{
      Method method=delegate.getClass().getMethod(name,ItemStack.class);
      if(Boolean.TRUE.equals(BRIDGES.get(method)))return;
      Class<?> owner=method.getDeclaringClass();
      if(BRIDGES.size()>=1024)throw fault("OC delegate callback budget exceeded");
      try(InputStream input=owner.getResourceAsStream("/"+owner.getName().replace('.','/')+".class");
          ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
        if(input==null)throw fault("Missing OC delegate callback source");
        byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(bytes.size()+count>2097152)throw fault("OC delegate source exceeds budget");bytes.write(buffer,0,count);}
        ClassNode source=new ClassNode();new ClassReader(bytes.toByteArray()).accept(source,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        for(Object raw:source.methods){MethodNode body=(MethodNode)raw;
          if(!body.name.equals(name)||!body.desc.equals(Type.getMethodDescriptor(method)))continue;
          List<AbstractInsnNode> code=new ArrayList<>();
          for(AbstractInsnNode node:body.instructions.toArray())if(node.getOpcode()>=0)code.add(node);
          if((body.access&(Opcodes.ACC_STATIC|Opcodes.ACC_SYNCHRONIZED))==0&&body.tryCatchBlocks.isEmpty()
              &&code.size()==4&&load(code.get(0),0)&&load(code.get(1),1)
              &&code.get(2) instanceof MethodInsnNode&&code.get(3).getOpcode()==exit){
            MethodInsnNode call=(MethodInsnNode)code.get(2);
            if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("li/cil/oc/common/item/traits/Delegate$class")
                &&call.name.equals(name)&&call.desc.equals("(Lli/cil/oc/common/item/traits/Delegate;Lnet/minecraft/item/ItemStack;)"+(exit==Opcodes.IRETURN?"Z":"Lnet/minecraft/item/ItemStack;"))){
              BRIDGES.put(method,true);return;
            }
          }
        }
      }
    }catch(IOException|NoSuchMethodException failure){throw new IllegalStateException("Cannot audit OC delegate callback",failure);}
    throw fault("Unadapted OC container delegate: "+delegate.getClass().getName());
  }
  private static boolean load(AbstractInsnNode node,int index){return node.getOpcode()==Opcodes.ALOAD&&((VarInsnNode)node).var==index;}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
