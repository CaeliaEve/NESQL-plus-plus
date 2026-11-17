package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native NASA registry predicates intersected with the actual workbench's slot restrictions. */
final class SpaceRecipes implements RegistryRecipes {
    private static final String GC = "micdoodle8.mods.galacticraft.";
    private static final String GS = "galaxyspace.core.";
    private static final String AR = "de.katzenpapst.amunra.";
    private static final String RECIPE = GC + "core.recipe.NasaWorkbenchRecipe";
    private final List<Object> recipes;
    private final NativeDefinitions definitions;
    private Layout layout;
    private boolean closed;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        if (name.equals(GS + "nei.RocketRecipeHandler")) {
            int tier = (Integer) field(handler, "tier");
            return tier >= 1 && tier <= 8 && handler.getHandlerId().equals(GS + "nei.rocket.RocketT" + tier + "RecipeHandler");
        }
        return name.equals(GC + "core.nei.BuggyRecipeHandler") || name.equals(GC + "planets.mars.nei.CargoRocketRecipeHandler")
                || name.equals(GC + "planets.asteroids.nei.AstroMinerRecipeHandler") || name.equals(AR + "nei.recipehandler.ARNasaWorkbenchShuttle");
    }

    SpaceRecipes(TemplateRecipeHandler handler) {
        this(registry(handler), () -> registry(handler), () -> layout(handler));
    }

    private static List<?> registry(TemplateRecipeHandler handler) {
        return registry(handler, true);
    }

    static List<?> registrationSource(TemplateRecipeHandler handler) { return registry(handler, false); }

    private static List<?> registry(TemplateRecipeHandler handler, boolean requireVersion) {
        if (requireVersion) MagicApi.version("SpaceRecipes","GalacticraftCore");
        if (!supports(handler)) throw fault("Unknown NASA handler");
        boolean galaxy = handler.getClass().getName().equals(GS + "nei.RocketRecipeHandler");
        boolean shuttle = handler.getClass().getName().equals(AR + "nei.recipehandler.ARNasaWorkbenchShuttle");
        int tier = galaxy ? (Integer) field(handler, "tier") : 0;
        if (galaxy && requireVersion) MagicApi.version("SpaceRecipes","GalaxySpace");
        String registry = galaxy ? GS + "recipe.RocketRecipes" : GC + "api.GalacticraftRegistry";
        String getter = galaxy ? "getRocketT" + tier + "Recipes" : handler.getClass().getName().contains("Buggy")
                ? "getBuggyBenchRecipes" : handler.getClass().getName().contains("Cargo") ? "getCargoRocketRecipes" : "getAstroMinerRecipes";
        List<?> registered;
        if (shuttle) {
            if (requireVersion) MagicApi.version("SpaceRecipes","GalacticraftAmunRa");
            registered = (List<?>) invoke(type(AR + "crafting.RecipeHelper"), null, "getAllRecipesFor", new Class<?>[] {Item.class},
                    field(type(AR + "item.ARItems"), null, "shuttleItem"));
        } else registered = (List<?>) invoke(type(registry), null, getter, new Class<?>[0]);
        return registered;
    }

    private static Layout layout(TemplateRecipeHandler handler) {
        boolean galaxy = handler.getClass().getName().equals(GS + "nei.RocketRecipeHandler");
        boolean shuttle = handler.getClass().getName().equals(AR + "nei.recipehandler.ARNasaWorkbenchShuttle");
        int tier = galaxy ? (Integer) field(handler, "tier") : 0;
        // Constructors query this registry with an empty matrix: reject unknown executable predicates first.
        String container = shuttle ? AR + "inventory.schematic.ContainerSchematicShuttle"
                : galaxy ? GS + "inventory.container.rocket.ContainerSchematicTier" + tier + "Rocket"
                : handler.getClass().getName().contains("Buggy") ? GC + "core.inventory.ContainerBuggyBench"
                : handler.getClass().getName().contains("Cargo") ? GC + "planets.mars.inventory.ContainerSchematicCargoRocket"
                : GC + "planets.asteroids.inventory.ContainerSchematicAstroMiner";
        if (Minecraft.getMinecraft().thePlayer == null) throw fault("NASA layout requires a prepared client player");
        // Empty owned inventory; never install this container, click it, populate its matrix or call close/pickup.
        Container owned = (Container) TinkerRecipes.construct(type(container),
                new Class<?>[] {InventoryPlayer.class, int.class, int.class, int.class},
                new InventoryPlayer(Minecraft.getMinecraft().thePlayer), 0, 0, 0);
        return new Layout(owned);
    }

    /** Owned layout plus native definitions for the same semantic reader, without client startup. */
    SpaceRecipes(List<?> registered, Layout layout) {
        this(registered, () -> registered, () -> layout);
    }

    private SpaceRecipes(List<?> registered, java.util.function.Supplier<List<?>> current,
                         java.util.function.Supplier<Layout> layoutFactory) {
        if (registered == null) throw new Jobs.Fault("handler_not_ready", "NASA native registry is unavailable");
        if (registered.size() > 262144) throw fault("NASA registry exceeds its budget");
        this.recipes = new ArrayList<>(registered);
        // Audit before constructing a native container, which queries the registry with an empty matrix.
        for (Object recipe : recipes) { Jobs.checkpoint(); audit(recipe); }
        this.layout = layoutFactory.get();
        if (this.layout == null) throw new Jobs.Fault("handler_not_ready", "NASA inventory predicates are unavailable");
        this.definitions = new NativeDefinitions("NASA", registered, current, recipe -> definition(recipe, this.layout));
    }

    public int size() { verify(); return recipes.size(); }
    public void verify() { if(closed)throw new Jobs.Fault("capture_closed","Native NASA reader is closed");definitions.verify(); }
    public void finishVerification() { verify(); definitions.finishVerification(); }
    public void close() { if(!closed){ recipes.clear(); definitions.close(); layout=null; closed=true; } }
    public boolean capture(int index, RecipeRow row) {
        verify();
        return definitions.checked(index, () -> {
            Object recipe = recipes.get(index);
            if (shadowed(recipes.subList(0, index), recipe, layout)) return row.exclude(object("native", RECIPE,
                    "reason", "earlier_native_recipe_claims_all_admissible_inputs", "nativeIndex", index, "earlierEntries", index));
            return capture(recipe, layout, row);
        });
    }

    static boolean shadowed(List<?> previous, Object recipe, Layout layout) {
        IInventory inventory = supply(recipe, layout.size);
        IInventory broadest = supply(recipe, layout.size);
        for (Map.Entry<Integer, Slot> entry : layout.slots.entrySet()) {
            ItemStack input = broadest.getStackInSlot(entry.getKey());
            if (input == null) continue;
            ItemStack wildcard = input.copy(); wildcard.setItemDamage(32767);
            if (entry.getValue().isItemValid(wildcard.copy())) broadest.setInventorySlotContents(entry.getKey(), wildcard);
        }
        for (Object earlier : previous) {
            Jobs.checkpoint();
            audit(earlier);
            if ((Boolean) invoke(type(RECIPE), earlier, "matches", new Class<?>[] {IInventory.class}, inventory))
                return true; // The exact input and all its allowed wildcard expansions match an earlier recipe.
            if ((Boolean) invoke(type(RECIPE), earlier, "matches", new Class<?>[] {IInventory.class}, broadest))
                throw fault("NASA wildcard choices depend on earlier recipe precedence");
        }
        return false;
    }

    static boolean capture(Object recipe, Layout layout, RecipeRow row) {
        audit(recipe);
        Map<Integer, ItemStack> inputs = inputs(recipe);
        int required = 0;
        List<Integer> empty = new ArrayList<>();
        for (Integer index : inputs.keySet()) if (!layout.slots.containsKey(index)) throw fault("NASA recipe references inaccessible slot " + index);
        for (Map.Entry<Integer, Slot> entry : layout.slots.entrySet()) {
            Jobs.checkpoint();
            int index = entry.getKey(); Slot slot = entry.getValue();
            ItemStack source = inputs.get(index);
            if (source == null) {
                // Omitted keys are allowed only for the audited disabled physical slots.
                if (!inputs.containsKey(index) && !disabled(slot)) throw fault("NASA recipe leaves a usable slot unconstrained: " + index);
                empty.add(index); continue;
            }
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (int meta : source.getItemDamage() == 32767 ? new int[] {32767} : new int[] {source.getItemDamage(), 32767}) {
                ItemStack offered = source.copy(); offered.stackSize = 1; offered.setTagCompound(null); offered.setItemDamage(meta);
                if (slot.isItemValid(offered.copy())) choices.add(new RecipeRow.Ingredient(offered, 1, false,
                        object("kind", "wildcard", "meta", false, "nbt", true)));
            }
            if (choices.isEmpty()) return row.exclude(object("native", slot.getClass().getName(),
                    "reason", "native_inventory_slot_rejects_registered_ingredient", "slot", index, "input", RecipeRow.stackEvidence(source)));
            row.itemInput(index, choices, false);
            JsonObject semantic = row.inputs.get(row.inputs.size() - 1).getAsJsonObject();
            for (int alternative = 0; alternative < choices.size(); alternative++)
                containerReturn(choices.get(alternative).item, semantic.getAsJsonArray("choices").get(alternative).getAsJsonObject(), row.facts);
            required++;
        }
        if (required == 0) throw fault("NASA recipe has no required ingredients");
        ItemStack output = (ItemStack) invoke(type(RECIPE), recipe, "getRecipeOutput", new Class<?>[0]);
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("Invalid NASA output");
        row.itemOutput(0, output, 10000);
        if (!empty.isEmpty()) row.property("galacticraft:emptySlots", "Required empty workbench slots", empty);
        row.property("galacticraft:layout", "Input slot numbering", "Native NASA workbench inventory indices");
        return true;
    }

    private static void containerReturn(ItemStack input, JsonObject choice, Facts facts) {
        Item item = input.getItem();
        if (!ItemCallbacks.hasContainer(input)) return;
        // SlotRocketBenchResult uses the legacy item getter, not getContainerItem(ItemStack).
        if (ItemCallbacks.method(item, "getContainerItem", "func_77668_q").getDeclaringClass() != Item.class)
            throw fault("Unadapted NASA legacy container return: " + item.getClass().getName());
        Item returned = item.getContainerItem();
        if (returned == null) throw fault("NASA native container item is null");
        choice.getAsJsonArray("returns").add(object("kind", "item", "id", facts.item(new ItemStack(returned)), "amount", "1"));
    }
    private static boolean disabled(Slot slot) {
        String name = slot.getClass().getName();
        return name.equals(GS + "inventory.slot.SlotSchematic") && field(slot, "item") == null
                || name.equals(GC + "planets.mars.inventory.SlotSchematicCargoRocket") && slot.getSlotIndex() == 6;
    }
    private static void audit(Object recipe) {
        if (recipe == null || !recipe.getClass().getName().equals(RECIPE)) throw fault("Unadapted NASA recipe: " + (recipe == null ? "null" : recipe.getClass().getName()));
    }

    private static JsonObject definition(Object recipe, Layout layout) {
        audit(recipe);
        Map<Integer, ItemStack> inputs = inputs(recipe);
        JsonObject raw = object();
        for (Map.Entry<Integer, ItemStack> entry : inputs.entrySet())
            raw.add(Integer.toString(entry.getKey()), value(RecipeRow.stackEvidence(entry.getValue())));
        JsonArray predicates = array();
        for (Map.Entry<Integer, Slot> entry : layout.slots.entrySet()) {
            Jobs.checkpoint();
            ItemStack input = inputs.get(entry.getKey());
            JsonArray admission = array();
            if (input != null) {
                for (int meta : input.getItemDamage() == 32767 ? new int[] {32767} : new int[] {input.getItemDamage(), 32767}) {
                    ItemStack offered = input.copy(); offered.stackSize = 1; offered.setTagCompound(null); offered.setItemDamage(meta);
                    admission.add(value(entry.getValue().isItemValid(offered)));
                }
            }
            predicates.add(object("index", entry.getKey(), "class", entry.getValue().getClass().getName(),
                    "disabled", disabled(entry.getValue()), "admission", admission));
        }
        ItemStack output = (ItemStack) invoke(type(RECIPE), recipe, "getRecipeOutput", new Class<?>[0]);
        return object("input", raw, "output", RecipeRow.stackEvidence(output), "size", layout.size, "predicates", predicates);
    }
    @SuppressWarnings("unchecked")
    private static Map<Integer, ItemStack> inputs(Object recipe) {
        Map<?, ?> original = (Map<?, ?>) invoke(type(RECIPE), recipe, "getRecipeInput", new Class<?>[0]);
        if (original.size() > 128) throw fault("NASA recipe exceeds its slot budget");
        Map<Integer, ItemStack> result = new TreeMap<>();
        for (Map.Entry<?, ?> entry : original.entrySet()) {
            if (!(entry.getKey() instanceof Integer) || (Integer) entry.getKey() <= 0 || (Integer) entry.getKey() > 128
                    || entry.getValue() != null && (!(entry.getValue() instanceof ItemStack) || ((ItemStack) entry.getValue()).getItem() == null))
                throw fault("Invalid NASA registry input");
            result.put((Integer) entry.getKey(), entry.getValue() == null ? null : ((ItemStack) entry.getValue()).copy());
        }
        return result;
    }
    private static IInventory supply(Object recipe, int size) {
        InventoryBasic inventory = new InventoryBasic("nesql", true, size);
        for (Map.Entry<Integer, ItemStack> entry : inputs(recipe).entrySet()) {
            if (entry.getKey() >= size) throw fault("NASA recipe index outside its inventory");
            ItemStack value = entry.getValue();
            if (value != null) { value.stackSize = 1; inventory.setInventorySlotContents(entry.getKey(), value); }
        }
        return inventory;
    }
    static final class Layout {
        final Map<Integer, Slot> slots = new TreeMap<>();
        final Slot output;
        final int size;
        Layout(Container container) {
            IInventory matrix = (IInventory) field(container, "craftMatrix");
            size = matrix.getSizeInventory();
            if (size <= 1 || size > 129) throw fault("Invalid NASA inventory size");
            Slot result = null;
            for (Object raw : container.inventorySlots) {
                Slot slot = (Slot) raw;
                String name = slot.getClass().getName();
                if (name.equals(GC + "core.inventory.SlotRocketBenchResult")) {
                    if (result != null) throw fault("Multiple NASA output slots");
                    result = slot;
                } else if (slot.inventory == matrix) {
                    if (!Arrays.asList(GS + "inventory.slot.SlotSchematic", GS + "inventory.slot.SlotSchematicChest",
                            GC + "core.inventory.SlotBuggyBench", GC + "planets.mars.inventory.SlotSchematicCargoRocket",
                            GC + "planets.asteroids.inventory.SlotSchematicAstroMiner", AR + "inventory.schematic.SlotSchematicShuttle").contains(name))
                        throw fault("Unadapted NASA slot: " + name);
                    if (name.equals(AR + "inventory.schematic.SlotSchematicShuttle")) {
                        for (Object item : (Object[]) field(slot, "validItem"))
                            if (item == null || !item.getClass().getName().equals(AR + "item.ItemDamagePair"))
                                throw fault("Unadapted shuttle slot item predicate");
                    }
                    if (slots.put(slot.getSlotIndex(), slot) != null) throw fault("Duplicate NASA input slot");
                } else if (!(slot.inventory instanceof InventoryPlayer) || slot.getClass() != Slot.class)
                    throw fault("Unexpected NASA inventory binding");
            }
            if (result == null || slots.size() != size - 1) throw fault("Incomplete NASA workbench layout");
            output = result;
        }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
