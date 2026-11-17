package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.FurnaceRecipes;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native vanilla smelting definitions and precedence. No NEI loader, cache or GUI positions. */
final class VanillaFurnaceRecipes implements RegistryRecipes {
    private final FurnaceRecipes manager;
    private final Map<ItemStack, ItemStack> source;
    private final int sourceCount;
    private final List<Map.Entry<ItemStack, ItemStack>> entries;
    private final NativeDefinitions definitions;
    private final NativeSmeltingTable table;
    private final NativeDefinitionGraph graph;
    private final NativeItemExamples examples = new NativeItemExamples();
    private final List<Plan> plans = new ArrayList<>();
    private final Supplier<FurnaceRecipes> current;
    private boolean closed;

    VanillaFurnaceRecipes() { this(FurnaceRecipes.smelting(), FurnaceRecipes::smelting); }
    VanillaFurnaceRecipes(FurnaceRecipes manager) { this(manager, () -> manager); }

    @SuppressWarnings("unchecked")
    private VanillaFurnaceRecipes(FurnaceRecipes manager, Supplier<FurnaceRecipes> current) {
        if (manager == null || manager.getSmeltingList() == null)
            throw new Jobs.Fault("handler_not_ready", "Vanilla furnace registry is unavailable");
        if (manager.getClass() != FurnaceRecipes.class)
            throw new Jobs.Fault("recipe_unsupported", "Unknown native furnace implementation");
        this.manager = manager;
        this.current = current;
        source = (Map<ItemStack, ItemStack>) manager.getSmeltingList();
        sourceCount = source.size();
        table=new NativeSmeltingTable(source);
        if (sourceCount < 0 || sourceCount > 262144) throw new Jobs.Fault("recipe_limit", "Vanilla furnace exceeds its definition budget");
        entries = new ArrayList<>();
        for(Map.Entry<ItemStack,Object> entry:table.entries()){
            if(!(entry.getValue() instanceof ItemStack))throw new Jobs.Fault("recipe_unsupported","Invalid native furnace output");
            entries.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(),(ItemStack)entry.getValue()));
        }
        Map<String,String[]> shapes=NativeDefinitionGraph.shapes(FurnaceRecipes.class.getName(),"smeltingList","experienceList");
        NativeSmeltingTable.shapes(shapes);
        graph=new NativeDefinitionGraph(()->new Object[]{current.get(),MagicApi.field(manager,"smeltingList"),MagicApi.field(manager,"experienceList")},shapes);
        definitions = new NativeDefinitions("Vanilla furnace", entries, this::currentEntries, this::definition);
        Map<Item, Claims> claimed = new IdentityHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            Jobs.checkpoint();
            ItemStack input = entries.get(index).getKey();
            Claims state = claimed.computeIfAbsent(input.getItem(), ignored -> new Claims());
            int metadata = net.minecraft.init.Items.feather.getDamage(input);
            Integer previous = state.wildcard >= 0 ? state.wildcard : metadata == 32767 ? null : state.metadata.get(metadata);
            if (previous != null) plans.add(new Plan(previous, new int[0]));
            else if (metadata == 32767) {
                if (state.metadata.size() > 4096) throw new Jobs.Fault("recipe_limit", "Native furnace wildcard exceptions exceed their budget");
                plans.add(new Plan(-1, state.metadata.values().stream().mapToInt(Integer::intValue).toArray()));
                state.wildcard = index;
            } else {
                plans.add(new Plan(-1, new int[0]));
                state.metadata.put(metadata, index);
            }
        }
    }

    private List<?> currentEntries() {
        if (current.get() != manager || manager.getSmeltingList() != source || source.size() != sourceCount)
            throw new Jobs.Fault("recipe_changed", "Native furnace registry/count changed");
        return entries;
    }

    public int size() { verify(); return entries.size(); }
    public void verify() {
        if (closed) throw new Jobs.Fault("capture_closed", "Vanilla furnace reader is closed");
        definitions.verify();
    }
    public void finishVerification() {
        verify();
        definitions.finishVerification();
        examples.verify();
        try{graph.verify();}
        catch(Jobs.Fault failure){
            if(!failure.code.equals("environment_changed"))throw failure;
            Jobs.Fault changed=new Jobs.Fault("recipe_changed","Native furnace backing definitions changed");changed.initCause(failure);throw changed;
        }
    }
    public void close() { if (!closed) { closed = true; entries.clear(); plans.clear(); definitions.close(); graph.close(); examples.close(); } }

    public boolean capture(int index, RecipeRow row) {
        verify();
        return definitions.checked(index, () -> captureEntry(index, row));
    }

    private boolean captureEntry(int index, RecipeRow row) {
        Map.Entry<ItemStack, ItemStack> entry = entries.get(index);
        ItemStack input = entry.getKey(), output = entry.getValue();
        boolean wildcard = net.minecraft.init.Items.feather.getDamage(input) == 32767;
        Plan plan = plans.get(index);
        if (plan.shadow >= 0) {
            definitions.record(plan.shadow);
            return row.exclude(object("native", FurnaceRecipes.class.getName(), "reason", "earlier_native_smelting_definition_claims_all_inputs",
                    "nativeIndex", index, "earlierIndex", plan.shadow, "input", RecipeRow.stackEvidence(input),
                    "earlierInput", RecipeRow.stackEvidence(entries.get(plan.shadow).getKey())));
        }
        JsonArray excluded = array();
        for (int previous : plan.exceptions) {
            Jobs.checkpoint();
            definitions.record(previous);
            ItemStack earlier = entries.get(previous).getKey();
            excluded.add(object("id", row.facts.item(earlier), "rule", rule(false)));
        }
        JsonObject match = excluded.size() == 0 ? rule(wildcard) : object("kind", "except", "base", rule(true), "exclude", excluded);
        List<RecipeRow.Ingredient> choices = new ArrayList<>();
        // Native furnace matching ignores NBT. Visual witnesses must come from
        // the ready native catalogue, retaining Genetics' real gene tags.
        for (ItemStack offered : examples.permutations(input)) {
            offered.stackSize = 1;
            choices.add(new RecipeRow.Ingredient(offered, 1, false, match));
        }
        row.itemInput(0, choices, false);
        row.itemOutput(0, output.copy(), 10000);
        row.record.addProperty("duration", "200");
        row.property("minecraft:experience", "Experience per output item", experience(output));
        return true;
    }

    @SuppressWarnings("unchecked")
    private JsonObject definition(Object raw) {
        Map.Entry<ItemStack, ItemStack> entry = (Map.Entry<ItemStack, ItemStack>) raw;
        ItemStack input = entry.getKey(), output = entry.getValue();
        if (input == null || input.getItem() == null || output == null || output.getItem() == null || output.stackSize <= 0)
            throw new Jobs.Fault("recipe_unsupported", "Invalid native smelting definition");
        if (ItemCallbacks.method(input.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
            throw new Jobs.Fault("recipe_unsupported", "Native furnace input needs a dedicated metadata predicate: " + input.getItem().getClass().getName());
        if (table.definitionValue(input) != output)
            throw new Jobs.Fault("recipe_changed", "Native smelting definition was replaced");
        return object("input", RecipeRow.stackEvidence(input), "output", RecipeRow.stackEvidence(output),
                "experience", Float.toString(experience(output)));
    }

    private float experience(ItemStack output) {
        float value = SmeltingItemCallbacks.experience(output);
        if (value == -1f) value = manager.func_151398_b(output.copy());
        if (!Float.isFinite(value) || value < 0) throw new Jobs.Fault("recipe_unsupported", "Invalid native smelting experience");
        return value;
    }
    private static JsonObject rule(boolean wildcard) { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }
    private static final class Claims {
        int wildcard = -1;
        final Map<Integer, Integer> metadata = new LinkedHashMap<>();
    }
    private static final class Plan {
        final int shadow;
        final int[] exceptions;
        Plan(int shadow, int[] exceptions) { this.shadow = shadow; this.exceptions = exceptions; }
    }
}
