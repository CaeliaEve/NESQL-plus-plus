package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Method;

/** Pinned, read-only container callbacks; never a general item callback allowlist. */
final class ItemCallbacks {
    private static final String RAIL_BUCKET="mods.railcraft.common.fluids.ItemBucketRailcraft";
    private static final String BLOOD_BATTERY="WayofTime.alchemicalWizardry.common.items.EnergyBattery";
    private static final String ARMOK="fox.spiteful.avaritia.compat.bloodmagic.ItemOrbArmok";
    private static final String FOCUS="thaumcraft.common.items.wands.foci.ItemFocusWarding";
    private static final String BLOOD_BAUBLE="com.arc.bloodarsenal.common.items.bauble.ItemBauble";
    private static final String CANISTER="micdoodle8.mods.galacticraft.core.items.ItemCanisterGeneric";
    private ItemCallbacks() {}
    static Method method(Item item, String mcp, String srg, Class<?>... args) {
        return method(item, Item.class, mcp, srg, args);
    }
    static <T extends Item> Method method(Item item, Class<T> nativeOwner, String mcp, String srg, Class<?>... args) {
        if (!nativeOwner.isInstance(item)) throw fault("Native item callback owner differs");
        // Resolve the environment on the class that actually declares the callback.
        String name = ReflectionHelper.<T>findMethod(nativeOwner, null, new String[]{mcp, srg, MagicApi.runtimeMethod(nativeOwner,mcp)}, args).getName();
        try { return item.getClass().getMethod(name, args); }
        catch (NoSuchMethodException error) { throw fault("Missing native item method: " + name); }
    }
    static boolean hasContainer(ItemStack input) {
        if (NativeOcContainers.supports(input.getItem())) return NativeOcContainers.container(input,false,false)!=null;
        if (selfContainer(input.getItem())) return true;
        auditContainer(input);
        String entry=GtItemListBinding.containerEntry(input,method(input.getItem(),"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName());
        if(entry!=null)return new GtItemListBinding(entry,input).result()!=null;
        return input.getItem().hasContainerItem(input.copy());
    }
    private static void auditContainer(ItemStack input) {
        Item item = input.getItem();
        Class<?> presence = method(item, "hasContainerItem", "hasContainerItem", ItemStack.class).getDeclaringClass();
        if (presence == Item.class) {
            if (method(item, "hasContainerItem", "func_77634_r").getDeclaringClass() != Item.class)
                throw fault("Unadapted legacy container predicate: " + item.getClass().getName());
        } else if (presence.getName().equals("ic2.core.item.resources.ItemCell")) {
            MagicApi.version("ItemCallbacks","IC2"); MagicApi.version("ItemCallbacks","hodgepodge");
            if (method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass() != presence
                    || method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
                throw fault("Patched IC2 cell callback changed");
        } else {
            if (!presence.getName().equals("gregtech.api.items.GTGenericItem"))
                throw fault("Unadapted container predicate: " + item.getClass().getName());
            MagicApi.version("ItemCallbacks","gregtech_nh");
            if (method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                throw fault("GT container metadata getter changed");
            String getter = method(item, "getContainerItem", "getContainerItem", ItemStack.class).getDeclaringClass().getName();
            switch (getter) {
                case "gregtech.api.items.GTGenericItem":
                case "gregtech.api.items.MetaGeneratedItemX01":
                case "gregtech.api.items.MetaGeneratedItemX32":
                case "gregtech.common.items.MetaGeneratedItem01":
                case "gregtech.common.items.MetaGeneratedItem02":
                case "gregtech.common.items.MetaGeneratedItem98":
                case "gregtech.common.items.MetaGeneratedItem99":
                case "bartworks.system.material.BWMetaGeneratedItems":
                    gtContainerDependencies(item,input,getter);break;
                default: throw fault("Unadapted GT container result: " + getter);
            }
        }
    }
    private static void gtContainerDependencies(Item item,ItemStack input,String getter){
        // The installed 5.09.51.482 bytecode reads material/prefix fields directly.
        // Newer source calls getMaterial/getOrePrefix; those methods are not its contract.
        // copyAmount copies this native stack. Never invoke a foreign ItemStack.copy override.
        if(getter.equals("gregtech.api.items.MetaGeneratedItemX01")){
            Object prefix=MagicApi.field(item,"mPrefix");
            if(prefix!=null)checkContainerStack(MagicApi.field(prefix,"mContainerItem"));
        }else if(getter.equals("gregtech.api.items.MetaGeneratedItemX32")||getter.equals("gregtech.common.items.MetaGeneratedItem01")){
            Object prefixes=MagicApi.field(item,"mGeneratedPrefixList");
            int meta=net.minecraft.init.Items.feather.getDamage(input);
            if(meta>=0&&meta<32000&&prefixes instanceof Object[]&&meta/1000<((Object[])prefixes).length){
                Object prefix=((Object[])prefixes)[meta/1000];if(prefix!=null)checkContainerStack(MagicApi.field(prefix,"mContainerItem"));
            }
        }
    }
    private static void checkContainerStack(Object value){if(value!=null&&value.getClass()!=ItemStack.class)throw fault("Unadapted GT container stack copy callback");}
    static ItemStack container(ItemStack input, boolean ignoreMeta) {
        return container(input,ignoreMeta,null);
    }
    static ItemStack container(ItemStack input, boolean ignoreMeta, java.util.List<GtItemListBinding> bindings) {
        Item item=input.getItem();
        if (NativeOcContainers.supports(item)) return NativeOcContainers.container(input,ignoreMeta,false);
        if (selfContainer(item)) return input.copy();
        if (galacticCanister(item)) return galacticContainer(input, ignoreMeta);
        // The supported native overrides use metadata, never offered NBT or count.
        if (ignoreMeta && method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass()!=Item.class
                && !method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName().equals("bartworks.system.material.BWMetaGeneratedItems"))
            throw fault("Container predicate needs fixed metadata");
        auditContainer(input);
        String entry=GtItemListBinding.containerEntry(input,method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName());
        if(entry!=null){
            GtItemListBinding binding=new GtItemListBinding(entry,input);
            if(bindings!=null)bindings.add(binding);
            return binding.result();
        }
        if (!hasContainer(input)) return null;
        boolean railBucket=method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName().equals(RAIL_BUCKET);
        if (!railBucket && method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass()==Item.class
                && (method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass()!=Item.class
                    || method(item,"getContainerItem","func_77668_q").getDeclaringClass()!=Item.class))
            throw fault("Unadapted native container result: "+item.getClass().getName());
        ItemStack result=railBucket?railBucketContainer(item).copy():item.getContainerItem(input.copy());
        if (result==null || result.getItem()==null || result.stackSize<=0) throw fault("Native container predicate has no positive result");
        return result.copy();
    }
    /** TileInfusionMatrix calls the getter directly, without a hasContainerItem gate. */
    static ItemStack pedestalContainer(ItemStack input, java.util.List<GtItemListBinding> bindings) {
        NativeStackAudit.audit(input);
        Item item=input.getItem();
        if (NativeOcContainers.supports(item)) return NativeOcContainers.container(input,false,true);
        if (selfContainer(item)) return input.copy();
        if (galacticCanister(item)) return galacticContainer(input, false);
        String owner=method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName();
        if(owner.equals(Item.class.getName())) {
            if(method(item,"getContainerItem","func_77668_q").getDeclaringClass()!=Item.class)
                throw fault("Unadapted pedestal legacy container callback: "+item.getClass().getName());
        } else {
            switch(owner) {
                case "ic2.core.item.resources.ItemCell":
                case "gregtech.api.items.GTGenericItem":
                case "gregtech.api.items.MetaGeneratedItemX01":
                case "gregtech.api.items.MetaGeneratedItemX32":
                case "gregtech.common.items.MetaGeneratedItem01":
                case "gregtech.common.items.MetaGeneratedItem02":
                case "gregtech.common.items.MetaGeneratedItem98":
                case "gregtech.common.items.MetaGeneratedItem99": auditContainer(input);break;
                case "bartworks.system.material.BWMetaGeneratedItems": auditContainer(input);break;
                case RAIL_BUCKET: break; // Its direct getter does not read either container predicate.
                default: throw fault("Unadapted pedestal container result: "+owner);
            }
        }
        String entry=GtItemListBinding.containerEntry(input,owner);
        ItemStack result;
        if(entry!=null){GtItemListBinding binding=new GtItemListBinding(entry,input);bindings.add(binding);result=binding.result();}
        else result=owner.equals(RAIL_BUCKET)?railBucketContainer(item).copy():item.getContainerItem(input.copy());
        if(result==null)return null;
        NativeStackAudit.audit(result);
        if(result.stackSize<=0)throw fault("Native pedestal container has a nonpositive count");
        return result.copy();
    }
    private static ItemStack railBucketContainer(Item item) {
        MagicApi.version("ItemCallbacks","Railcraft");
        Class<?> owner=MagicApi.type(RAIL_BUCKET);
        if(!owner.isInstance(item))throw fault("Native Railcraft bucket class identity differs");
        Object raw=MagicApi.field(owner,item,"container");
        if(!(raw instanceof ItemStack))throw fault("Native Railcraft bucket has no container stack");
        NativeStackAudit.auditRaw((ItemStack)raw);
        return (ItemStack)raw;
    }
    private static boolean galacticCanister(Item item) {
        String name=item.getClass().getName();
        return name.equals("micdoodle8.mods.galacticraft.core.items.ItemOilCanister")
            ||name.equals("micdoodle8.mods.galacticraft.core.items.ItemFuelCanister");
    }
    /** Forestry/workbench context, never Thermal Expansion's stack-trace-dependent transposer branch. */
    private static ItemStack galacticContainer(ItemStack input, boolean ignoreMeta) {
        MagicApi.version("ItemCallbacks","GalacticraftCore");
        Item item=input.getItem();NativeStackAudit.auditRaw(input);
        if(ignoreMeta||item.getClass()!=MagicApi.type(item.getClass().getName())
            ||!method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName().equals(CANISTER)
            ||method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass()!=Item.class
            ||method(item,"hasContainerItem","func_77634_r").getDeclaringClass()!=Item.class
            ||method(item,"getContainerItem","func_77668_q").getDeclaringClass()!=Item.class
            ||method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
            throw fault("Galacticraft canister callback policy changed");
        Object container=MagicApi.field(Item.class,item,"containerItem");
        if(container==null)return null;
        if(!(container instanceof Item))throw fault("Invalid native Galacticraft canister container");
        if(item==container&&net.minecraft.init.Items.feather.getDamage(input)==1001)return null;
        return new ItemStack((Item)container,1,1001);
    }
    /** These pinned methods return the exact offered stack, independently of its count/meta/NBT. */
    private static boolean selfContainer(Item item) {
        String getter=method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName();
        if(getter.equals(BLOOD_BAUBLE)){
            MagicApi.version("ItemCallbacks","BloodArsenal");
            String name=item.getClass().getName();
            if(!name.equals("com.arc.bloodarsenal.common.items.bauble.SacrificeAmulet")
                    &&!name.equals("com.arc.bloodarsenal.common.items.bauble.SelfSacrificeAmulet"))
                throw fault("Unadapted Blood Arsenal self-container owner: "+name);
            if(item.getClass()!=MagicApi.type(name)
                    ||!method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass().getName().equals(BLOOD_BAUBLE))
                throw fault("Blood Arsenal self-container callback changed");
            return true;
        }
        if(getter.equals(FOCUS)) {
            MagicApi.version("ItemCallbacks","Thaumcraft");MagicApi.version("ItemCallbacks","dreamcraft");
            if(item.getClass()!=MagicApi.type(FOCUS)
                    ||!Boolean.TRUE.equals(MagicApi.field(MagicApi.type("com.dreammaster.coremod.DreamCoreMod"),null,"patchItemFocusWarding"))
                    ||MagicApi.field(Item.class,item,"containerItem")!=item
                    ||method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass()!=Item.class
                    ||method(item,"hasContainerItem","func_77634_r").getDeclaringClass()!=Item.class
                    ||method(item,"doesContainerItemLeaveCraftingGrid","func_77630_h",ItemStack.class).getDeclaringClass()!=Item.class)
                throw fault("GTNH transformed warding focus container changed");
            return true;
        }
        if (!getter.equals(BLOOD_BATTERY) && !getter.equals(ARMOK)) return false;
        Class<?> presence=method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass();
        if (getter.equals(BLOOD_BATTERY)) {
            MagicApi.version("ItemCallbacks","AWWayofTime");
            if (!presence.getName().equals(BLOOD_BATTERY)) throw fault("Blood battery container predicate changed");
        } else {
            MagicApi.version("ItemCallbacks","Avaritia");
            if (presence!=Item.class || !method(item,"hasContainerItem","func_77634_r").getDeclaringClass().getName().equals(ARMOK))
                throw fault("Armok container predicate changed");
        }
        if (!method(item,"doesContainerItemLeaveCraftingGrid","func_77630_h",ItemStack.class).getDeclaringClass().getName().equals(getter))
            throw fault("Blood container crafting-grid policy changed");
        return true;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
