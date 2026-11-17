package com.github.dcysteine.nesql.exporter.capture;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Apply the installed NotEnoughIDs Block storage method bodies, without a live game. */
public final class NativeHarvestStorageTest {
    static void run() throws Exception {
        NativeCoreFixesTest.version("neid","2.1.10");
        Block block=Blocks.stone;
        if(MagicApi.field(Block.class,block,"harvestLevel")!=null)throw new AssertionError("NotEnoughIDs fixture was not transformed");
        block.setHarvestLevel("pickaxe",2);block.setHarvestLevel("pickaxe",7,45001);
        try(NativeHarvest binding=new NativeHarvest(block,45001)){
            if(binding.level()!=block.getHarvestLevel(45001)||binding.level()!=7)throw new AssertionError("Extended native harvest metadata was lost");
            block.setHarvestLevel("pickaxe",6,45001);
            try{binding.verify();throw new AssertionError("Native harvest override drift was ignored");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){if(!expected.code.equals("recipe_changed"))throw expected;}
        }
        try(NativeHarvest binding=new NativeHarvest(block,45002)){
            if(binding.level()!=2)throw new AssertionError("NotEnoughIDs default harvest level was lost");
        }
        System.out.println("NotEnoughIDs harvest storage: exact installed map/default getters, extended metadata and drift passed");
    }
    public static final class Definitions implements IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes){
            if(bytes==null||!transformed.equals("net.minecraft.block.Block"))return bytes;
            try(java.io.InputStream in=getClass().getClassLoader().getResourceAsStream("com/gtnewhorizons/neid/mixins/early/minecraft/MixinBlock.class")){
                ClassNode mixin=new ClassNode(),target=new ClassNode();new ClassReader(in).accept(mixin,0);new ClassReader(bytes).accept(target,0);
                for(Object raw:mixin.fields){FieldNode f=(FieldNode)raw;if(target.fields.stream().noneMatch(x->((FieldNode)x).name.equals(f.name)))target.fields.add(f);}
                for(Object raw:mixin.methods){
                    MethodNode m=(MethodNode)raw;
                    if(m.name.equals("isToolEffective"))continue;
                    if(m.name.equals("<init>")){
                        AbstractInsnNode superCall=null;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("<init>")){superCall=i;break;}
                        while(m.instructions.getFirst()!=superCall)m.instructions.remove(m.instructions.getFirst());m.instructions.remove(superCall);
                        m.name="nesql$neidInit";m.access=Opcodes.ACC_PRIVATE;
                    }else target.methods.removeIf(x->((MethodNode)x).name.equals(m.name)&&((MethodNode)x).desc.equals(m.desc));
                    for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof FieldInsnNode&&((FieldInsnNode)i).owner.equals(mixin.name))((FieldInsnNode)i).owner=target.name;
                    target.methods.add(m);
                }
                for(Object raw:target.methods){MethodNode m=(MethodNode)raw;if(!m.name.equals("<init>"))continue;for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN){InsnList call=new InsnList();call.add(new VarInsnNode(Opcodes.ALOAD,0));call.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,target.name,"nesql$neidInit","()V",false));m.instructions.insertBefore(i,call);}}
                ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);target.accept(out);return out.toByteArray();
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }
    }
}
