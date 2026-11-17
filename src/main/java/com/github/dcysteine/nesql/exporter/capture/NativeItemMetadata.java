package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.object;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Logical native getter values are distinct from the physical metadata in item identity. */
final class NativeItemMetadata {
    private static final String MIRROR="vazkii.botania.common.item.ItemManaMirror";
    private NativeItemMetadata() {}
    static boolean computed(ItemStack stack) {
        Item item=stack.getItem();
        if(!item.getClass().getName().equals(MIRROR))return false;
        MagicApi.version("NativeItemMetadata","Botania");
        try{
            if(item.getClass()!=MagicApi.type(MIRROR)
                ||ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=item.getClass()
                ||item.getClass().getMethod("getMana",ItemStack.class).getDeclaringClass()!=item.getClass())
                throw fault();
        }catch(NoSuchMethodException missing){throw fault();}
        return true;
    }
    static int value(ItemStack stack) {
        NativeStackAudit.auditRaw(stack);
        if(!computed(stack)){NativeStackAudit.audit(stack);return stack.getItemDamage();}
        int mana=stack.hasTagCompound()?stack.getTagCompound().getInteger("mana"):0;
        // The installed getter uses two Java float operations followed by f2i and wrapped subtraction.
        return 1000-(int)((float)mana/1000000.0f*1000.0f);
    }
    static JsonObject rule(ItemStack template,boolean wildcard,boolean ignoreNbt) {
        if(computed(template)&&!wildcard)return object("kind","nativeItemMetadata","callback","botaniaManaMirror","value",value(template),"nbt",ignoreNbt);
        return wildcard||ignoreNbt?object("kind","wildcard","meta",wildcard,"nbt",ignoreNbt):object("kind","exact");
    }
    private static Jobs.Fault fault(){return new Jobs.Fault("recipe_unsupported","Botania mana mirror metadata callback changed");}
}
