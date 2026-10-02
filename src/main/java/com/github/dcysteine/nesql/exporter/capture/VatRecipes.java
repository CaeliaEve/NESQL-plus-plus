package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.capture.EnderInputs.Pattern;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.collect.Table;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** The native precomputed table is authoritative; NEI's independently multiplied amounts are not. */
final class VatRecipes implements RegistryRecipes {
    private static final String HANDLER="crazypants.enderio.nei.VatRecipeHandler", RECIPE="crazypants.enderio.machine.vat.VatRecipe";
    private final TemplateRecipeHandler handler;
    private final List<Pair> pairs=new ArrayList<>();
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals(HANDLER);}
    VatRecipes(TemplateRecipeHandler handler){this(handler,registry());}
    private static List<?> registry(){version("EnderIO","2.9.28");Object manager=invoke(type("crazypants.enderio.machine.vat.VatRecipeManager"),null,"getInstance",new Class<?>[0]);return (List<?>)call(manager,"getRecipes");}
    VatRecipes(TemplateRecipeHandler handler,List<?> recipes) {
        if(!supports(handler)||recipes==null||recipes.size()>262144)throw fault("Unknown or oversized Vat registry");
        this.handler=handler;
        for(Object recipe:recipes) {
            Jobs.checkpoint();
            if(recipe==null||!recipe.getClass().getName().equals(RECIPE))throw fault("Unknown Vat recipe implementation");
            if(!(Boolean)call(recipe,"isValid"))continue;
            Entry entry=new Entry(recipe);
            for(Input left:entry.inputs)if(left.slot==0) {
                if(entry.two) {for(Input right:entry.inputs)if(right.slot==1)add(entry,left,right);}
                else add(entry,left,null);
            }
        }
    }
    private void add(Entry entry,Input left,Input right) {
        FluidStack in=entry.in.get(left.source,right==null?left.source:right.source),out=entry.out.get(left.source,right==null?left.source:right.source);
        if(in==null&&out==null)return;
        if(in==null||out==null||in.getFluid()==null||out.getFluid()==null||in.amount<0||out.amount<0)
            throw fault("Invalid native Vat fluid table entry");
        // Such a record can block later recipes despite never fitting a tank. Do not silently remove it.
        if(in.amount>8000||out.amount>8000)throw fault("Vat recipe exceeds its native tank capacity");
        if(pairs.size()>=262144)throw fault("Vat table expansion exceeds budget");
        pairs.add(new Pair(entry,left,right,in.copy(),out.copy()));
    }
    public int size(){return pairs.size();}
    public boolean capture(int index,RecipeRow row) {
        Pair pair=pairs.get(index);
        List<RecipeRow.Ingredient> left=choices(pair.entry,pair.left,row.facts),right=pair.right==null?null:choices(pair.entry,pair.right,row.facts);
        if(left.isEmpty()||right!=null&&right.isEmpty())return false;
        input(row,0,left,51);if(right!=null)input(row,1,right,100);
        row.fluidInput(null,0,pair.in,pair.in.amount==0);
        JsonArray extra=array();
        if(right==null)for(Input input:pair.entry.inputs)for(Pattern pattern:input.patterns)
            extra.add(object("id",row.facts.item(pattern.item),"rule",pattern.rule(),"amount",input.amount));
        if(extra.size()>4096)throw fault("Vat optional consumption exceeds budget");
        row.record.add("process",object("kind","vat","energy",pair.entry.energy,"extra",extra,
                "zeroOutput",pair.out.amount==0?row.facts.fluid(pair.out):null));
        // Tanks are rendered natively below; their semantic links stay in the existing details area.
        if(pair.out.amount>0)row.fluidOutput(null,0,pair.out);
        handler.arecipes.clear();handler.arecipes.add(handler.new CachedRecipe(){@Override public PositionedStack getResult(){return null;}});
        return true;
    }
    private static void input(RecipeRow row,int slot,List<RecipeRow.Ingredient> choices,int x) {
        List<ItemStack> display=new ArrayList<>();for(RecipeRow.Ingredient c:choices){ItemStack item=c.item.copy();item.stackSize=(int)Math.min(64,c.amount);display.add(item);}
        row.itemInput(new PositionedStack(display,x,1,false),slot,choices,false);
        for(com.google.gson.JsonElement value:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices")) {
            JsonObject c=value.getAsJsonObject();if(c.getAsJsonObject("consume").get("kind").getAsString().equals("consume"))c.add("consume",object("kind","upto"));
        }
    }
    /** Intersect slot-selection predicates with the separate, first-match consumption predicates. */
    private static List<RecipeRow.Ingredient> choices(Entry entry,Input selected,Facts facts) {
        List<Pattern> slotPrior=new ArrayList<>();
        for(Input in:entry.inputs){if(in==selected)break;if(in.slot==selected.slot)slotPrior.addAll(in.patterns);}
        List<RecipeRow.Ingredient> result=new ArrayList<>();List<Pattern> consumptionPrior=new ArrayList<>();
        for(Input consume:entry.inputs) {
            List<Pattern> excluded=new ArrayList<>(slotPrior);excluded.addAll(consumptionPrior);
            for(Pattern wanted:selected.patterns)for(Pattern consuming:consume.patterns) {
                Pattern base=wanted.intersect(consuming);if(base==null)continue;
                boolean covered=false;for(Pattern p:excluded)if(p.covers(base)){covered=true;break;}if(covered)continue;
                ItemStack example=base.item.copy();
                if(base.anyMeta)while(matches(excluded,example)){if(example.getItemDamage()>=65535)throw fault("No bounded Vat metadata example");example.setItemDamage(example.getItemDamage()+1);}
                JsonArray filters=array();Set<String> unique=new HashSet<>();
                for(Pattern p:excluded)if(p.overlaps(base)) {
                    JsonObject filter=object("id",facts.item(p.item),"rule",p.rule());
                    if(unique.add(CanonicalJson.digest(filter)))filters.add(filter);
                    if(filters.size()>4096)throw fault("Vat matching filter exceeds budget");
                }
                JsonObject rule=filters.size()==0?base.rule():object("kind","except","base",base.rule(),"exclude",filters);
                result.add(new RecipeRow.Ingredient(example,Math.max(1,consume.amount),consume.amount<=0,rule));
                if(result.size()>65536)throw fault("Vat alternatives exceed budget");
            }
            consumptionPrior.addAll(consume.patterns);
        }
        return result;
    }
    private static boolean matches(List<Pattern> patterns,ItemStack item){for(Pattern p:patterns)if(p.matches(item))return true;return false;}
    @SuppressWarnings("unchecked") private static final class Entry {
        final List<Input> inputs=new ArrayList<>();final boolean two;final int energy;
        final Table<Object,Object,FluidStack> in,out;
        Entry(Object source) {
            Object[] raw=(Object[])call(source,"getInputs");if(raw==null||raw.length>4096)throw fault("Invalid Vat input registry");
            for(Object input:raw){EnderInputs.check(input);if(!(Boolean)call(input,"isFluid"))inputs.add(new Input(input));}
            two=(Integer)field(source,"requiredItems")==2;energy=(Integer)call(source,"getEnergyRequired");
            in=(Table<Object,Object,FluidStack>)field(source,"inputFluidStacks");out=(Table<Object,Object,FluidStack>)field(source,"outputFluidStacks");
        }
    }
    private static final class Input {
        final Object source;final int slot,amount;final float multiplier;final List<Pattern> patterns;
        Input(Object source){this.source=source;slot=(Integer)call(source,"getSlotNumber");ItemStack item=(ItemStack)call(source,"getInput");if(item==null)throw fault("Missing Vat item input");amount=item.stackSize;multiplier=(Float)call(source,"getMulitplier");patterns=EnderInputs.patterns(source);}
    }
    private static final class Pair {
        final Entry entry;final Input left,right;final FluidStack in,out;
        Pair(Entry entry,Input left,Input right,FluidStack in,FluidStack out){this.entry=entry;this.left=left;this.right=right;this.in=in;this.out=out;}
    }
    /** Preserve native background/tank coordinates while drawing authoritative table amounts. */
    void draw(int index) {
        Pair p=pairs.get(index);handler.drawBackground(0);
        Class<?> render=type("com.enderio.core.client.render.RenderUtil");
        Class<?>[] signature={FluidStack.class,int.class,int.class,double.class,double.class,double.class,double.class,double.class};
        invoke(render,null,"renderGuiTank",signature,p.in.copy(),8000,p.in.amount,25d,1d,0d,15d,47d);
        invoke(render,null,"renderGuiTank",signature,p.out.copy(),8000,p.out.amount,127d,1d,0d,15d,47d);
        Class<?> power=type("crazypants.enderio.power.PowerDisplayUtil");
        String energy=invoke(power,null,"formatPower",new Class<?>[]{int.class},p.entry.energy)+" "+invoke(power,null,"abrevation",new Class<?>[0]);
        codechicken.lib.gui.GuiDraw.drawStringC(energy,86,54,0x808080,false);
        codechicken.lib.gui.GuiDraw.drawStringC("x"+p.left.multiplier,59,20,0x808080,false);
        if(p.right!=null)codechicken.lib.gui.GuiDraw.drawStringC("x"+p.right.multiplier,108,20,0x808080,false);
    }
    private static Object call(Object target,String name){return invoke(target.getClass(),target,name,new Class<?>[0]);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
