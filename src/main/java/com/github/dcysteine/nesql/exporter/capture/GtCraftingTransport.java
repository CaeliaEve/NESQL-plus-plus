package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.ItemStack;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Typed finite candidate observations plus the pinned native rule declaration.
 * These are not a result function for arbitrary user-supplied inventory/NBT.
 * NativeCraftingRecipes owns publication and retains compact sealing bindings.
 */
final class GtCraftingTransport {
    private GtCraftingTransport() {}

    static List<ItemStack> attach(GtCraftingCapturePlan plan,RecipeRow row) {
        plan.verify();
        if(plan.cases.isEmpty())throw new Jobs.Fault("recipe_unrepresented","GT exported candidates have no matching native result; this is not an unreachable-recipe proof");
        if(row.inputs.size()!=plan.groups.size()||row.outputs.size()!=0)
            throw new Jobs.Fault("slot_changed","GT candidate transport does not own the expected input/result slots");
        List<ItemStack> records=new ArrayList<>();
        ItemStack prototype=plan.prototype(),base=plan.normalizedBase();records.add(prototype);records.add(base);
        JsonArray transfers=array(),states=array(),samples=array();
        for(int slot=0;slot<plan.groups.size();slot++) {
            JsonArray projected=array(),choices=row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices");
            List<GtCraftingStates.Candidate> candidates=plan.groups.get(slot);
            if(choices.size()!=candidates.size())throw new Jobs.Fault("slot_changed","GT candidate transport lost input deduplication/order");
            for(int choice=0;choice<candidates.size();choice++) {
                GtCraftingStates.Candidate candidate=candidates.get(choice);records.add(candidate.stack.copy());
                if(!row.facts.item(candidate.stack).equals(choices.get(choice).getAsJsonObject().get("id").getAsString()))
                    throw new Jobs.Fault("slot_changed","GT candidate transport changed its concrete input identity");
                projected.add(value(candidate.transfer));
            }
            transfers.add(projected);
        }
        Map<String,String> outputs=new LinkedHashMap<>();
        for(GtCraftingCapturePlan.Case sample:plan.cases) {
            Jobs.checkpoint();
            String key=sample.state.transfer+":"+tag(sample.state.inherited)+":"+tag(sample.state.overwritten);
            ItemStack result=sample.result.result();String signature=CanonicalJson.digest(RecipeRow.stackEvidence(result));
            String prior=outputs.putIfAbsent(key,signature);
            if(prior!=null) {
                if(!prior.equals(signature))throw new Jobs.Fault("slot_changed","GT folded state has inconsistent native outputs");
                continue;
            }
            JsonArray witness=array(),positions=array();
            for(int choice:sample.state.witness)witness.add(value(choice));
            for(int cell:sample.positions)positions.add(value(cell));
            states.add(object("choices",witness,"positions",positions));
            samples.add(stack(row,result));records.add(result);
        }
        row.itemOutput(0,plan.cases.get(0).result.result(),10000);
        row.outputs.get(0).getAsJsonObject().add("change",object("input",0,"action",object("kind","gtCrafting"),"samples",samples));
        row.record.add("process",object("kind","gtCrafting","version","5.09.51.482","scope","exportedCandidates",
                "rules",plan.rules(),"prototype",stack(row,prototype),"base",stack(row,base),"transfers",transfers,"states",states));
        plan.verify();return records;
    }
    private static String tag(GtCraftingStates.Candidate candidate){return candidate==null?null:candidate.tag;}
    private static JsonObject stack(RecipeRow row,ItemStack stack) {
        return object("id",row.facts.item(stack),"amount",Integer.toString(stack.stackSize));
    }
}
