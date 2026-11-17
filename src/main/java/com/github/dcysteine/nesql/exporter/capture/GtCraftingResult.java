package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Audited installed GT result stages for a concrete owned offered inventory.
 * Each result is checked against the original result callback, with every reached
 * normalization/electric callback bound first. It never invokes container wear,
 * matcher/world callbacks or RNG. A specimen is not an arbitrary-inventory proof.
 * GtCraftingTransport owns the separate multi-input observation payload.
 */
final class GtCraftingResult implements AutoCloseable {
    private final ItemStack result;
    private final JsonObject evidence;
    private Runnable verification;
    private final List<GtCraftingBinding> bindings;
    private GtCraftingResult(ItemStack result,JsonObject evidence,Runnable verification,List<GtCraftingBinding> bindings) {
        this.result=result.copy();this.evidence=evidence;this.verification=verification;
        this.bindings=bindings;
    }
    ItemStack result(){return result.copy();}
    JsonObject evidence(){return new com.google.gson.JsonParser().parse(evidence.toString()).getAsJsonObject();}
    void verify() {
        if(verification==null)throw new Jobs.Fault("capture_closed","GT concrete result binding is closed");
        verification.run();
    }
    List<GtCraftingBinding> bindings(){verify();return new ArrayList<>(bindings);}
    @Override public void close(){verification=null;bindings.clear();}

    static GtCraftingResult evaluate(IRecipe recipe,InventoryCrafting offered) {
        if(!GtCraftingRules.supports(recipe)||offered==null||offered.getSizeInventory()!=9)
            throw fault("GT result stages require the pinned recipe and a concrete 3 x 3 offered inventory");
        Definition definition=new Definition(recipe);
        ItemStack[] inputs=new ItemStack[9];
        for(int index=0;index<9;index++) {
            ItemStack source=offered.getStackInSlot(index);
            if(source!=null&&(source.getItem()==null||source.stackSize<=0))throw fault("Invalid concrete GT offered stack");
            inputs[index]=source==null?null:source.copy();
        }
        GtItemNormalization first=new GtItemNormalization(definition.output);
        ItemStack initial=first.apply(),staged=initial.copy();int inherited=-1,overwritten=-1;
        if(definition.rules.get("keepingNbt").getAsBoolean())for(int index=0;index<9;index++) {
            if(inputs[index]!=null&&inputs[index].hasTagCompound()) {
                staged.setTagCompound((NBTTagCompound)inputs[index].getTagCompound().copy());inherited=index;break;
            }
        }
        if(definition.rules.get("overwriteNbt").getAsBoolean())for(int index=0;index<9;index++) {
            ItemStack input=inputs[index];if(input==null||!input.hasTagCompound())continue;
            if(ItemCallbacks.method(input.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=net.minecraft.item.Item.class
                    ||ItemCallbacks.method(staged.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=net.minecraft.item.Item.class)
                throw fault("Native GT overwrite requires an audited metadata predicate");
            if(!(Boolean)invoke(type("gregtech.api.util.GTUtility"),null,"areStacksEqual",
                    new Class<?>[]{ItemStack.class,ItemStack.class,boolean.class},input.copy(),staged.copy(),true))continue;
            NBTTagCompound merged=(NBTTagCompound)input.getTagCompound().copy();
            if(staged.hasTagCompound())for(Object key:staged.getTagCompound().func_150296_c()) {
                String name=(String)key;NBTBase tag=staged.getTagCompound().getTag(name);merged.setTag(name,tag.copy());
            }
            staged.setTagCompound(merged);overwritten=index;break;
        }
        GtElectricBindings electric=new GtElectricBindings(staged,true);
        List<GtElectricBindings> sources=new ArrayList<>();JsonArray transfers=array();int sum=0;
        if(electric.active())for(int index=0;index<9;index++) {
            ItemStack input=inputs[index];if(input==null)continue;
            Jobs.checkpoint();GtElectricBindings binding=new GtElectricBindings(input,false);sources.add(binding);
            int amount=binding.transfer();sum+=amount; // Installed Java int wrap, including negative totals.
            transfers.add(object("cell",index,"amount",amount,"binding",binding.declaration()));
        }
        staged=electric.apply(sum);ItemStack afterElectric=staged.copy();
        for(int index=0;index<definition.enchantments.length;index++) {
            Jobs.checkpoint();Enchantment enchantment=definition.enchantments[index];
            int level=EnchantmentHelper.getEnchantmentLevel(enchantment.effectId,staged)+definition.levels[index];
            invoke(type("gregtech.api.util.GTUtility$ItemNBT"),null,"addEnchantment",
                    new Class<?>[]{ItemStack.class,Enchantment.class,int.class},staged,enchantment,level);
        }
        GtItemNormalization last=new GtItemNormalization(staged);ItemStack expected=last.apply();
        Runnable verification=()-> {
            definition.verify();first.verify();electric.verify();last.verify();for(GtElectricBindings source:sources)source.verify();
        };
        verification.run();
        InventoryCrafting owned=inventory(inputs);
        ItemStack actual;
        try {actual=electric.ownedCall(()->recipe.getCraftingResult(owned));}
        catch(RuntimeException|Error failure) {
            try {verification.run();}
            catch(RuntimeException|Error drift) {if(drift!=failure)failure.addSuppressed(drift);}
            throw failure;
        }
        verification.run();
        if(actual==null||actual.getItem()==null||actual.stackSize<=0||actual.getItem()!=expected.getItem()
                ||!CanonicalJson.digest(RecipeRow.stackEvidence(expected)).equals(CanonicalJson.digest(RecipeRow.stackEvidence(actual))))
            throw new Jobs.Fault("slot_changed","Audited GT stages differ from the installed native result callback");
        List<GtCraftingBinding> bindings=new ArrayList<>(Arrays.asList(first,last,electric));bindings.addAll(sources);
        return new GtCraftingResult(actual,object("rules",definition.rules,"normalizedBase",RecipeRow.stackEvidence(initial),
                "firstNormalization",first.declaration(),"inheritedCell",inherited,"overwriteCell",overwritten,
                "electric",electric.declaration(),"inputTransfers",transfers,"signedTransfer",sum,
                "afterElectric",RecipeRow.stackEvidence(afterElectric),"lastNormalization",last.declaration(),
                "actualNativeResult",RecipeRow.stackEvidence(actual),"completeProductionTransport",false),verification,bindings);
    }
    private static InventoryCrafting inventory(ItemStack[] inputs) {
        InventoryCrafting owned=new InventoryCrafting(new Container(){
            @Override public boolean canInteractWith(EntityPlayer player){return false;}
        },3,3);
        for(int index=0;index<9;index++)if(inputs[index]!=null)owned.setInventorySlotContents(index,inputs[index].copy());
        return owned;
    }
    private static final class Definition {
        final IRecipe recipe;final JsonObject rules;final ItemStack output,borrowedOutput;
        final Object enchantmentArray,levelArray;final Enchantment[] enchantments;final int[] levels;final String signature;
        Definition(IRecipe recipe) {
            this.recipe=recipe;rules=GtCraftingRules.definition(recipe);borrowedOutput=recipe.getRecipeOutput();
            if(borrowedOutput==null||borrowedOutput.getItem()==null||borrowedOutput.stackSize<=0)throw fault("GT crafting has no positive base result");
            output=borrowedOutput.copy();enchantmentArray=field(recipe,"mEnchantmentsAdded");levelArray=field(recipe,"mEnchantmentLevelsAdded");
            enchantments=enchantmentArray==null?new Enchantment[0]:((Enchantment[])enchantmentArray).clone();
            levels=levelArray==null?new int[0]:((int[])levelArray).clone();signature=read();
        }
        String read(){return CanonicalJson.digest(object("rules",GtCraftingRules.definition(recipe),"output",RecipeRow.stackEvidence(recipe.getRecipeOutput())));}
        void verify() {
            if(recipe.getRecipeOutput()!=borrowedOutput||field(recipe,"mEnchantmentsAdded")!=enchantmentArray
                    ||field(recipe,"mEnchantmentLevelsAdded")!=levelArray||!signature.equals(read()))throw changed();
            if(enchantmentArray!=null)for(int index=0;index<enchantments.length;index++)
                if(((Enchantment[])enchantmentArray)[index]!=enchantments[index])throw changed();
        }
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native GT result definition changed");}
}
