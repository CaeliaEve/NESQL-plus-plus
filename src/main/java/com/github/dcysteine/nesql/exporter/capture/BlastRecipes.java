package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** IC2 heat/air state machine, not an electric one-input machine. */
final class BlastRecipes implements RegistryRecipes {
    private static final String HANDLER="ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler";
    private static final String MACHINE="ic2.core.block.machine.tileentity.TileEntityBlastFurnace";
    private final TemplateRecipeHandler handler;
    private final Object manager;
    private final List<Map.Entry<?,?>> sources=new ArrayList<>();
    private final int heat;
    private final ItemStack air,cell;
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals(HANDLER);}
    private static Object manager(){version("IC2","2.2.828-experimental");return field(type("ic2.api.recipe.Recipes"),null,"blastfurance");}
    BlastRecipes(TemplateRecipeHandler handler){this(handler,manager(),(Integer)field(type(MACHINE),null,"maxHeat"),
        (ItemStack)field(type("ic2.core.Ic2Items"),null,"airCell"),(ItemStack)field(type("ic2.core.Ic2Items"),null,"cell"));}
    BlastRecipes(TemplateRecipeHandler handler,Object manager,int heat,ItemStack air,ItemStack cell){
        if(!supports(handler)||manager==null||!manager.getClass().getName().equals("ic2.core.BasicMachineRecipeManager"))throw fault("Unknown IC2 blast registry");
        this.handler=handler;this.manager=manager;this.heat=heat;this.air=stack(air);this.cell=stack(cell);
        if(this.air.getTagCompound()!=null&&!this.air.getTagCompound().hasNoTags())throw fault("Tagged IC2 air wrapper requires native tag-hash matching");
        this.air.setTagCompound(null);
        // getRecipe would execute arbitrary callbacks for these; do not call them for display.
        if(!((List<?>)field(manager,"uncacheableRecipes")).isEmpty())throw fault("Unadapted IC2 blast selector callback");
        Map<?,?> registry=(Map<?,?>)invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]);
        if(registry.size()>262144)throw fault("IC2 blast registry budget exceeded");
        for(Map.Entry<?,?> source:registry.entrySet()){
            Jobs.checkpoint();Object key=source.getKey();
            if(key==null||!(key.getClass().getName().equals("ic2.api.recipe.RecipeInputItemStack")||key.getClass().getName().equals("ic2.api.recipe.RecipeInputOreDict")))throw fault("Unadapted IC2 blast predicate");
            sources.add(new AbstractMap.SimpleImmutableEntry<>(source));
        }
    }
    public int size(){return sources.size();}
    public boolean capture(int index,RecipeRow row){
        if(!((List<?>)field(manager,"uncacheableRecipes")).isEmpty())throw fault("IC2 blast selectors changed to an unadapted callback");
        Map.Entry<?,?> source=sources.get(index);
        List<RecipeRow.Ingredient> inputs=Ic2Recipes.ingredients(source.getKey());
        JsonArray containers=new JsonArray(),primary=new JsonArray(),airContainers=new JsonArray();
        for(RecipeRow.Ingredient input:inputs){
            primary.add(container(input.item,input.rule.get("meta").getAsBoolean(),row.facts));
            if(type("ic2.core.item.ItemUpgradeModule").isInstance(input.item.getItem()))throw fault("Native blast input slot rejects upgrade items");
            Object selected=invoke(manager.getClass(),manager,"getRecipe",new Class<?>[]{ItemStack.class},input.item.copy());
            if(selected==null||field(selected,"a")!=source.getKey()||field(selected,"b")!=source.getValue())throw new Jobs.Fault("slot_changed","IC2 blast cache selects another recipe for the displayed input");
        }
        Object value=source.getValue();
        if(value==null||!value.getClass().getName().equals("ic2.api.recipe.RecipeOutput"))throw fault("Unknown IC2 blast result");
        List<?> raw=(List<?>)field(value,"items");if(raw.isEmpty())throw fault("IC2 blast has no first result");
        List<ItemStack> outputs=new ArrayList<>();for(int i=0;i<Math.min(2,raw.size());i++)outputs.add(stack(raw.get(i)));
        // Native work() only reads these two results and ignores recipe metadata.
        TemplateRecipeHandler.CachedRecipe cached=cached(inputs,outputs);
        @SuppressWarnings("unchecked") List<PositionedStack> positions=(List<PositionedStack>)field(cached,"ingredients");
        if(positions.size()!=1)throw new Jobs.Fault("slot_changed","IC2 blast primary layout changed");
        row.itemInput(positions.get(0),0,inputs,false);
        boolean ignoreMeta=!air.getHasSubtypes()&&!air.isItemStackDamageable();
        airContainers.add(container(air,ignoreMeta,row.facts)); containers.add(primary); containers.add(airContainers);
        row.itemInput(null,1,Collections.singletonList(new RecipeRow.Ingredient(air.copy(),1,false,object("kind","untagged","meta",ignoreMeta))),false);
        row.slot("input","item",1,15,38);
        for(JsonElement input:row.inputs)for(JsonElement c:input.getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind","staged"));
        row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns")
            .add(object("kind","item","id",row.facts.item(cell),"amount",Integer.toString(cell.stackSize)));
        row.itemOutput(cached.getResult(),0,outputs.get(0),10000);
        if(outputs.size()>1)row.itemOutput(cached.getOtherStacks().get(0),1,outputs.get(1),10000,
            object("kind","potential","stat","ic2:slagSpace","nominal",Integer.toString(outputs.get(1).stackSize)));
        row.record.add("process",object("kind","ic2Blast","heat",heat,"containers",containers));
        handler.arecipes.clear();handler.arecipes.add(cached);return true;
    }
    private TemplateRecipeHandler.CachedRecipe cached(List<RecipeRow.Ingredient> inputs,List<ItemStack> outputs){
        try{
            List<ItemStack> copy=new ArrayList<>();for(ItemStack stack:outputs)copy.add(stack.copy());
            Class<?> result=type("ic2.api.recipe.RecipeOutput"),base=type("ic2.neiIntegration.core.recipehandler.MachineRecipeHandler");
            Object value=result.getConstructor(NBTTagCompound.class,List.class).newInstance(null,copy);
            return (TemplateRecipeHandler.CachedRecipe)type(base.getName()+"$CachedIORecipe").getConstructor(base,type("ic2.api.recipe.IRecipeInput"),result).newInstance(handler,Ic2Recipes.projection(inputs),value);
        }catch(ReflectiveOperationException error){Jobs.Fault failure=fault("Cannot construct IC2 blast layout");failure.initCause(error);throw failure;}
    }
    private static ItemStack stack(Object value){if(!(value instanceof ItemStack)||((ItemStack)value).getItem()==null||((ItemStack)value).stackSize<=0)throw fault("Invalid IC2 blast item");return ((ItemStack)value).copy();}
    private static JsonElement container(ItemStack stack,boolean ignoreMeta,Facts facts){
        ItemStack result=ItemCallbacks.container(stack,ignoreMeta);
        return result==null?com.google.gson.JsonNull.INSTANCE:object("id",facts.item(result),"amount",Integer.toString(result.stackSize));
    }
    static int[][] progressBars(){return new int[][]{{64,16,176,51,27,27,20,3}};}
    static void draw(TemplateRecipeHandler handler,int index){Ic2Recipes.scene(handler,()->{handler.drawBackground(index);handler.drawForeground(index);});}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
