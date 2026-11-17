package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import gregtech.api.util.GTShapedRecipe;
import gregtech.api.util.GTShapelessRecipe;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Installed GT matching, audited result stages and bounded transport on owned inputs. */
final class NativeGtCraftingTest {
    static void run() throws Exception {
        if (!GTShapedRecipe.class.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/gregtech-space.jar!"))
            throw new AssertionError("Pinned installed GT JAR required for crafting declarations");
        ItemStack output = new ItemStack(Items.diamond);
        Enchantment[] enchantments = {Enchantment.sharpness, Enchantment.sharpness};
        int[] levels = {2, -1};
        GTShapedRecipe shaped = new GTShapedRecipe(output, true, true, enchantments, levels,
                "ab", 'a', Items.paper, 'b', Items.gold_ingot);
        GTShapelessRecipe shapeless = new GTShapelessRecipe(output, false, true, true, null, null,
                Items.paper, Items.gold_ingot);
        ItemStack paper = new ItemStack(Items.paper), gold = new ItemStack(Items.gold_ingot);
        paper.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("owned"));
        gold.setTagCompound((net.minecraft.nbt.NBTTagCompound)paper.getTagCompound().copy());
        require(shaped.matches(NativeCraftingTest.inventory(3, 3, paper, gold), null)
                && shapeless.matches(NativeCraftingTest.inventory(3, 3, gold, paper), null), "Native keeping-NBT equality rejected a valid tuple");
        ItemStack untagged = new ItemStack(Items.gold_ingot);
        require(!shaped.matches(NativeCraftingTest.inventory(3, 3, paper, untagged), null)
                && !shapeless.matches(NativeCraftingTest.inventory(3, 3, untagged, paper), null), "Native keeping-NBT hasTag equality disappeared");
        gold.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("different"));
        require(!shaped.matches(NativeCraftingTest.inventory(3, 3, paper, gold), null), "Native keeping-NBT tag equality disappeared");
        com.google.gson.JsonObject declaration = GtCraftingRules.definition(shaped);
        require(declaration.get("keepingNbt").getAsBoolean() && declaration.get("removable").getAsBoolean()
                && declaration.getAsJsonArray("enchantments").size() == 2
                && declaration.getAsJsonArray("enchantments").get(1).getAsJsonObject().get("level").getAsInt() == -1,
                "GT declaration order, duplicate enchantments or signed levels were altered");
        require(GtCraftingRules.definition(shapeless).get("overwriteNbt").getAsBoolean(), "Shapeless overwrite mode was lost");
        nativeResults();
        nativeTagEquality();
        stateSpace();
        normalizationBindings();
        electricBindings();
        boundResultStages();
        finiteCapturePlans();
        RegistryRecipes reader = NativeCraftingTest.reader(Collections.singletonList(shaped), true);
        ItemStack nativeResult=shaped.getCraftingResult(NativeCraftingTest.inventory(3,3,new ItemStack(Items.paper),untagged));
        RecipeRow captured=NativeCraftingTest.row(true,new ItemStack(Items.paper),untagged,output,nativeResult);
        require(reader.capture(0,captured)&&captured.record.getAsJsonObject("process").get("kind").getAsString().equals("gtCrafting"),
                "Native GT capture did not use its typed multi-input result process");
        NativeContractFixture.writeOwned("native-gt-crafting-shaped",captured,Arrays.asList(new ItemStack(Items.paper),untagged,output,nativeResult));
        levels[0] = 3;
        try { reader.finishVerification(); throw new AssertionError("Mutated native GT level array escaped sealing"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("recipe_changed"), failure.toString()); }
        levels[0] = 2; reader.finishVerification(); reader.close();
        neutralKeepingRepresentatives();
        java.nio.file.Path evidence = java.nio.file.Paths.get("build/native-tests/gt-crafting-declarations.json");
        java.nio.file.Files.createDirectories(evidence.getParent());
        java.nio.file.Files.write(evidence, com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(object(
                "shaped", declaration, "shapeless", GtCraftingRules.definition(shapeless),
                "actualNativeMatching", true, "resultProcessImplemented", true)));
        System.out.println("Installed GT crafting: native finite result transport, candidate order, compact sealing bindings and drift passed");
    }

    private static void neutralKeepingRepresentatives() throws Exception {
        ItemStack paper=new ItemStack(Items.paper),gold=new ItemStack(Items.gold_ingot),output=new ItemStack(Items.diamond);
        paper.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("left"));gold.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("right"));
        GTShapelessRecipe recipe=new GTShapelessRecipe(output,false,true,false,null,null,paper,gold);
        RecipeRow row=NativeCraftingTest.row(false,paper,gold,new ItemStack(Items.paper),new ItemStack(Items.gold_ingot),output);
        RegistryRecipes reader=NativeCraftingTest.reader(Collections.singletonList(recipe),false);
        try {
            require(reader.capture(0,row),"Native keeping recipe without equal declaration tags was excluded");
            require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size()==2
                    &&row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").size()==2,
                    "Native ignored-NBT predicates lost their valid untagged representatives");
            reader.finishVerification();
            NativeContractFixture.writeOwned("native-gt-crafting-neutral",row,Arrays.asList(paper,gold,new ItemStack(Items.paper),new ItemStack(Items.gold_ingot),output));
        } finally {reader.close();}
        require(paper.getTagCompound().getString("owner").equals("left")&&gold.getTagCompound().getString("owner").equals("right"),
                "Generated neutral witnesses changed borrowed native templates");
    }

    private static void normalizationBindings() throws Exception {
        ItemStack diamond = new ItemStack(Items.diamond,3,2); diamond.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("retained"));
        GtItemNormalization plain = new GtItemNormalization(diamond);
        ItemStack copied = plain.apply();
        require(copied!=diamond && copied.stackSize==3 && copied.getItemDamage()==2
                && ItemStack.areItemStackTagsEqual(copied,diamond),"Native no-GT normalization lost owned stack semantics");
        java.lang.reflect.Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)access.get(null);
        net.minecraft.item.Item meta=(net.minecraft.item.Item)unsafe.allocateInstance(MetaNormalizationSpecimen.class);
        java.lang.reflect.Field delegate=net.minecraft.item.Item.class.getField("delegate");delegate.setAccessible(true);
        delegate.set(meta,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(meta,net.minecraft.item.Item.class));
        Map<Short,ArrayList<Object>> behaviors = new java.util.concurrent.ConcurrentHashMap<>();
        Object noOp=MagicApi.type("gregtech.common.items.behaviors.BehaviourNone").newInstance();
        behaviors.put((short)7,new ArrayList<>(Collections.singletonList(noOp)));
        java.lang.reflect.Field field=MagicApi.type("gregtech.api.items.MetaBaseItem").getDeclaredField("mItemBehaviors");field.setAccessible(true);field.set(meta,behaviors);
        ItemStack specimen=new ItemStack(meta,1,7);
        GtItemNormalization bound=new GtItemNormalization(specimen);
        require(bound.apply().getItem()==meta,"Actual inherited MetaBaseItem identity behavior changed");
        behaviors.get((short)7).add(noOp);
        try{bound.verify();throw new AssertionError("Changed native behavior list escaped normalization binding");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
        behaviors.get((short)7).remove(1);bound.verify();
        field.set(meta,new java.util.concurrent.ConcurrentHashMap<>(behaviors));
        try{bound.verify();throw new AssertionError("Replaced native table with equal contents escaped normalization binding");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
        net.minecraft.item.Item unknown=(net.minecraft.item.Item)unsafe.allocateInstance(UnknownGeneric.class);
        delegate.set(unknown,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(unknown,net.minecraft.item.Item.class));
        try{new GtItemNormalization(new ItemStack(unknown));throw new AssertionError("Unknown normalization override was accepted");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_unsupported"),failure.toString());}
        net.minecraft.item.Item tool=(net.minecraft.item.Item)unsafe.allocateInstance(MagicApi.type("gregtech.common.items.MetaGeneratedTool01"));
        delegate.set(tool,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(tool,net.minecraft.item.Item.class));
        java.lang.reflect.Field statsField=MagicApi.type("gregtech.api.items.MetaGeneratedTool").getDeclaredField("mToolStats");statsField.setAccessible(true);
        statsField.set(tool,new java.util.concurrent.ConcurrentHashMap<>());
        ItemStack broken=new ItemStack(tool,1,1);
        broken.setTagCompound((net.minecraft.nbt.NBTTagCompound)diamond.getTagCompound().copy());
        broken.addEnchantment(Enchantment.sharpness,1);
        GtItemNormalization invalid=new GtItemNormalization(broken);ItemStack normalized=invalid.apply();
        require(!normalized.getTagCompound().hasKey("ench") && normalized.getTagCompound().getString("owner").equals("retained")
                && broken.getTagCompound().hasKey("ench"),"Native invalid-tool cleanup mutated the source or unrelated tags");
        @SuppressWarnings("unchecked") Map<Short,Object> nativeStats=(Map<Short,Object>)statsField.get(tool);
        nativeStats.put((short)0,MagicApi.type("gregtech.common.tools.ToolSaw").newInstance());
        ItemStack valid=broken.copy();valid.setItemDamage(0);
        GtItemNormalization basicTool=new GtItemNormalization(valid);
        ItemStack normalizedValid=basicTool.apply();
        require(!normalizedValid.getTagCompound().hasKey("ench") && valid.getTagCompound().hasKey("ench")
                && basicTool.declaration().getAsJsonArray("filters").size()==0,
                "Native empty tool enchantment normalization acquired an unrelated readiness dependency or mutated source");
        Object savedStat=nativeStats.put((short)0,MagicApi.type("gregtech.common.tools.ToolSaw").newInstance());
        try{basicTool.verify();throw new AssertionError("Equal-class tool-stat instance replacement escaped normalization binding");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
        nativeStats.put((short)0,savedStat);basicTool.verify();
        net.minecraft.item.Item item02=(net.minecraft.item.Item)unsafe.allocateInstance(MagicApi.type("gregtech.common.items.MetaGeneratedItem02"));
        delegate.set(item02,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(item02,net.minecraft.item.Item.class));
        field.set(item02,new java.util.concurrent.ConcurrentHashMap<>());
        ItemStack plain02=new ItemStack(item02,1,7);plain02.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("retained"));
        GtItemNormalization metadata02=new GtItemNormalization(plain02);ItemStack result02=metadata02.apply();
        require(ItemStack.areItemStackTagsEqual(plain02,result02) && !metadata02.declaration().get("withinInitializationRange").getAsBoolean(),
                "Native MetaGeneratedItem02 outside initialization range changed unrelated tags");
        Object material=unsafe.allocateInstance(MagicApi.type("gregtech.api.enums.Materials"));
        material.getClass().getField("mName").set(material,"nesql_meta02_material");
        material.getClass().getField("mEnchantmentTools").set(material,Enchantment.sharpness);
        material.getClass().getField("mEnchantmentToolsLevel").setByte(material,(byte)3);
        Object[] materialTable=(Object[])MagicApi.field(MagicApi.type("gregtech.api.GregTechAPI"),null,"sGeneratedMaterials");
        Object previousMaterial=materialTable[1];
        try {
            materialTable[1]=material;
            ItemStack firstUpdate=plain02.copy();firstUpdate.setItemDamage(25001);
            GtItemNormalization update=new GtItemNormalization(firstUpdate);ItemStack once=update.apply();
            require(once.getTagCompound().getBoolean("GT.HasBeenUpdated")
                    && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(16,once)==3
                    && !firstUpdate.getTagCompound().hasKey("GT.HasBeenUpdated"),"Native Meta02 initialization did not remain owned or add native material level");
            ItemStack twice=new GtItemNormalization(once).apply();
            require(ItemStack.areItemStackTagsEqual(once,twice),"Native Meta02 marker failed to keep normalization idempotent");
            material.getClass().getField("mEnchantmentToolsLevel").setByte(material,(byte)4);
            try{update.verify();throw new AssertionError("Native material enchantment configuration drift was not bound");}
            catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
        } finally {materialTable[1]=previousMaterial;}
    }

    private static final class UnknownGeneric extends gregtech.api.items.GTGenericItem {
        UnknownGeneric(){super("nesql_probe","Probe","");}
        @Override public boolean isItemStackUsable(ItemStack stack){throw new AssertionError("Unknown callback must never execute");}
    }

    /** Configuration is controlled; callbacks and manager bodies come from installed IC2. */
    private static void electricBindings() throws Exception {
        Class<?> binding=Class.forName("com.github.dcysteine.nesql.exporter.capture.GtElectricBindings");
        java.lang.reflect.Constructor<?> constructor=binding.getDeclaredConstructor(ItemStack.class,boolean.class);
        constructor.setAccessible(true);
        java.lang.reflect.Method transfer=binding.getDeclaredMethod("transfer");transfer.setAccessible(true);
        java.lang.reflect.Method verify=binding.getDeclaredMethod("verify");verify.setAccessible(true);
        java.lang.reflect.Method apply=binding.getDeclaredMethod("apply",int.class);apply.setAccessible(true);
        java.lang.reflect.Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)access.get(null);
        net.minecraft.item.Item battery=(net.minecraft.item.Item)unsafe.allocateInstance(MagicApi.type("ic2.core.item.ItemBattery"));
        java.lang.reflect.Field delegate=net.minecraft.item.Item.class.getField("delegate");delegate.setAccessible(true);
        delegate.set(battery,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(battery,net.minecraft.item.Item.class));
        Class<?> base=MagicApi.type("ic2.core.item.BaseElectricItem");
        java.lang.reflect.Field capacity=base.getDeclaredField("maxCharge");capacity.setAccessible(true);capacity.setDouble(battery,1000);
        java.lang.reflect.Field tier=base.getDeclaredField("tier");tier.setAccessible(true);tier.setInt(battery,1);
        java.lang.reflect.Field limit=base.getDeclaredField("transferLimit");limit.setAccessible(true);limit.setDouble(battery,32);
        net.minecraft.item.Item emptyBattery=(net.minecraft.item.Item)unsafe.allocateInstance(MagicApi.type("ic2.core.item.ItemBatteryDischarged"));
        delegate.set(emptyBattery,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(emptyBattery,net.minecraft.item.Item.class));
        capacity.setDouble(emptyBattery,1000);tier.setInt(emptyBattery,1);limit.setDouble(emptyBattery,32);
        battery.setMaxDamage(27);emptyBattery.setMaxDamage(27);
        Class<?> api=MagicApi.type("ic2.api.item.ElectricItem"),items=MagicApi.type("ic2.core.Ic2Items");
        java.lang.reflect.Field managerField=api.getField("manager"),rawField=api.getField("rawManager"),chargedField=items.getField("chargedReBattery"),emptyField=items.getField("reBattery");
        Object priorManager=managerField.get(null),priorRaw=rawField.get(null),priorCharged=chargedField.get(null),priorEmpty=emptyField.get(null);
        try {
            Object raw=MagicApi.type("ic2.core.item.ElectricItemManager").newInstance();
            managerField.set(null,MagicApi.type("ic2.core.item.GatewayElectricItemManager").newInstance());rawField.set(null,raw);
            chargedField.set(null,new ItemStack(battery));emptyField.set(null,new ItemStack(emptyBattery));
            ItemStack offered=new ItemStack(battery);charge(raw,offered,75);
            Object input=constructor.newInstance(offered,false);
            require((Integer)transfer.invoke(input)==75 && charge(raw,offered)==75,"Bound native simulated discharge changed original charge");
            ItemStack many=offered.copy();many.stackSize=2;
            require((Integer)transfer.invoke(constructor.newInstance(many,false))==0,"Binding replaced offered count with consume-one");
            Object output=constructor.newInstance(offered,true);
            ItemStack positive=(ItemStack)apply.invoke(output,125),negative=(ItemStack)apply.invoke(output,-1);
            require(charge(raw,positive)==125 && charge(raw,negative)==0 && charge(raw,offered)==75
                    && positive.getItem()==battery && negative.getItem()==emptyBattery && negative.getItemDamage()==26,
                    "Bound native clear/charge result changed item transition, damage or source");
            capacity.setDouble(battery,999);
            expectBindingFault(verify,input,"recipe_changed","Changed electric capacity escaped configuration binding");
            capacity.setDouble(battery,1000);verify.invoke(input);
            rawField.set(null,MagicApi.type("ic2.core.item.ElectricItemManager").newInstance());
            expectBindingFault(verify,input,"recipe_changed","Replaced native raw manager escaped binding");
            rawField.set(null,raw);verify.invoke(input);
            capacity.setDouble(emptyBattery,999);
            expectBindingFault(verify,output,"recipe_changed","Changed empty electric variant escaped binding");
            capacity.setDouble(emptyBattery,1000);verify.invoke(output);
            try{constructor.newInstance(new ItemStack(new ElectricSpecimen()),false);throw new AssertionError("Unknown electric callback was invoked");}
            catch(java.lang.reflect.InvocationTargetException failure){require(failure.getCause() instanceof Jobs.Fault && ((Jobs.Fault)failure.getCause()).code.equals("recipe_unsupported"),failure.toString());}
            net.minecraft.item.Item gt=(net.minecraft.item.Item)unsafe.allocateInstance(MagicApi.type("gregtech.common.items.MetaGeneratedItem01"));
            delegate.set(gt,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(gt,net.minecraft.item.Item.class));
            java.lang.reflect.Field behavior=MagicApi.type("gregtech.api.items.MetaBaseItem").getDeclaredField("mItemBehaviors");behavior.setAccessible(true);
            behavior.set(gt,new java.util.concurrent.ConcurrentHashMap<>());
            java.util.concurrent.ConcurrentHashMap<Short,Long[]> stats=new java.util.concurrent.ConcurrentHashMap<>();
            Long[] specification={1000L,32L,1L,-3L};stats.put((short)7,specification);
            java.lang.reflect.Field table=MagicApi.type("gregtech.api.items.MetaGeneratedItem").getDeclaredField("mElectricStats");table.setAccessible(true);table.set(gt,stats);
            ItemStack nativeGt=new ItemStack(gt,1,7);nativeGt.setTagInfo("GT.ItemCharge",new net.minecraft.nbt.NBTTagLong(75));
            nativeGt.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("retained"));
            Object gtInput=constructor.newInstance(nativeGt,false),gtOutput=constructor.newInstance(nativeGt,true);
            ItemStack gtPositive=(ItemStack)apply.invoke(gtOutput,125),gtNegative=(ItemStack)apply.invoke(gtOutput,-1);
            require((Integer)transfer.invoke(gtInput)==75 && gtPositive.getTagCompound().getLong("GT.ItemCharge")==125
                    && !gtNegative.getTagCompound().hasKey("GT.ItemCharge") && gtNegative.getTagCompound().getString("owner").equals("retained")
                    && nativeGt.getTagCompound().getLong("GT.ItemCharge")==75,"Native GT self-manager result changed owned charge or unrelated NBT");
            stats.put((short)7,specification.clone());expectBindingFault(verify,gtInput,"recipe_changed","Equal-value GT stats-array replacement escaped binding");
            stats.put((short)7,specification);verify.invoke(gtInput);
            specification[0]=999L;expectBindingFault(verify,gtInput,"recipe_changed","Native GT stat-value drift escaped binding");specification[0]=1000L;
            GTShapelessRecipe retainedRecipe=new GTShapelessRecipe(nativeGt,false,false,false,null,null,gt);
            GtCraftingBindings retained=new GtCraftingBindings();
            try {
                try(GtCraftingCapturePlan plan=GtCraftingCapturePlan.prepareUnordered(retainedRecipe,
                        Collections.singletonList(Collections.singletonList(nativeGt)))) {
                    retained.retain(plan);int boundCount=retained.size();retained.retain(plan);
                    require(boundCount>0&&retained.size()==boundCount,"Native GT dependencies were duplicated for the same concrete state");
                }
                retained.verify();specification[0]=999L;
                try {retained.verify();throw new AssertionError("Closed plan lost its reader-sealing dependency binding");}
                catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
                finally {specification[0]=1000L;}
                retained.verify();
            } finally {retained.close();retained.close();}
            try {retained.verify();throw new AssertionError("Closed native GT dependency reader remained usable");}
            catch(Jobs.Fault failure){require(failure.code.equals("capture_closed"),failure.toString());}
            net.minecraft.item.Item tool=(net.minecraft.item.Item)unsafe.allocateInstance(MagicApi.type("gregtech.common.items.MetaGeneratedTool01"));
            delegate.set(tool,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(tool,net.minecraft.item.Item.class));
            java.lang.reflect.Field toolTable=MagicApi.type("gregtech.api.items.MetaGeneratedTool").getDeclaredField("mToolStats");toolTable.setAccessible(true);
            Map<Short,Object> toolStats=new java.util.concurrent.ConcurrentHashMap<>();toolStats.put((short)0,MagicApi.type("gregtech.common.tools.ToolSaw").newInstance());toolTable.set(tool,toolStats);
            ItemStack electricTool=new ItemStack(tool,1,0);
            net.minecraft.nbt.NBTTagCompound nativeToolStats=new net.minecraft.nbt.NBTTagCompound();
            nativeToolStats.setBoolean("Electric",true);nativeToolStats.setLong("MaxCharge",1000);nativeToolStats.setLong("Voltage",32);
            nativeToolStats.setLong("Tier",1);nativeToolStats.setLong("SpecialData",-3);
            electricTool.setTagInfo("GT.ToolStats",nativeToolStats);electricTool.setTagInfo("GT.ItemCharge",new net.minecraft.nbt.NBTTagLong(75));
            electricTool.addEnchantment(Enchantment.sharpness,1);
            Object toolOutput=constructor.newInstance(electricTool,true);
            ItemStack emptyTool=(ItemStack)apply.invoke(toolOutput,-1),chargedTool=(ItemStack)apply.invoke(toolOutput,125);
            require(emptyTool.getItemDamage()==1 && chargedTool.getItemDamage()==0 && chargedTool.getTagCompound().getLong("GT.ItemCharge")==125
                    && !emptyTool.getTagCompound().hasKey("ench") && electricTool.getTagCompound().hasKey("ench")
                    && electricTool.getTagCompound().getLong("GT.ItemCharge")==75,"Native electric tool parity, enchantment cleanup or input ownership changed");
            Class<?> consumption=Class.forName("com.github.dcysteine.nesql.exporter.capture.GtToolConsumption");
            java.lang.reflect.Constructor<?> toolBinding=consumption.getDeclaredConstructor(ItemStack.class);toolBinding.setAccessible(true);
            java.lang.reflect.Method describe=consumption.getDeclaredMethod("declaration"),toolVerify=consumption.getDeclaredMethod("verify");
            describe.setAccessible(true);toolVerify.setAccessible(true);
            Object electricWear=toolBinding.newInstance(electricTool);
            com.google.gson.JsonObject toolRule=(com.google.gson.JsonObject)describe.invoke(electricWear);
            require(toolRule.get("cost").getAsInt()==200 && toolRule.get("electric").getAsBoolean()
                    && toolRule.getAsJsonObject("wearChance").get("denominator").getAsString().equals("25")
                    && electricTool.getTagCompound().hasKey("ench") && electricTool.getTagCompound().getLong("GT.ItemCharge")==75,
                    "Electric tool wear was executed or replaced by a deterministic container sample");
            ItemStack physical=electricTool.copy();physical.getTagCompound().getCompoundTag("GT.ToolStats").removeTag("Electric");
            physical.getTagCompound().getCompoundTag("GT.ToolStats").setLong("Damage",10);
            physical.getTagCompound().getCompoundTag("GT.ToolStats").setLong("MaxDamage",1000);
            com.google.gson.JsonObject physicalRule=(com.google.gson.JsonObject)describe.invoke(toolBinding.newInstance(physical));
            ItemStack nativeReturn=physical.getItem().getContainerItem(physical.copy());
            require(!physicalRule.get("electric").getAsBoolean() && physicalRule.getAsJsonObject("wearChance").get("denominator").getAsString().equals("1")
                    && nativeReturn.getTagCompound().getCompoundTag("GT.ToolStats").getLong("Damage")==210
                    && physical.getTagCompound().getCompoundTag("GT.ToolStats").getLong("Damage")==10,
                    "Physical tool wear declaration differs from its actual native deterministic branch");
            Object previousTool=toolStats.put((short)0,MagicApi.type("gregtech.common.tools.ToolMortar").newInstance());
            expectBindingFault(toolVerify,electricWear,"recipe_changed","Changed native tool consumption stats escaped binding");
            toolStats.put((short)0,previousTool);toolVerify.invoke(electricWear);
            net.minecraft.item.Item.itemRegistry.addObject(31001,"nesql_bound_native_tool",tool);
            for(ItemStack candidate:Arrays.asList(physical,electricTool)) {
                net.minecraftforge.oredict.ShapedOreRecipe fixed=new net.minecraftforge.oredict.ShapedOreRecipe(new ItemStack(Items.diamond),"t",'t',candidate);
                RegistryRecipes toolReader=NativeCraftingTest.reader(Collections.singletonList(fixed),true);
                try {
                    RecipeRow toolRow=NativeCraftingTest.row(true,candidate,new ItemStack(Items.diamond));
                    require(toolReader.capture(0,toolRow) && toolRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("consume").get("kind").getAsString().equals("gtTool"),
                            "Native crafting invoked tool RNG or exported deterministic container wear");
                    NativeContractFixture.writeOwned(candidate==physical?"native-crafting-tool-physical":"native-crafting-tool-electric",toolRow,
                            Arrays.asList(candidate,new ItemStack(Items.diamond)));
                    toolReader.finishVerification();
                } finally {toolReader.close();}
                bloodOrbTool(candidate,candidate==physical?"physical":"electric");
            }
            net.minecraft.item.Item armor=(net.minecraft.item.Item)unsafe.allocateInstance(MagicApi.type("ic2.core.item.armor.ItemArmorBatpack"));
            delegate.set(armor,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(armor,net.minecraft.item.Item.class));
            Class<?> armorBase=MagicApi.type("ic2.core.item.armor.ItemArmorElectric");
            for(String name:Arrays.asList("maxCharge","transferLimit","tier")) {
                java.lang.reflect.Field member=armorBase.getDeclaredField(name);member.setAccessible(true);
                if(name.equals("tier"))member.setInt(armor,1);else member.setDouble(armor,name.equals("maxCharge")?1000:32);
            }
            java.lang.reflect.Field localField=armorBase.getDeclaredField("allowDamaging");localField.setAccessible(true);
            ThreadLocal<Boolean> allowDamage=new ThreadLocal<>();localField.set(armor,allowDamage);armor.setMaxDamage(27);
            ItemStack armorStack=new ItemStack(armor);Object armorOutput=constructor.newInstance(armorStack,true);
            ItemStack armorResult=(ItemStack)apply.invoke(armorOutput,125);
            require(charge(raw,armorResult)==125 && allowDamage.get()==null && armorStack.getTagCompound()==null,
                    "Native electric armor charge leaked a borrowed ThreadLocal or source NBT");
            allowDamage.set(true);apply.invoke(armorOutput,125);require(allowDamage.get(),"Borrowed true armor flag was not restored");allowDamage.remove();
            GtElectricBindings guarded=new GtElectricBindings(armorStack,true);
            RuntimeException sentinel=new RuntimeException("owned native operation failure");allowDamage.set(true);
            try {
                guarded.ownedCall(()-> {charge(raw,new ItemStack(armor),125);armor.setMaxDamage(26);throw sentinel;});
                throw new AssertionError("Native failure was lost");
            } catch(RuntimeException failure) {
                require(failure==sentinel && allowDamage.get() && failure.getSuppressed().length==1
                        && failure.getSuppressed()[0] instanceof Jobs.Fault
                        && ((Jobs.Fault)failure.getSuppressed()[0]).code.equals("recipe_changed"),
                        "Native failure, suppressed drift or borrowed armor flag was lost");
            } finally {armor.setMaxDamage(27);allowDamage.remove();}
            GTShapelessRecipe electricRecipe=new GTShapelessRecipe(offered,false,false,false,null,null,battery,battery);
            GtCraftingResult boundElectric=GtCraftingResult.evaluate(electricRecipe,NativeCraftingTest.inventory(3,3,offered,offered));
            require(charge(raw,boundElectric.result())==150 && charge(raw,offered)==75,"Complete bound native electric stages changed charge or ownership");
            capacity.setDouble(battery,Integer.MAX_VALUE);capacity.setDouble(emptyBattery,Integer.MAX_VALUE);
            ItemStack enormousLeft=new ItemStack(battery),enormousRight=new ItemStack(battery);
            charge(raw,enormousLeft,1500000000);charge(raw,enormousRight,1500000000);
            GtCraftingResult overflowResult=GtCraftingResult.evaluate(electricRecipe,NativeCraftingTest.inventory(3,3,enormousLeft,enormousRight));
            require(overflowResult.evidence().get("signedTransfer").getAsInt()==-1294967296 && charge(raw,overflowResult.result())==0,
                    "Bound full result widened native signed transfer or recharged a negative total");
            capacity.setDouble(battery,1000);capacity.setDouble(emptyBattery,1000);
            GTShapelessRecipe armorRecipe=new GTShapelessRecipe(armorStack,false,false,false,null,null,battery);
            GtCraftingResult nativeArmor=GtCraftingResult.evaluate(armorRecipe,NativeCraftingTest.inventory(3,3,offered));
            require(charge(raw,nativeArmor.result())==75 && allowDamage.get()==null,"Native recipe callback bypass leaked armor configuration");
            ItemStack noStats=nativeGt.copy();noStats.setItemDamage(8);
            managerField.set(null,null);
            require((Integer)transfer.invoke(constructor.newInstance(noStats,false))==0,"Non-electric GT state invented a manager readiness dependency");
            java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/gt-electric-bindings.json"),
                    com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(object("ic2Positive",RecipeRow.stackEvidence(positive),
                            "ic2Negative",RecipeRow.stackEvidence(negative),"gtPositive",RecipeRow.stackEvidence(gtPositive),
                            "gtNegative",RecipeRow.stackEvidence(gtNegative),"offeredCount2Transfer",0,"originalInputCharge",75,
                            "toolPositive",RecipeRow.stackEvidence(chargedTool),"toolNegative",RecipeRow.stackEvidence(emptyTool),
                            "toolWearRule",toolRule,"physicalWearRule",physicalRule,
                            "armorPositive",RecipeRow.stackEvidence(armorResult),"armorBorrowedFlagRestored",true,
                            "completeElectricStages",boundElectric.evidence(),"completeSignedOverflowStages",overflowResult.evidence(),
                            "completeArmorStages",nativeArmor.evidence(),
                            "actualInstalledCallbackBodies",true,"completeGtCaptureImplemented",false)));
        } finally {managerField.set(null,priorManager);rawField.set(null,priorRaw);chargedField.set(null,priorCharged);emptyField.set(null,priorEmpty);}
    }

    private static void bloodOrbTool(ItemStack candidate,String label)throws Exception {
        NativeCoreFixesTest.version("AWWayofTime","1.7.52");
        ItemStack output=new ItemStack(Items.diamond);
        net.minecraft.item.crafting.IRecipe raw=(net.minecraft.item.crafting.IRecipe)MagicApi.type(
            "WayofTime.alchemicalWizardry.api.items.ShapedBloodOrbRecipe")
            .getConstructor(ItemStack.class,Object[].class).newInstance(output,new Object[]{"t",'t',candidate});
        codechicken.nei.recipe.TemplateRecipeHandler handler=(codechicken.nei.recipe.TemplateRecipeHandler)MagicApi.type(
            "WayofTime.alchemicalWizardry.client.nei.NEIBloodOrbShapedHandler").newInstance();
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            scope.replace(net.minecraft.item.crafting.CraftingManager.class,net.minecraft.item.crafting.CraftingManager.getInstance(),
                "recipes",new ArrayList<>(Collections.singletonList(raw)));
            scope.replace(codechicken.nei.ItemList.class,null,"loadFinished",true);
            com.google.gson.JsonObject origin=object("owner","AWWayofTime","handler",handler.getClass().getName(),"key",handler.getClass().getName());
            RecipeRow row=new RecipeRow(NativeCoreFixesTest.row(candidate,output).facts,origin,
                com.github.dcysteine.nesql.exporter.source.Identity.origin("category",origin),0);
            try(BloodOrbCraftingRecipes reader=new BloodOrbCraftingRecipes(handler)){
                require(reader.capture(0,row),"Blood Orb tool recipe was excluded");
                com.google.gson.JsonObject choice=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
                require(choice.getAsJsonObject("consume").get("kind").getAsString().equals("gtTool")
                    &&choice.getAsJsonArray("returns").size()==0,"Blood Orb tool input lost its native wear law");
                reader.finishVerification();
                NativeContractFixture.writeOwned("native-blood-orb-tool-"+label,row,Arrays.asList(candidate,output));
            }
        }
    }
    private static void expectBindingFault(java.lang.reflect.Method method,Object target,String code,String message) throws Exception {
        try{method.invoke(target);throw new AssertionError(message);}
        catch(java.lang.reflect.InvocationTargetException failure){require(failure.getCause() instanceof Jobs.Fault && ((Jobs.Fault)failure.getCause()).code.equals(code),failure.toString());}
    }

    private static void boundResultStages() throws Exception {
        Class<?> stages=Class.forName("com.github.dcysteine.nesql.exporter.capture.GtCraftingResult");
        java.lang.reflect.Method evaluate=stages.getDeclaredMethod("evaluate",net.minecraft.item.crafting.IRecipe.class,net.minecraft.inventory.InventoryCrafting.class);
        evaluate.setAccessible(true);
        ItemStack base=new ItemStack(Items.diamond,3,2);base.setTagInfo("base",new net.minecraft.nbt.NBTTagString("output-wins"));
        ItemStack paper=new ItemStack(Items.paper),gold=new ItemStack(Items.gold_ingot);
        paper.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("first"));gold.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("last"));
        GTShapedRecipe keeping=new GTShapedRecipe(base,true,true,null,null,"ab",'a',Items.paper,'b',Items.gold_ingot);
        Object first=evaluate.invoke(null,keeping,NativeCraftingTest.inventory(3,3,paper,gold));
        java.lang.reflect.Method result=stages.getDeclaredMethod("result");result.setAccessible(true);
        ItemStack actual=(ItemStack)result.invoke(first);
        require(actual.getTagCompound().getString("owner").equals("first") && actual.stackSize==3 && actual.getItemDamage()==2,
                "Bound GT result did not follow installed FIRST-tagged semantics");
        ItemStack same=new ItemStack(Items.diamond,7,2);same.setTagInfo("base",new net.minecraft.nbt.NBTTagString("input-loses"));
        same.setTagInfo("inputOnly",new net.minecraft.nbt.NBTTagString("retained"));
        GTShapelessRecipe overwrite=new GTShapelessRecipe(base,false,false,true,null,null,same);
        ItemStack merged=(ItemStack)result.invoke(evaluate.invoke(null,overwrite,NativeCraftingTest.inventory(3,3,same)));
        require(merged.getTagCompound().getString("base").equals("output-wins") && merged.getTagCompound().getString("inputOnly").equals("retained")
                && same.stackSize==7 && same.getTagCompound().getString("base").equals("input-loses"),"Bound GT overwrite precedence or owned input changed");
        GTShapedRecipe enchant=new GTShapedRecipe(new ItemStack(Items.diamond),true,false,
                new Enchantment[]{Enchantment.sharpness,Enchantment.sharpness},new int[]{127,2},"p",'p',Items.paper);
        Object signed=evaluate.invoke(null,enchant,NativeCraftingTest.inventory(3,3,paper));
        require(net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(16,(ItemStack)result.invoke(signed))==-127,
                "Bound GT duplicate signed-byte enchantment arithmetic changed");
        java.lang.reflect.Method evidence=stages.getDeclaredMethod("evidence");evidence.setAccessible(true);
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/gt-bound-result-stages.json"),
                com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(array((com.google.gson.JsonObject)evidence.invoke(first),
                        (com.google.gson.JsonObject)evidence.invoke(signed))));
    }

    private static void finiteCapturePlans() throws Exception {
        Class<?> plan=Class.forName("com.github.dcysteine.nesql.exporter.capture.GtCraftingCapturePlan");
        java.lang.reflect.Method prepare=plan.getDeclaredMethod("prepare",net.minecraft.item.crafting.IRecipe.class,List.class,int[][].class);prepare.setAccessible(true);
        ItemStack paper=new ItemStack(Items.paper),taggedPaper=paper.copy(),diamond=new ItemStack(Items.diamond),taggedDiamond=diamond.copy();
        taggedPaper.setTagInfo("ignored",new net.minecraft.nbt.NBTTagString("no-overwrite"));
        taggedDiamond.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("transferred"));
        GTShapelessRecipe recipe=new GTShapelessRecipe(diamond,false,false,true,null,null,Items.paper,Items.diamond);
        List<List<ItemStack>> groups=Arrays.asList(Arrays.asList(paper,taggedPaper),Arrays.asList(diamond,taggedDiamond));
        Object prepared=prepare.invoke(null,recipe,groups,new int[][]{{0,1}});
        java.lang.reflect.Method evidence=plan.getDeclaredMethod("evidence");evidence.setAccessible(true);
        com.google.gson.JsonObject actual=(com.google.gson.JsonObject)evidence.invoke(prepared);
        require(actual.getAsJsonArray("cases").size()==2 && actual.get("candidateProduct").getAsString().equals("4"),
                "Native capture plan did not fold irrelevant tag combinations");
        GTShapelessRecipe greedy=new GTShapelessRecipe(diamond,false,false,false,null,null,Items.paper,Items.gold_ingot);
        greedy.getInput().set(0,new ArrayList<>(Arrays.asList(paper.copy(),new ItemStack(Items.gold_ingot))));
        List<List<ItemStack>> overlap=Arrays.asList(Collections.singletonList(new ItemStack(Items.gold_ingot)),Collections.singletonList(paper));
        Object rejected=prepare.invoke(null,greedy,overlap,new int[][]{{0,1}}),accepted=prepare.invoke(null,greedy,overlap,new int[][]{{1,0}});
        com.google.gson.JsonObject invalid=(com.google.gson.JsonObject)evidence.invoke(rejected);
        com.google.gson.JsonObject valid=(com.google.gson.JsonObject)evidence.invoke(accepted);
        require(invalid.getAsJsonArray("cases").size()==0 && valid.getAsJsonArray("cases").size()==1,
                "Native greedy offered order was replaced by an existence-only allocation");
        java.lang.reflect.Method unordered=plan.getDeclaredMethod("prepareUnordered",net.minecraft.item.crafting.IRecipe.class,List.class);unordered.setAccessible(true);
        Object allOrders=unordered.invoke(null,greedy,overlap);
        com.google.gson.JsonObject anyOrder=(com.google.gson.JsonObject)evidence.invoke(allOrders);
        require(anyOrder.getAsJsonArray("cases").size()==1
                && anyOrder.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonArray("positions").get(0).getAsInt()==1,
                "Shapeless state coverage did not retain the native accepted offered order");
        require(taggedDiamond.getTagCompound().getString("owner").equals("transferred") && taggedPaper.stackSize==1,
                "Native capture plan mutated its candidate declarations");
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/gt-finite-capture-plans.json"),
                com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(object("folded",actual,"greedyRejected",invalid,"greedyAccepted",valid,"allOrders",anyOrder)));
        java.lang.reflect.Method verify=plan.getDeclaredMethod("verify");verify.setAccessible(true);
        java.lang.reflect.Method close=plan.getDeclaredMethod("close");close.setAccessible(true);
        verify.invoke(prepared);verify.invoke(accepted);
        greedy.getInput().set(1,new ItemStack(Items.iron_ingot));
        try {verify.invoke(accepted);throw new AssertionError("Changed native GT input table was not detected");}
        catch(java.lang.reflect.InvocationTargetException failure) {
            require(failure.getCause() instanceof Jobs.Fault&&((Jobs.Fault)failure.getCause()).code.equals("recipe_changed"),failure.toString());
        }
        for(Object retained:Arrays.asList(prepared,rejected,accepted,allOrders)) {
            close.invoke(retained);close.invoke(retained);
            try {verify.invoke(retained);throw new AssertionError("Closed native GT capture plan remained usable");}
            catch(java.lang.reflect.InvocationTargetException failure) {
                require(failure.getCause() instanceof Jobs.Fault&&((Jobs.Fault)failure.getCause()).code.equals("capture_closed"),failure.toString());
            }
        }
        finiteOrderOracle();
    }

    private static void finiteOrderOracle() throws Exception {
        ItemStack paper=new ItemStack(Items.paper),diamond=new ItemStack(Items.diamond);
        ItemStack a=diamond.copy(),b=diamond.copy(),c=diamond.copy(),d=diamond.copy();
        a.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("a"));b.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("b"));
        c.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("c"));d.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("d"));
        List<List<ItemStack>> groups=Arrays.asList(Collections.singletonList(paper),Arrays.asList(diamond,a,b),Arrays.asList(diamond,c,d));
        GTShapelessRecipe nativeRecipe=new GTShapelessRecipe(diamond,false,false,true,null,null,Items.paper,Items.diamond,Items.diamond);
        Set<String> actual=new HashSet<>(),folded=new HashSet<>();
        int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        for(ItemStack first:groups.get(0))for(ItemStack second:groups.get(1))for(ItemStack third:groups.get(2))for(int[] order:orders) {
            ItemStack[] selected={first,second,third};
            net.minecraft.inventory.InventoryCrafting offered=NativeCraftingTest.inventory(3,3,selected[order[0]],selected[order[1]],selected[order[2]]);
            require(nativeRecipe.matches(offered,null),"Native tiny-oracle input unexpectedly failed");
            actual.add(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(RecipeRow.stackEvidence(nativeRecipe.getCraftingResult(offered))));
        }
        try(GtCraftingCapturePlan plan=GtCraftingCapturePlan.prepareUnordered(nativeRecipe,groups)) {
            for(GtCraftingCapturePlan.Case result:plan.cases)folded.add(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(RecipeRow.stackEvidence(result.result.result())));
            require(plan.cases.size()==5&&folded.equals(actual),"Finite all-order coverage lost a native first overwrite result");
            com.google.gson.JsonObject detached=plan.evidence();detached.getAsJsonObject("rules").addProperty("overwriteNbt",false);
            require(plan.evidence().getAsJsonObject("rules").get("overwriteNbt").getAsBoolean(),"Evidence leaked mutable plan rules");
            writePlan("native-gt-crafting-overwrite",plan);
        }
        // Nine identical slots have 362880 offered permutations but one result state.
        GTShapelessRecipe nine=new GTShapelessRecipe(diamond,false,false,false,null,null,
                Items.paper,Items.paper,Items.paper,Items.paper,Items.paper,Items.paper,Items.paper,Items.paper,Items.paper);
        List<List<ItemStack>> repeated=new ArrayList<>();for(int index=0;index<9;index++)repeated.add(Collections.singletonList(paper));
        try(GtCraftingCapturePlan plan=GtCraftingCapturePlan.prepareUnordered(nine,repeated)) {
            require(plan.cases.size()==1,"Nine native shapeless permutations were not folded");
        }
        GTShapelessRecipe keeping=new GTShapelessRecipe(diamond,false,true,false,null,null,Items.paper,Items.diamond);
        ItemStack zero=paper.copy(),negative=diamond.copy();zero.setTagInfo("zero",new net.minecraft.nbt.NBTTagFloat(0f));
        negative.setTagInfo("zero",new net.minecraft.nbt.NBTTagFloat(-0f));
        try(GtCraftingCapturePlan plan=GtCraftingCapturePlan.prepareUnordered(keeping,
                Arrays.asList(Collections.singletonList(zero),Collections.singletonList(negative)))) {
            require(plan.cases.size()==2,"Equal signed-zero tags lost their distinct first-tagged native result identities");
            writePlan("native-gt-crafting-keeping",plan);
        }
    }

    private static void writePlan(String name,GtCraftingCapturePlan plan) throws Exception {
        List<ItemStack> records=new ArrayList<>(Arrays.asList(plan.prototype(),plan.normalizedBase()));
        for(List<GtCraftingStates.Candidate> group:plan.groups)for(GtCraftingStates.Candidate candidate:group)records.add(candidate.stack.copy());
        for(GtCraftingCapturePlan.Case sample:plan.cases)records.add(sample.result.result());
        RecipeRow row=NativeCraftingTest.row(false,records.toArray(new ItemStack[0]));
        int slot=0;for(List<GtCraftingStates.Candidate> group:plan.groups) {
            List<RecipeRow.Ingredient> choices=new ArrayList<>();
            for(GtCraftingStates.Candidate candidate:group)choices.add(new RecipeRow.Ingredient(candidate.stack.copy(),1,false,
                    object("kind","wildcard","meta",false,"nbt",true)));
            row.nativeCraftingInput(slot++,choices);
        }
        records.addAll(GtCraftingTransport.attach(plan,row));
        NativeContractFixture.writeOwned(name,row,records);
    }

    /** The fixture inherits the ACTUAL native MetaBaseItem callback at runtime.
     * Only its neutral electric/fluid statistics are controlled; no native body changes.
     */
    private static final class MetaNormalizationSpecimen extends gregtech.api.items.GTGenericItem {
        MetaNormalizationSpecimen(){super("nesql_normalization","Probe","");}
        public Long[] getElectricStats(ItemStack stack){return null;}
        public Long[] getFluidContainerStats(ItemStack stack){return null;}
    }

    private static void stateSpace() {
        ItemStack paper = new ItemStack(Items.paper), namedPaper = paper.copy(), diamond = new ItemStack(Items.diamond), namedDiamond = diamond.copy();
        namedPaper.setTagInfo("name",new net.minecraft.nbt.NBTTagString("unrelated"));
        namedDiamond.setTagInfo("name",new net.minecraft.nbt.NBTTagString("output-tag"));
        List<List<GtCraftingStates.Candidate>> groups = Arrays.asList(
                Arrays.asList(new GtCraftingStates.Candidate(paper,0,1),new GtCraftingStates.Candidate(namedPaper,0,1)),
                Arrays.asList(new GtCraftingStates.Candidate(diamond,0,2),new GtCraftingStates.Candidate(namedDiamond,0,2)));
        List<GtCraftingStates.State> states = GtCraftingStates.prepare(new GtCraftingStates.Rules(false,true,true,diamond),groups,new int[]{0,1});
        require(states.size()==2,"Irrelevant tag carrier was not folded out of native GT overwrite states");
        GTShapelessRecipe recipe = new GTShapelessRecipe(diamond,false,false,true,null,null,Items.paper,Items.diamond);
        Set<String> nativeOutputs = new HashSet<>(), foldedOutputs = new HashSet<>();
        for(GtCraftingStates.Candidate a:groups.get(0))for(GtCraftingStates.Candidate b:groups.get(1))
            nativeOutputs.add(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(RecipeRow.stackEvidence(
                    recipe.getCraftingResult(NativeCraftingTest.inventory(3,3,a.stack,b.stack)))));
        for(GtCraftingStates.State state:states) {
            net.minecraft.inventory.InventoryCrafting grid = NativeCraftingTest.inventory(3,3,groups.get(0).get(state.witness[0]).stack,groups.get(1).get(state.witness[1]).stack);
            require(recipe.matches(grid,null),"Native state witness does not match the real recipe");
            foldedOutputs.add(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(RecipeRow.stackEvidence(recipe.getCraftingResult(grid))));
        }
        require(foldedOutputs.equals(nativeOutputs),"Native GT folding lost a result reachable by exported candidates");
        // An overlapping first requirement greedily claims gold, leaving paper
        // unable to fulfill the gold-only second requirement. The other order works.
        List<List<GtCraftingStates.Candidate>> overlap = Arrays.asList(
                Collections.singletonList(new GtCraftingStates.Candidate(new ItemStack(Items.gold_ingot),0,3)),
                Collections.singletonList(new GtCraftingStates.Candidate(paper,0,1)));
        GtCraftingStates.Rules unordered = new GtCraftingStates.Rules(false,false,true,diamond);
        GTShapelessRecipe nativeOverlap = new GTShapelessRecipe(diamond,false,false,false,null,null,Items.paper,Items.gold_ingot);
        nativeOverlap.getInput().set(0,new ArrayList<>(Arrays.asList(paper.copy(),new ItemStack(Items.gold_ingot))));
        require(!nativeOverlap.matches(NativeCraftingTest.inventory(3,3,new ItemStack(Items.gold_ingot),paper),null)
                && nativeOverlap.matches(NativeCraftingTest.inventory(3,3,paper,new ItemStack(Items.gold_ingot)),null),
                "Actual installed shapeless greedy matcher differs from prepared allocation");
        require(GtCraftingStates.prepare(unordered,overlap,new int[]{0,1}).isEmpty()
                && GtCraftingStates.prepare(unordered,overlap,new int[]{1,0}).size()==1,
                "Shapeless greedy allocation lost offered inventory order");
        List<List<GtCraftingStates.Candidate>> electric = Arrays.asList(
                Collections.singletonList(new GtCraftingStates.Candidate(paper,1500000000,1)),
                Collections.singletonList(new GtCraftingStates.Candidate(diamond,1500000000,2)));
        require(GtCraftingStates.prepare(new GtCraftingStates.Rules(false,false,false,diamond),electric,new int[]{0,1}).get(0).transfer == -1294967296,
                "Prepared native transfer was widened or clamped instead of Java int accumulation");
        List<List<GtCraftingStates.Candidate>> keeping = Arrays.asList(
                Collections.singletonList(new GtCraftingStates.Candidate(paper,0,1)),
                Collections.singletonList(new GtCraftingStates.Candidate(namedDiamond,0,2)));
        require(GtCraftingStates.prepare(new GtCraftingStates.Rules(true,false,false,diamond),keeping,new int[]{0,1}).isEmpty(),
                "Native state space accepted mixed hasTag inputs");
        ItemStack zero = paper.copy(), negativeZero = diamond.copy();
        zero.setTagInfo("value",new net.minecraft.nbt.NBTTagFloat(0f));
        negativeZero.setTagInfo("value",new net.minecraft.nbt.NBTTagFloat(-0f));
        List<List<GtCraftingStates.Candidate>> zeroGroups = Arrays.asList(
                Collections.singletonList(new GtCraftingStates.Candidate(zero,0,1)),
                Collections.singletonList(new GtCraftingStates.Candidate(negativeZero,0,2)));
        require(GtCraftingStates.prepare(new GtCraftingStates.Rules(true,false,false,diamond),zeroGroups,new int[]{0,1}).size()==1,
                "Keeping NBT was inferred from unequal signed-zero hashes rather than native equality");
        try {GtCraftingStates.prepare(unordered,groups,new int[]{0,0});throw new AssertionError("Duplicate offered cell accepted");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_unsupported"),failure.toString());}
    }

    private static void nativeTagEquality() throws Exception {
        com.google.gson.JsonArray cases = new com.google.gson.JsonArray();
        equality(cases,"float-signed-zero",new net.minecraft.nbt.NBTTagFloat(0f),new net.minecraft.nbt.NBTTagFloat(-0f));
        equality(cases,"float-nan",new net.minecraft.nbt.NBTTagFloat(Float.NaN),new net.minecraft.nbt.NBTTagFloat(Float.NaN));
        equality(cases,"double-signed-zero",new net.minecraft.nbt.NBTTagDouble(0d),new net.minecraft.nbt.NBTTagDouble(-0d));
        equality(cases,"double-nan",new net.minecraft.nbt.NBTTagDouble(Double.NaN),new net.minecraft.nbt.NBTTagDouble(Double.NaN));
        equality(cases,"integer-type",new net.minecraft.nbt.NBTTagInt(1),new net.minecraft.nbt.NBTTagByte((byte)1));
        net.minecraft.nbt.NBTTagCompound a = new net.minecraft.nbt.NBTTagCompound(), b = new net.minecraft.nbt.NBTTagCompound();
        a.setFloat("value",0f); b.setFloat("value",-0f); equality(cases,"compound-signed-zero",a,b);
        a.setFloat("value",Float.NaN); b.setFloat("value",Float.NaN); equality(cases,"compound-nan",a,b);
        net.minecraft.nbt.NBTTagList one = new net.minecraft.nbt.NBTTagList(), two = new net.minecraft.nbt.NBTTagList();
        java.lang.reflect.Field type = net.minecraft.nbt.NBTTagList.class.getDeclaredField("tagType"); type.setAccessible(true);
        type.setByte(one,(byte)1); type.setByte(two,(byte)2); equality(cases,"empty-list-type",one,two);
        two = new net.minecraft.nbt.NBTTagList(); type.setByte(two,(byte)1); equality(cases,"empty-list-same-type",one,two);
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/gt-native-tag-equality.json"),
                com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(cases));
    }
    private static void equality(com.google.gson.JsonArray cases,String name,net.minecraft.nbt.NBTBase left,net.minecraft.nbt.NBTBase right) {
        cases.add(object("name",name,"left",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(left),
                "right",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(right),"nativeEqual",left.equals(right)));
    }

    /** Real pinned result and electric managers on owned inventories; no live player/world. */
    private static void nativeResults() throws Exception {
        ItemStack base = new ItemStack(Items.diamond, 3, 2);
        base.setTagInfo("base", new net.minecraft.nbt.NBTTagString("output-wins"));
        ItemStack paper = new ItemStack(Items.paper), gold = new ItemStack(Items.gold_ingot);
        paper.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("first"));
        gold.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("last"));
        GTShapedRecipe transfer = new GTShapedRecipe(base, true, true, null, null, "ab", 'a', Items.paper, 'b', Items.gold_ingot);
        ItemStack nativeCopy = transfer.getCraftingResult(NativeCraftingTest.inventory(3, 3, paper, gold));
        require(nativeCopy.getTagCompound().getString("owner").equals("first") && !nativeCopy.getTagCompound().hasKey("base")
                && nativeCopy.stackSize == 3 && nativeCopy.getItemDamage() == 2, "Installed GT FIRST-tagged full replacement changed");
        ItemStack sameItem = new ItemStack(Items.diamond, 7, 2);
        sameItem.setTagInfo("base", new net.minecraft.nbt.NBTTagString("input-loses"));
        sameItem.setTagInfo("inputOnly", new net.minecraft.nbt.NBTTagString("retained"));
        GTShapelessRecipe overwrite = new GTShapelessRecipe(base, false, false, true, null, null, sameItem);
        ItemStack merged = overwrite.getCraftingResult(NativeCraftingTest.inventory(3, 3, sameItem));
        require(merged.getTagCompound().getString("base").equals("output-wins")
                && merged.getTagCompound().getString("inputOnly").equals("retained") && sameItem.stackSize == 7,
                "Installed overwrite root-tag precedence or input ownership changed");
        GTShapedRecipe enchanted = new GTShapedRecipe(new ItemStack(Items.diamond), true, false,
                new Enchantment[]{Enchantment.sharpness, Enchantment.sharpness}, new int[]{127, 2}, "p", 'p', Items.paper);
        ItemStack enchantResult = enchanted.getCraftingResult(NativeCraftingTest.inventory(3, 3, paper));
        require(net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId,enchantResult) == -127,
                "Installed addEnchantment duplicate/signed-byte level changed");
        com.google.gson.JsonObject evidence = object("nativeVersion","5.09.51.482", "firstTagged",RecipeRow.stackEvidence(nativeCopy),
                "overwrite",RecipeRow.stackEvidence(merged), "duplicateSignedByteEnchantment",RecipeRow.stackEvidence(enchantResult),
                "productionResultAdapterComplete",false);
        Class<?> api = MagicApi.type("ic2.api.item.ElectricItem");
        java.lang.reflect.Field managerField = api.getField("manager");
        Object previous = managerField.get(null);
        try {
            Object manager = MagicApi.type("ic2.core.item.ElectricItemManager").newInstance();
            managerField.set(null, manager);
            ElectricSpecimen battery = new ElectricSpecimen();
            net.minecraft.item.Item.itemRegistry.addObject(31000, "nesql_fixture:gt_crafting_battery", battery);
            ItemStack left = new ItemStack(battery), right = new ItemStack(battery), charged = new ItemStack(battery);
            charge(manager,left,1500000000); charge(manager,right,1500000000); charge(manager,charged,100);
            GTShapelessRecipe electric = new GTShapelessRecipe(charged, false, false, false, null, null, battery, battery);
            ItemStack overflow = electric.getCraftingResult(NativeCraftingTest.inventory(3,3,left,right));
            require(charge(manager,overflow) == 0 && charge(manager,left) == 1500000000 && charge(manager,right) == 1500000000,
                    "Installed crafting signed-int accumulation or simulated input discharge changed");
            ItemStack small = new ItemStack(battery);
            charge(manager,small,50);
            ItemStack positive = electric.getCraftingResult(NativeCraftingTest.inventory(3,3,small,small));
            require(charge(manager,positive) == 100 && charge(manager,charged) == 100 && charge(manager,small) == 50,
                    "Native positive total, initial output reset or borrowed input charge changed");
            ItemStack stacked = small.copy(); stacked.stackSize = 2;
            ItemStack countSensitive = electric.getCraftingResult(NativeCraftingTest.inventory(3,3,stacked,small));
            require(charge(manager,countSensitive) == 50 && stacked.stackSize == 2,
                    "Native electric discharge lost offered count semantics");
            evidence.add("electric",object("manager",manager.getClass().getName(),"signedOverflow",RecipeRow.stackEvidence(overflow),
                    "positive",RecipeRow.stackEvidence(positive),"offeredCountSensitive",RecipeRow.stackEvidence(countSensitive),
                    "inputCount",stacked.stackSize, "inputCharge",Double.toString(charge(manager,small)), "originalOutputCharge",Double.toString(charge(manager,charged))));
        } finally { managerField.set(null, previous); }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/gt-crafting-native-results.json"),
                com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(evidence));
    }
    private static void charge(Object manager,ItemStack stack,double amount) {
        MagicApi.invoke(manager.getClass(), manager, "charge", new Class<?>[]{ItemStack.class,double.class,int.class,boolean.class,boolean.class},stack,amount,1,true,false);
    }
    private static double charge(Object manager,ItemStack stack) {
        return (Double)MagicApi.invoke(manager.getClass(),manager,"getCharge",new Class<?>[]{ItemStack.class},stack);
    }

    /** Deterministic callback inputs to the ACTUAL native electric manager, not a fake manager. */
    private static final class ElectricSpecimen extends net.minecraft.item.Item {
        public boolean canProvideEnergy(ItemStack stack) { return true; }
        public net.minecraft.item.Item getChargedItem(ItemStack stack) { return this; }
        public net.minecraft.item.Item getEmptyItem(ItemStack stack) { return this; }
        public double getMaxCharge(ItemStack stack) { return Integer.MAX_VALUE; }
        public int getTier(ItemStack stack) { return 1; }
        public double getTransferLimit(ItemStack stack) { return Integer.MAX_VALUE; }
    }
    /** Only declare the fixture's API interface at runtime; native classes/methods are untouched. */
    public static final class ElectricDefinitions implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes) {
            if(name.equals("com.github.dcysteine.nesql.exporter.capture.NativeGtCraftingTest$MetaNormalizationSpecimen")){
                org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,0);
                node.superName="gregtech/api/items/MetaBaseItem";
                for(Object raw:node.methods){org.objectweb.asm.tree.MethodNode method=(org.objectweb.asm.tree.MethodNode)raw;
                    if(!method.name.equals("<init>"))continue;
                    method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
                    method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(25,0));
                    method.instructions.add(new org.objectweb.asm.tree.LdcInsnNode("nesql_normalization"));
                    method.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(183,node.superName,"<init>","(Ljava/lang/String;)V",false));
                    method.instructions.add(new org.objectweb.asm.tree.InsnNode(177));method.maxStack=2;method.maxLocals=1;
                }
                org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);node.accept(writer);return writer.toByteArray();
            }
            if (name.equals("net.minecraft.nbt.NBTTagCompound")) {
                // Match the installed GT access transformer, without changing any method body.
                org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(bytes).accept(node,0);
                for (Object field : node.fields) {
                    org.objectweb.asm.tree.FieldNode nativeField = (org.objectweb.asm.tree.FieldNode)field;
                    if (nativeField.name.equals("tagMap")) nativeField.access = (nativeField.access & ~6) | 1;
                }
                org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0); node.accept(writer); return writer.toByteArray();
            }
            if (!name.equals("com.github.dcysteine.nesql.exporter.capture.NativeGtCraftingTest$ElectricSpecimen")) return bytes;
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            node.interfaces.add("ic2/api/item/IElectricItem");
            org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0); node.accept(writer); return writer.toByteArray();
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
