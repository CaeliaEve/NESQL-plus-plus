package com.github.dcysteine.nesql.exporter.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Exact-name, same-descriptor redirection. No GL operations, models or
 * render branches change; unreviewed classes are returned byte-for-byte. */
public final class RendererTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        if ("net.minecraft.client.renderer.Tessellator".equals(transformedName)) return tessellation(bytes);
        if ("net.minecraft.client.model.ModelRenderer".equals(transformedName)) return displayLists(bytes);
        if ("com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer".equals(transformedName)) return bufferUploads(bytes);
        if (ForestryTrace.reviewed(transformedName)) {
            byte[] observed=forestry(transformedName,bytes);
            return ForestryTrace.item(transformedName)?inventory(transformedName,observed):observed;
        }
        if (InventoryTrace.reviewed(transformedName)) return inventory(transformedName, bytes);
        if (RendererClock.shaderCallback(transformedName)) return shaderClock(transformedName, bytes);
        if (RendererClock.profile(transformedName) == null) return bytes;
        ClassReader reader = new ClassReader(bytes); ClassWriter writer = new ClassWriter(reader, 0);
        boolean[] tickHook = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (owner.equals("com/github/dcysteine/nesql/exporter/core/RendererClock") && name.equals("entityTick")) tickHook[0] = true;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        int[] changed = {0}; boolean[] existing = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                        super.visitFieldInsn(opcode, owner, field, descriptor);
                        // Preserve the original object dereference and native tick read.
                        // Only the reviewed inventory pulse consumes the scoped tick.
                        if (!tickHook[0] && transformedName.equals("witchinggadgets.client.render.ItemRenderPrimordialGauntlet")
                                && opcode == Opcodes.GETFIELD && descriptor.equals("I")
                                && (field.equals("ticksExisted") || field.equals("field_70173_aa"))
                                && (owner.equals("net/minecraft/client/entity/EntityClientPlayerMP") || owner.equals("net/minecraft/entity/Entity"))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/github/dcysteine/nesql/exporter/core/RendererClock", "entityTick", "(I)I", false);
                            changed[0]++;
                        }
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (opcode == Opcodes.INVOKESTATIC && descriptor.equals("()J")) {
                            if ((owner.equals("org/lwjgl/Sys") && method.equals("getTime"))
                                    || (owner.equals("java/lang/System") && method.equals("currentTimeMillis"))) {
                                method = owner.equals("org/lwjgl/Sys") ? "sysTime" : "millis";
                                owner = "com/github/dcysteine/nesql/exporter/core/RendererClock"; changed[0]++;
                            } else if (owner.equals("com/github/dcysteine/nesql/exporter/core/RendererClock")) existing[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                    }
                };
            }
        }, 0);
        if (changed[0] > 0 || existing[0]) RendererClock.installed(transformedName);
        return changed[0] == 0 ? bytes : writer.toByteArray();
    }

    private static byte[] bufferUploads(byte[] bytes){
        String hook="com/github/dcysteine/nesql/exporter/core/BufferUploads";ClassReader reader=new ClassReader(bytes);boolean[] present={false};
        reader.accept(new ClassVisitor(Opcodes.ASM5){@Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
            return new MethodVisitor(Opcodes.ASM5){@Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){if(owner.equals(hook))present[0]=true;}};
        }},ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);if(present[0])return bytes;
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] found={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){@Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
            boolean selected=(name.equals("upload")&&desc.equals("(Ljava/nio/ByteBuffer;I)V")||name.equals("close")&&desc.equals("()V"))&&(access&Opcodes.ACC_STATIC)==0;
            if(selected)found[0]++;
            return new MethodVisitor(Opcodes.ASM5,super.visitMethod(access,name,desc,sig,exceptions)){
                @Override public void visitInsn(int op){if(selected&&op==Opcodes.RETURN){super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,hook,"changed","(Ljava/lang/Object;)V",false);}super.visitInsn(op);}
            };
        }},0);return found[0]==2?writer.toByteArray():bytes;
    }

    private static byte[] displayLists(byte[] bytes){
        String hook="com/github/dcysteine/nesql/exporter/core/ModelDisplayLists";ClassReader reader=new ClassReader(bytes);boolean[] present={false};
        reader.accept(new ClassVisitor(Opcodes.ASM5){@Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
            return new MethodVisitor(Opcodes.ASM5){@Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){if(owner.equals(hook))present[0]=true;}};
        }},ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(present[0])return bytes;
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] found={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){@Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
            boolean selected=(name.equals("compileDisplayList")||name.equals("func_78788_d"))&&desc.equals("(F)V")&&(access&Opcodes.ACC_STATIC)==0;
            if(selected)found[0]++;
            return new MethodVisitor(Opcodes.ASM5,super.visitMethod(access,name,desc,sig,exceptions)){
                @Override public void visitCode(){super.visitCode();if(selected){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.FLOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,hook,"before","(Ljava/lang/Object;F)V",false);}}
                @Override public void visitInsn(int op){if(selected&&op==Opcodes.RETURN){super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,hook,"after","(Ljava/lang/Object;)V",false);}super.visitInsn(op);}
            };
        }},0);return found[0]==1?writer.toByteArray():bytes;
    }

    private static byte[] tessellation(byte[] bytes) {
        final String hook="com/github/dcysteine/nesql/exporter/core/InventoryDrawTrace";
        ClassReader reader=new ClassReader(bytes);java.util.Set<String> present=new java.util.HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM5){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM5){
                    @Override public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
                        if(op==Opcodes.INVOKESTATIC&&owner.equals(hook))present.add(name+desc);
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] found={0},changed={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                String event=desc.equals("(I)V")&&(name.equals("startDrawing")||name.equals("func_78371_b"))?"begin":
                        desc.equals("(DDD)V")&&(name.equals("addVertex")||name.equals("func_78377_a"))?"vertex":
                        desc.equals("()I")&&(name.equals("draw")||name.equals("func_78381_a"))?"draw":null;
                if(event!=null&&(access&Opcodes.ACC_STATIC)==0)found[0]++;else event=null;
                final String selected=event;
                return new MethodVisitor(Opcodes.ASM5,super.visitMethod(access,name,desc,signature,exceptions)){
                    @Override public void visitCode(){
                        super.visitCode();if(selected==null||present.contains(name+desc))return;
                        changed[0]++;super.visitVarInsn(Opcodes.ALOAD,0);String params="(Ljava/lang/Object;";
                        if(selected.equals("begin")){super.visitVarInsn(Opcodes.ILOAD,1);params+="I";}
                        if(selected.equals("vertex")){for(int slot:new int[]{1,3,5})super.visitVarInsn(Opcodes.DLOAD,slot);params+="DDD";}
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,hook,selected,params+")V",false);
                    }
                };
            }
        },0);
        if(found[0]!=3)return bytes;
        InventoryDrawTrace.installed();return changed[0]==0?bytes:writer.toByteArray();
    }

    private static byte[] inventory(String type, byte[] bytes) {
        final String hook = "com/github/dcysteine/nesql/exporter/core/InventoryTrace";
        final String descriptor = "(Lnet/minecraftforge/client/IItemRenderer$ItemRenderType;Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V";
        ClassReader reader = new ClassReader(bytes); boolean[] present = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                if (!name.equals("renderItem") || !desc.equals(descriptor)) return null;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC && owner.equals(hook) && name.equals("enter")
                                && desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")) present[0] = true;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS); int[] changed = {0}, entries = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, name, desc, sig, exceptions);
                boolean entry = name.equals("renderItem") && desc.equals(descriptor) && (access & Opcodes.ACC_STATIC) == 0;
                if (entry) entries[0]++;
                return new MethodVisitor(Opcodes.ASM5, target) {
                    @Override public void visitCode() {
                        super.visitCode();
                        if (!entry || present[0]) return;
                        changed[0]++;
                        super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, 1); super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "enter", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    }
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC && desc.equals("()J") && (owner.equals("org/lwjgl/Sys") && name.equals("getTime")
                                || owner.equals("java/lang/System") && name.equals("currentTimeMillis"))) {
                            name=owner.equals("org/lwjgl/Sys")?"sysTime":"millis";
                            owner = "com/github/dcysteine/nesql/exporter/core/RendererClock"; changed[0]++;
                        } else if (type.equals(InventoryTrace.ETERNAL) && op == Opcodes.INVOKEVIRTUAL
                                && owner.equals("java/util/Random") && name.equals("nextGaussian") && desc.equals("()D")) {
                            op = Opcodes.INVOKESTATIC; owner = "com/github/dcysteine/nesql/exporter/core/RendererRandom";
                            name = "gaussian"; desc = "(Ljava/util/Random;)D"; itf = false; changed[0]++;
                        }
                        super.visitMethodInsn(op, owner, name, desc, itf);
                    }
                    @Override public void visitInsn(int op) {
                        if (entry && !present[0] && op == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "exit", "(Ljava/lang/Object;)V", false);
                        }
                        super.visitInsn(op);
                    }
                };
            }
        }, 0);
        if (entries[0] != 1) return bytes;
        InventoryTrace.installed(type);
        return changed[0] == 0 ? bytes : writer.toByteArray();
    }

    /** Exact Forestry 4.10.17 call sites, including display-list model draws.
     * Duplicated operands are observations, never replacement native values. */
    private static byte[] forestry(String type,byte[] bytes){
        final String hook="com/github/dcysteine/nesql/exporter/core/ForestryTrace";
        ClassReader reader=new ClassReader(bytes);boolean[] present={false};
        reader.accept(new ClassVisitor(Opcodes.ASM5){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM5){@Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){if(owner.equals(hook))present[0]=true;}};
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(present[0]){ForestryTrace.installed(type);return bytes;}
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] found={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
                boolean model=ForestryTrace.model(type)&&(name.equals("render")||name.equals("func_78088_a"))&&desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V");
                boolean wing=ForestryTrace.item(type)&&name.equals("getWingYaw")&&desc.equals(type.equals(ForestryTrace.BEE_ITEM)?"(Lforestry/api/apiculture/IBee;)F":"(Lforestry/api/lepidopterology/IButterfly;)F");
                boolean flap=type.equals(ForestryTrace.BUTTERFLY_STATE)&&name.equals("getWingFlap")&&desc.equals("(Lforestry/lepidopterology/entities/EntityButterfly;JF)F");
                boolean entity=ForestryTrace.entity(type)&&name.equals(type.equals(ForestryTrace.BEE_ENTITY)?"renderBee":"renderButterfly")
                        &&desc.equals(type.equals(ForestryTrace.BEE_ENTITY)?"(Lforestry/apiculture/entities/EntityBee;DDDFF)V":"(Lforestry/lepidopterology/entities/EntityButterfly;DDDFF)V");
                boolean texture=ForestryTrace.entity(type)&&(name.equals("getEntityTexture")||name.equals("func_110775_a"))&&desc.equals("(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/ResourceLocation;");
                if(model||wing||flap||entity||texture)found[0]++;
                return new MethodVisitor(Opcodes.ASM5,super.visitMethod(access,name,desc,sig,exceptions)){
                    @Override public void visitCode(){
                        super.visitCode();
                        if(model){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);for(int slot=2;slot<=7;slot++)super.visitVarInsn(Opcodes.FLOAD,slot);call("model","(Ljava/lang/Object;Ljava/lang/Object;FFFFFF)V");}
                        if(wing){super.visitVarInsn(Opcodes.ALOAD,0);call("individual","(Ljava/lang/Object;)V");}
                        if(flap){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.LLOAD,2);super.visitVarInsn(Opcodes.FLOAD,4);call("butterfly","(Ljava/lang/Object;Ljava/lang/Object;JF)V");}
                        if(entity){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);call("entity","(Ljava/lang/Object;Ljava/lang/Object;)V");}
                    }
                    @Override public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
                        boolean part=model&&op==Opcodes.INVOKEVIRTUAL&&owner.equals("net/minecraft/client/model/ModelRenderer")
                                &&(method.equals("render")||method.equals("func_78785_a"))&&descriptor.equals("(F)V");
                        if(part){
                            super.visitInsn(Opcodes.DUP2);call("part","(Ljava/lang/Object;F)V");super.visitInsn(Opcodes.DUP2);
                        }
                        if(op==Opcodes.INVOKESTATIC&&owner.equals("java/lang/System")&&method.equals("currentTimeMillis")&&descriptor.equals("()J")){
                            owner="com/github/dcysteine/nesql/exporter/core/RendererClock";method="millis";
                        }
                        super.visitMethodInsn(op,owner,method,descriptor,itf);
                        if(part){super.visitInsn(Opcodes.POP);call("drawn","(Ljava/lang/Object;)V");}
                        if(wing&&descriptor.equals("()Z")&&method.equals("isAlive")&&(owner.equals("forestry/api/apiculture/IBee")||owner.equals("forestry/api/lepidopterology/IButterfly"))){super.visitInsn(Opcodes.DUP);call("alive","(Z)V");}
                        if(wing&&owner.equals("java/lang/String")&&method.equals("hashCode")&&descriptor.equals("()I")){super.visitInsn(Opcodes.DUP);call("hash","(I)V");}
                    }
                    @Override public void visitInsn(int op){
                        if(model&&op==Opcodes.RETURN){super.visitVarInsn(Opcodes.ALOAD,0);call("end","(Ljava/lang/Object;)V");}
                        if((wing||flap)&&op==Opcodes.FRETURN){super.visitInsn(Opcodes.DUP);call(wing?"partial":"flap","(F)V");}
                        if(texture&&op==Opcodes.ARETURN){super.visitInsn(Opcodes.DUP);call("texture","(Ljava/lang/Object;)V");}
                        super.visitInsn(op);
                    }
                    private void call(String name,String desc){super.visitMethodInsn(Opcodes.INVOKESTATIC,hook,name,desc,false);}
                };
            }
        },0);
        if(found[0]!=(ForestryTrace.entity(type)?2:1))return bytes;
        ForestryTrace.installed(type);return writer.toByteArray();
    }

    /** Shader callback reads remain real dereferences; only the consumed tick
     * value changes inside a capture clock. This is not a finite-cycle profile. */
    private static byte[] shaderClock(String type, byte[] bytes) {
        ClassReader reader = new ClassReader(bytes); boolean[] present = {false};
        final String hook = "com/github/dcysteine/nesql/exporter/core/RendererClock";
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC && owner.equals(hook) && name.equals("entityTick") && desc.equals("(I)I")) present[0] = true;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (present[0]) { RendererClock.installed(type); return bytes; }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS); int[] changed = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, sig, exceptions)) {
                    @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
                        super.visitFieldInsn(op, owner, name, desc);
                        if (op == Opcodes.GETFIELD && desc.equals("I") && (name.equals("ticksExisted") || name.equals("field_70173_aa"))
                                && (owner.equals("net/minecraft/client/entity/EntityClientPlayerMP") || owner.equals("net/minecraft/entity/Entity"))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "entityTick", "(I)I", false); changed[0]++;
                        }
                    }
                };
            }
        }, 0);
        if (changed[0] > 0) RendererClock.installed(type);
        return changed[0] == 0 ? bytes : writer.toByteArray();
    }
}
