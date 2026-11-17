package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Exact finite concrete-candidate result cases. The caller supplies actual offered
 * positions, not inferred pixel layout. No Cartesian input tuples are enumerated.
 * This plan is not a transport or proof for arbitrary player tags/registry aliases.
 */
final class GtCraftingCapturePlan implements AutoCloseable {
    static final class Case {
        final int placement;final int[] positions;final GtCraftingStates.State state;final GtCraftingResult result;
        Case(int placement,int[] positions,GtCraftingStates.State state,GtCraftingResult result) {
            this.placement=placement;this.positions=positions.clone();this.state=state;this.result=result;
        }
    }
    final List<List<GtCraftingStates.Candidate>> groups;
    final List<Case> cases=new ArrayList<>();
    private final List<GtElectricBindings> sources=new ArrayList<>(),roots=new ArrayList<>();
    private final IRecipe recipe;
    private final JsonObject rules;
    private final GtItemNormalization first;
    private final BigInteger product;
    private final String signature;
    private final ItemStack outputAnchor;
    private final Object inputAnchor;
    private final boolean allOrders;
    private boolean closed;

    private GtCraftingCapturePlan(IRecipe recipe,List<List<ItemStack>> candidates,int[][] placements) {
        if(!GtCraftingRules.supports(recipe)||candidates.isEmpty()||candidates.size()>9
                ||placements!=null&&(placements.length<1||placements.length>2)||placements==null&&!(recipe instanceof ShapelessOreRecipe))
            throw fault("Invalid pinned GT candidate plan");
        this.recipe=recipe;allOrders=placements==null;rules=GtCraftingRules.definition(recipe);signature=definition();outputAnchor=recipe.getRecipeOutput();
        inputAnchor=recipe instanceof ShapedOreRecipe?((ShapedOreRecipe)recipe).getInput():((ShapelessOreRecipe)recipe).getInput();
        first=new GtItemNormalization(recipe.getRecipeOutput());ItemStack base=first.apply();
        boolean shaped=recipe instanceof ShapedOreRecipe;
        List<?> required=shaped?Arrays.asList(((ShapedOreRecipe)recipe).getInput()):((ShapelessOreRecipe)recipe).getInput();
        List<Object> occupied=new ArrayList<>();for(Object requirement:required)if(requirement!=null)occupied.add(requirement);
        if(occupied.size()!=candidates.size())throw fault("Native GT requirement/candidate counts differ");
        BigInteger cardinality=BigInteger.ONE;int total=0;
        for(List<ItemStack> group:candidates) {
            if(group==null||group.isEmpty()||group.size()>65536||(total+=group.size())>65536)throw limit("Invalid native GT candidate budget");
            cardinality=cardinality.multiply(BigInteger.valueOf(group.size()));
            for(ItemStack stack:group) {
                Jobs.checkpoint();if(stack==null||stack.getItem()==null||stack.stackSize<=0)
                    throw fault("Native GT concrete candidate is unavailable");
                // Audit every offered getter before the electric/overwrite probes,
                // which also read metadata through the native equality helper.
                NativeStackAudit.auditLogical(stack);
                if(net.minecraft.init.Items.feather.getDamage(stack)==32767)throw fault("Native GT concrete candidate is a wildcard");
            }
        }
        product=cardinality;groups=new ArrayList<>();
        try {
            boolean possible=potentialElectric(base,candidates);
            for(List<ItemStack> group:candidates) {
                List<GtCraftingStates.Candidate> prepared=new ArrayList<>();
                for(ItemStack stack:group) {
                    Jobs.checkpoint();
                    int mask=0,transfer=0;
                    for(int index=0;index<occupied.size();index++)if(matches(occupied.get(index),stack))mask|=1<<index;
                    if(possible) {GtElectricBindings binding=new GtElectricBindings(stack,false);sources.add(binding);transfer=binding.transfer();}
                    prepared.add(new GtCraftingStates.Candidate(stack,transfer,mask));
                }
                groups.add(prepared);
            }
            GtCraftingStates.Rules stateRules=new GtCraftingStates.Rules(rules.get("keepingNbt").getAsBoolean(),
                    rules.get("overwriteNbt").getAsBoolean(),!shaped,base);
            if(allOrders)for(GtCraftingStates.State state:GtCraftingStates.prepareAnyOrder(stateRules,groups)) {
                int[] positions=new int[candidates.size()];for(int cell=0;cell<positions.length;cell++)positions[state.order[cell]]=cell;
                capture(0,positions,state);
            }
            else for(int placement=0;placement<placements.length;placement++) {
                int[] positions=placements[placement];int[] order=order(positions,candidates.size());
                for(GtCraftingStates.State state:GtCraftingStates.prepare(stateRules,groups,order)) {
                    capture(placement,positions,state);
                }
            }
            verify();
        } catch(RuntimeException|Error failure) {close();throw failure;}
    }
    static GtCraftingCapturePlan prepare(IRecipe recipe,List<List<ItemStack>> candidates,int[][] placements) {
        return new GtCraftingCapturePlan(recipe,candidates,placements);
    }
    static GtCraftingCapturePlan prepareUnordered(IRecipe recipe,List<List<ItemStack>> candidates) {
        return new GtCraftingCapturePlan(recipe,candidates,null);
    }
    ItemStack prototype(){verify();return recipe.getRecipeOutput().copy();}
    ItemStack normalizedBase(){verify();return first.apply();}
    JsonObject rules(){verify();return new com.google.gson.JsonParser().parse(rules.toString()).getAsJsonObject();}
    List<GtCraftingBinding> bindings() {
        verify();List<GtCraftingBinding> result=new ArrayList<>();result.add(first);result.addAll(sources);result.addAll(roots);
        for(Case sample:cases)result.addAll(sample.result.bindings());return result;
    }
    private void capture(int placement,int[] positions,GtCraftingStates.State state) {
        Jobs.checkpoint();if(cases.size()>=65536)throw limit("Combined native GT placement states exceed budget");
        InventoryCrafting owned=inventory(positions,state.witness);
        if(!recipe.matches(owned,null))throw new Jobs.Fault("slot_changed","Prepared GT state does not match the pinned native matcher");
        cases.add(new Case(placement,positions,state,GtCraftingResult.evaluate(recipe,owned)));
    }
    private boolean potentialElectric(ItemStack base,List<List<ItemStack>> candidates) {
        if(!type("ic2.api.item.IElectricItem").isInstance(base.getItem()))return false;
        GtElectricBindings original=new GtElectricBindings(base,true);roots.add(original);boolean possible=original.active();
        if(rules.get("keepingNbt").getAsBoolean()||rules.get("overwriteNbt").getAsBoolean())for(List<ItemStack> group:candidates)for(ItemStack input:group) {
            Jobs.checkpoint();if(input==null||!input.hasTagCompound())continue;
            ItemStack root=base.copy();
            if(rules.get("keepingNbt").getAsBoolean())root.setTagCompound((NBTTagCompound)input.getTagCompound().copy());
            else {
                if(!(Boolean)invoke(type("gregtech.api.util.GTUtility"),null,"areStacksEqual",
                        new Class<?>[]{ItemStack.class,ItemStack.class,boolean.class},input.copy(),base.copy(),true))continue;
                NBTTagCompound merged=(NBTTagCompound)input.getTagCompound().copy();
                if(root.hasTagCompound())for(Object key:root.getTagCompound().func_150296_c()) {
                    String name=(String)key;NBTBase tag=root.getTagCompound().getTag(name);merged.setTag(name,tag.copy());
                }
                root.setTagCompound(merged);
            }
            GtElectricBindings binding=new GtElectricBindings(root,true);roots.add(binding);possible|=binding.active();
        }
        return possible;
    }
    private static boolean matches(Object required,ItemStack stack) {
        if(required instanceof ItemStack)return OreDictionary.itemMatches((ItemStack)required,stack,false);
        if(!(required instanceof ArrayList<?>))throw fault("Unadapted pinned GT crafting predicate");
        for(Object choice:(List<?>)required) {
            if(!(choice instanceof ItemStack))throw fault("Malformed pinned GT crafting candidate predicate");
            if(OreDictionary.itemMatches((ItemStack)choice,stack,false))return true;
        }
        return false;
    }
    private static int[] order(int[] positions,int count) {
        if(positions.length!=count)throw fault("Native GT offered placement count differs");
        boolean[] used=new boolean[9];Integer[] slots=new Integer[count];
        for(int index=0;index<count;index++) {
            int cell=positions[index];if(cell<0||cell>8||used[cell])throw fault("Invalid native GT offered placement");
            used[cell]=true;slots[index]=index;
        }
        Arrays.sort(slots,Comparator.comparingInt(index->positions[index]));int[] order=new int[count];
        for(int index=0;index<count;index++)order[index]=slots[index];return order;
    }
    private InventoryCrafting inventory(int[] positions,int[] witness) {
        InventoryCrafting owned=new InventoryCrafting(new Container(){@Override public boolean canInteractWith(EntityPlayer player){return false;}},3,3);
        for(int slot=0;slot<witness.length;slot++)owned.setInventorySlotContents(positions[slot],groups.get(slot).get(witness[slot]).stack.copy());
        return owned;
    }
    void verify() {
        if(closed)throw new Jobs.Fault("capture_closed","GT candidate plan is closed");
        Object current=recipe instanceof ShapedOreRecipe?((ShapedOreRecipe)recipe).getInput():((ShapelessOreRecipe)recipe).getInput();
        if(recipe.getRecipeOutput()!=outputAnchor||current!=inputAnchor||!signature.equals(definition()))
            throw new Jobs.Fault("recipe_changed","Native GT candidate-plan definition changed");
        first.verify();for(GtElectricBindings source:sources)source.verify();for(GtElectricBindings root:roots)root.verify();
        for(Case sample:cases)sample.result.verify();
    }
    JsonObject evidence() {
        verify();JsonArray records=array();
        for(Case sample:cases) {
            JsonArray witness=array();for(int index:sample.state.witness)witness.add(value(index));
            JsonArray positions=array();for(int cell:sample.positions)positions.add(value(cell));
            records.add(object("placement",sample.placement,"positions",positions,"witness",witness,"potentialTransfer",sample.state.transfer,
                    "inheritedTag",sample.state.inherited==null?null:sample.state.inherited.tag,
                    "overwriteTag",sample.state.overwritten==null?null:sample.state.overwritten.tag,"result",sample.result.evidence()));
        }
        return object("candidateProduct",product.toString(),"allOfferedOrders",allOrders,"cases",records,
                "rules",new com.google.gson.JsonParser().parse(rules.toString()),"productionTransportConnected",false);
    }
    private String definition() {
        NativeCraftingRecipes.Raw raw=new NativeCraftingRecipes.Raw(recipe);JsonArray inputs=array();
        for(List<ItemStack> group:raw.inputs) {
            if(group==null){inputs.add(value(null));continue;}
            JsonArray choices=array();for(ItemStack stack:group)choices.add(RecipeRow.stackEvidence(stack));inputs.add(choices);
        }
        return CanonicalJson.digest(object("rules",raw.gt,"output",RecipeRow.stackEvidence(raw.output),
                "width",raw.width,"height",raw.height,"mirror",raw.mirror,"inputs",inputs));
    }
    @Override public void close() {
        if(closed)return;closed=true;for(Case sample:cases)sample.result.close();cases.clear();sources.clear();roots.clear();groups.clear();
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault limit(String message){return new Jobs.Fault("recipe_limit",message);}
}
