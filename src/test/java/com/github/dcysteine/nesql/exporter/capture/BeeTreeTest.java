package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import forestry.api.apiculture.*;
import forestry.api.genetics.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real installed BBAB createStack callback; a controlled Forestry registry is explicitly a fixture. */
final class BeeTreeTest {
    static void run() throws Exception {
        Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BeeTreeReferences");
        Class<?> nativeHandler = Class.forName("hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler");
        Method snapshot = adapter.getDeclaredMethod("snapshot", IBeeRoot.class, JsonObject.class, boolean.class, boolean.class, Map.class);
        snapshot.setAccessible(true);
        IBeeRoot[] root = new IBeeRoot[1];
        Map<String,IAllele[]> templates = new TreeMap<>();
        List<IBeeMutation> mutations = new ArrayList<>();
        root[0] = (IBeeRoot) proxy(IBeeRoot.class, (method,args) -> {
            switch(method.getName()) {
                case "getUID": return "rootBees";
                case "getMutations": if ((Boolean) args[0]) throw new AssertionError("Shared list shuffled"); return mutations;
                case "getTemplate": return templates.get(args[0]);
                case "templateAsIndividual":
                    IAllele[] genome = (IAllele[]) args[0]; boolean[] analyzed = {false};
                    return proxy(IBee.class, (operation,values) -> {
                        if (operation.getName().equals("analyze")) { analyzed[0] = true; return true; }
                        if (operation.getName().equals("writeToNBT")) {
                            NBTTagCompound nbt = (NBTTagCompound) values[0]; nbt.setString("fixtureSpecies", genome[0].getUID());
                            nbt.setBoolean("isAnalyzed", analyzed[0]); return nbt;
                        }
                        throw new AssertionError("Unexpected individual callback " + operation);
                    });
                case "getMemberStack":
                    NBTTagCompound nbt = new NBTTagCompound(); ((IIndividual) args[0]).writeToNBT(nbt);
                    ItemStack stack = new ItemStack(Items.paper, 1, (Integer) args[1]); stack.setTagCompound(nbt); return stack;
                default: throw new AssertionError("Unexpected root callback " + method);
            }
        });
        Map<String,IAlleleSpecies> species = new TreeMap<>();
        for (String uid : new String[]{"fixture.bee.a","fixture.bee.b","fixture.bee.c","fixture.bee.d"}) {
            IAlleleSpecies allele = (IAlleleSpecies) proxy(IAlleleBeeSpecies.class,(method,args) -> {
                switch(method.getName()) {
                    case "getUID": return uid;
                    case "getName": return "Native " + uid;
                    case "getRoot": return root[0];
                    default: throw new AssertionError("Unexpected allele callback " + method);
                }
            });
            species.put(uid,allele); templates.put(uid,new IAllele[]{allele});
        }
        boolean[] fail = {false};
        mutations.add(mutation(root[0],species,"a","b","c",false,7.5f,fail));
        mutations.add(mutation(root[0],species,"a","d","c",true,0.5f,fail));
        mutations.add(mutations.get(0)); // Independent native registration, never deduplicate rules.
        JsonObject source = object("owner","beebetteratbees","handler",nativeHandler.getName(),"key","bbab.breedtree");
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        codechicken.nei.recipe.ICraftingHandler handler=(codechicken.nei.recipe.ICraftingHandler)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(nativeHandler);
        if(Recipes.adapter(handler)!=Recipes.Adapter.BEE_TREE_REFERENCE) throw new AssertionError("BBAB still routes through overlay/consuming template");
        Map<String,ItemStack> specimens = new TreeMap<>();
        JsonArray rows = (JsonArray) snapshot.invoke(null,root[0],source,false,false,specimens);
        if(rows.size()!=8 || specimens.size()!=8) throw new AssertionError("Lost species forms or duplicate mutation registration");
        JsonObject first = rows.get(5).getAsJsonObject().getAsJsonObject("data");
        if(!first.getAsJsonObject("chance").equals(object("numerator","3","denominator","40"))) throw new AssertionError("Rounded fractional native probability");
        if(rows.get(6).getAsJsonObject().getAsJsonObject("data").get("eligible").getAsBoolean()) throw new AssertionError("Secret root exposed");
        for(ItemStack stack:specimens.values()) if(!stack.getTagCompound().getBoolean("isAnalyzed")) throw new AssertionError("BBAB native analyzed specimen policy lost");
        String before = CanonicalJson.digest(rows);
        if(before.equals(CanonicalJson.digest((JsonArray)snapshot.invoke(null,root[0],source,true,false,new HashMap<>())))) throw new AssertionError("Visibility drift omitted");
        fail[0]=true;
        try { snapshot.invoke(null,root[0],source,false,false,new HashMap<>()); throw new AssertionError("Native condition failure swallowed"); }
        catch(InvocationTargetException expected) { if(!(expected.getCause() instanceof IllegalStateException)) throw expected; }
        fail[0]=false;
        JsonArray items = array();
        for(Map.Entry<String,ItemStack> entry:specimens.entrySet()) {
            ItemStack stack=entry.getValue(); items.add(object("id",entry.getKey(),"registry",net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),"meta",stack.getItemDamage(),"nbt",TypedNbt.encode(stack.getTagCompound()),"tags",array()));
        }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/bee-tree-contract.json"),CanonicalJson.bytes(object("records",rows,"items",items)));
        mutations.clear();
        JsonArray empty=(JsonArray)snapshot.invoke(null,root[0],source,false,false,new HashMap<>());
        if(empty.size()!=1) throw new AssertionError("Ready empty mutation list became an unsupported handler");
        System.out.println("BBAB: installed analyzed specimen callback, all native routes, duplicates, fractional chance, secrets, conditions and ready empty pool passed");
    }
    private static IBeeMutation mutation(IBeeRoot root,Map<String,IAlleleSpecies> species,String a,String b,String result,boolean secret,float chance,boolean[] fail) {
        return (IBeeMutation) proxy(IBeeMutation.class,(method,args) -> {
            switch(method.getName()) {
                case "getRoot": return root;
                case "getAllele0": return species.get("fixture.bee."+a);
                case "getAllele1": return species.get("fixture.bee."+b);
                case "getTemplate": return new IAllele[]{species.get("fixture.bee."+result)};
                case "getBaseChance": return chance;
                case "isSecret": return secret;
                case "getSpecialConditions": if(fail[0]) throw new IllegalStateException("fixture condition callback failed"); return Arrays.asList("Native biome condition","§eNative temperature condition");
                default: throw new AssertionError("Unexpected mutation callback " + method);
            }
        });
    }
    private interface Callback { Object call(Method method,Object[] args); }
    private static Object proxy(Class<?> type,Callback callback) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(target,method,args) -> {
            if(method.getDeclaringClass()==Object.class) {
                if(method.getName().equals("equals")) return target==args[0];
                if(method.getName().equals("hashCode")) return System.identityHashCode(target);
                if(method.getName().equals("toString")) return type.getName()+" fixture";
            }
            return callback.call(method,args);
        });
    }
}
