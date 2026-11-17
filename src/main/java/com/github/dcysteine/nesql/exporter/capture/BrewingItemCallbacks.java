package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Read-only potion expressions; foreign configuration is bound by the recipe source. */
final class BrewingItemCallbacks {
    private static final String WITHERING="crazypants.enderzoo.item.ItemWitheringDust";
    private static final String CONFUSING="crazypants.enderzoo.item.ItemConfusingDust";
    private BrewingItemCallbacks() {}
    static Object configuration() {
        return cpw.mods.fml.common.Loader.instance().getIndexedModList().containsKey("EnderZoo")
            ?MagicApi.field(MagicApi.type("crazypants.enderzoo.config.Config"),null,"useAltWitherPotionEffectMask"):null;
    }
    static String effect(ItemStack stack) {
        NativeStackAudit.audit(stack);
        Item item=stack.getItem();
        Class<?> getter=ItemCallbacks.method(item,"getPotionEffect","getPotionEffect",ItemStack.class).getDeclaringClass();
        Class<?> admission=ItemCallbacks.method(item,"isPotionIngredient","isPotionIngredient",ItemStack.class).getDeclaringClass();
        if(getter.getName().equals(WITHERING)||getter.getName().equals(CONFUSING)){
            MagicApi.version("BrewingItemCallbacks","EnderZoo");
            if(item.getClass()!=getter||admission!=Item.class)
                throw fault(item);
            if(getter.getName().equals(CONFUSING))return "+0+1+2-3&4-4+13";
            return Boolean.TRUE.equals(configuration())?"+0+1+2+3&4-4+13":"+0+1-2+3&4-4+13";
        }
        if((getter!=Item.class&&!getter.getName().equals("net.minecraft.item.ItemFishFood"))
            ||(admission!=Item.class&&!admission.getName().equals("net.minecraft.item.ItemFishFood")))throw fault(item);
        return item.isPotionIngredient(stack)?item.getPotionEffect(stack):null;
    }
    private static Jobs.Fault fault(Item item) {
        return new Jobs.Fault("recipe_unsupported","Unadapted potion ingredient callback: "+item.getClass().getName());
    }
}
