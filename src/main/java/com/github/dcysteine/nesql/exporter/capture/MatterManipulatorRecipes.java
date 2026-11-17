package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Native dynamic upgrade law, independent of the GUI's blank-wand examples. */
final class MatterManipulatorRecipes implements RegistryRecipes {
    static final String HANDLER="com.recursive_pineapple.matter_manipulator.client.nei.UpdateRecipeNEIHandler";
    private MatterManipulatorSource source;
    private boolean closed;
    static boolean supports(ICraftingHandler value){return value!=null&&supportsClass(value.getClass().getName());}
    static boolean supportsClass(String name){return HANDLER.equals(name);}
    MatterManipulatorRecipes(TemplateRecipeHandler handler){
        if(!supports(handler))throw fault("Unknown Matter Manipulator handler");
        try{source=new MatterManipulatorSource();}catch(RuntimeException|Error error){close();throw error;}
    }
    public int size(){alive();return source.branches.size();}
    public void verify(){alive();source.verify();}
    public void finishVerification(){verify();source.finishVerification();}
    public JsonObject handlerExclusion(){alive();return source.handlerExclusion();}
    public boolean capture(int index,RecipeRow row){
        verify();MatterManipulatorSource.Branch branch=source.branches.get(index);
        int bit=(Integer)field(branch.upgrade,"bit");
        List<ItemStack> components=source.components(branch.upgrade);
        if(components.isEmpty())return row.exclude(object("native",MatterManipulatorSource.RECIPE,
            "reason","native_upgrade_has_no_registered_metadata_input","tier",((Enum<?>)branch.tier).ordinal(),
            "upgrade",((Enum<?>)branch.upgrade).name(),"source","complete native metadata/default mapping and MetaItem class census"));
        List<MatterManipulatorSource.State> states=new ArrayList<>();List<ItemStack> centers=new ArrayList<>();
        Map<String,MatterManipulatorSource.State> normalized=new HashMap<>();
        for(MatterManipulatorSource.State state:source.states(branch.item))if(!MatterManipulatorCodec.bits(state.normalized).get(bit)){
            states.add(state);centers.add(state.input.copy());
            normalized.put(com.github.dcysteine.nesql.exporter.source.Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(state.input.getItem()),
                net.minecraft.init.Items.feather.getDamage(state.input),com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(state.input.getTagCompound())),state);
        }
        if(states.isEmpty())throw fault("Matter upgrade has no safe uninstalled specimen; not an unreachable-recipe proof");
        List<RecipeRow.Ingredient> centerChoices=new ArrayList<>(),componentChoices=new ArrayList<>();
        for(ItemStack center:centers)centerChoices.add(new RecipeRow.Ingredient(center,1,false,object("kind","matterManipulatorInput")));
        for(ItemStack component:components)componentChoices.add(new RecipeRow.Ingredient(component,1,false,object("kind","matterManipulatorUpgrade")));
        row.nativeCraftingInput(0,centerChoices);row.nativeCraftingInput(1,componentChoices);
        Products product=Products.observe(centers,object("kind","matterManipulator"),input->{
            String id=com.github.dcysteine.nesql.exporter.source.Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(input.getItem()),
                net.minecraft.init.Items.feather.getDamage(input),com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(input.getTagCompound()));
            MatterManipulatorSource.State state=normalized.get(id);
            if(state==null)throw fault("Matter result has no bound native normalization observation");
            ItemStack output=input.copy();output.setTagCompound(MatterManipulatorCodec.install(state.normalized,bit));return output;
        },row.facts);
        row.record.add("process",source.describe(branch,states,row.facts));
        row.itemOutput(0,product.output,10000);product.attach(row);
        row.note("matterManipulator:state",MatterManipulatorSource.RECIPE,row.facts.locale().equals("zh_CN")
            ?"所示为原生物品列表、注册类型和升级位组合的状态样本；组合样本不证明可获得性。前九格并非独立限制，原生配方检查整个合成库存，只允许两个占用格。按 MetaItem 类型及字面元数据查表，忽略材料 NBT 与堆叠数量，工作台每格取用一件；产物复制实际操纵器数量。0.0.51 的原生序列化会重写整个 NBT 根，删除未知字段，并在安装新位之前按旧位集累加能力。未观测状态需要原生完整编解码成功，不能据样本推算固定产物。"
            :"These are state specimens from native item lists, registered types and declared upgrade-bit combinations; generated combinations do not prove obtainability. The native recipe checks the entire crafting inventory, not an independent first-nine limit, and permits only two occupied cells. It looks up literal metadata of a MetaItem, ignoring component NBT and stack sizes. The workbench takes one per occupied cell; the output copies the offered manipulator count. In 0.0.51 serialization replaces the entire NBT root, drops unknown fields and accumulates capabilities from the OLD bits before installing the new bit. Unobserved states still require successful native decoding/encoding; a fixed result cannot be inferred from these samples.");
        return true;
    }
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Matter Manipulator reader is closed");}
    public void close(){closed=true;if(source!=null)source.close();source=null;}
    private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
