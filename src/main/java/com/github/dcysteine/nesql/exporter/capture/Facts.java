package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Game-thread facts. No database, file I/O, hidden NBT normalization or renderer queue. */
final class Facts {
    private final String locale;
    private final Set<String> items = new HashSet<>();
    private final Set<String> fluids = new HashSet<>();
    private final Set<String> strings = new HashSet<>();
    private final Set<String> tracks = new HashSet<>();
    private Batch batch = new Batch();

    Facts(String locale) { this.locale = locale; }

    String text(String value) {
        if (value == null) throw new IllegalArgumentException("A game text value was null");
        JsonObject record = object("locale", locale, "text", value);
        String id = Identity.content("text", record);
        if (strings.add(id)) { record.addProperty("id", id); row("strings", record); }
        return id;
    }

    String item(ItemStack stack) { return item(stack, null); }

    String track(JsonArray frames) {
        JsonObject record = object("frames", frames);
        String id = Identity.content("track", record);
        if (tracks.add(id)) { record.addProperty("id", id); row("tracks", record); }
        return id;
    }

    String item(ItemStack original, Integer order) {
        Jobs.checkpoint();
        if (original == null || original.getItem() == null) throw new IllegalArgumentException("Empty item fact");
        ItemStack stack = original.copy();
        stack.stackSize = 1;
        String registry = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) {
            throw new Jobs.Fault("unregistered_item", "The source uses an unregistered item: " + stack.getItem().getClass().getName());
        }
        int meta = Items.feather.getDamage(stack);
        com.google.gson.JsonElement nbt = TypedNbt.encode(stack.getTagCompound());
        String id = Identity.item(registry, meta, nbt);
        if (items.contains(id)) return id;
        try {
            JsonArray tooltip = new JsonArray();
            for (Object line : stack.getTooltip(Minecraft.getMinecraft().thePlayer, true)) tooltip.add(value(text((String) line)));
            JsonObject tools = new JsonObject();
            for (String tool : stack.getItem().getToolClasses(stack)) tools.addProperty(tool, stack.getItem().getHarvestLevel(stack, tool));
            TreeSet<String> tags = new TreeSet<>();
            for (int ore : OreDictionary.getOreIDs(stack)) tags.add(OreDictionary.getOreName(ore));
            JsonArray memberships = new JsonArray();
            for (String tag : tags) memberships.add(value(tag));
            JsonElement aspects = Aspects.item(stack);
            JsonObject record = object("id", id, "registry", registry, "meta", meta, "nbt", nbt,
                    "name", text(stack.getDisplayName()), "tooltip", tooltip,
                    "stackLimit", stack.getMaxStackSize(), "durability", stack.getMaxDamage(), "tools", tools,
                    "armor", stack.getItem() instanceof net.minecraft.item.ItemArmor,
                    "tags", memberships, "icon", null, "order", order, "aspects", aspects);
            batch.icons.add(new Icon("items", record, stack, null, registry));
            items.add(id);
        } catch (java.util.concurrent.CancellationException error) { throw error; }
        catch (RuntimeException error) {
            Jobs.Fault fault = new Jobs.Fault(error instanceof Jobs.Fault ? ((Jobs.Fault) error).code : "item_capture",
                    "Item " + registry + "; meta=" + meta + "; id=" + id
                            + (order == null ? "" : "; NEI index=" + order) + ": " + error
                            + (error.getStackTrace().length == 0 ? "" : "; at " + error.getStackTrace()[0]));
            fault.initCause(error);
            throw fault;
        }
        return id;
    }

    String fluid(FluidStack original) {
        Jobs.checkpoint();
        if (original == null || original.getFluid() == null) throw new IllegalArgumentException("Empty fluid fact");
        FluidStack stack = original.copy(); stack.amount = 1;
        String registry = stack.getFluid().getName();
        if (FluidRegistry.getFluid(registry) != stack.getFluid()) throw new Jobs.Fault("unregistered_fluid", "Fluid key is not registered: " + registry);
        com.google.gson.JsonElement nbt = TypedNbt.encode(stack.tag);
        String id = Identity.fluid(registry, nbt);
        if (!fluids.add(id)) return id;
        JsonObject record = object("id", id, "registry", registry, "nbt", nbt, "name", text(stack.getLocalizedName()),
                "temperature", stack.getFluid().getTemperature(stack), "density", stack.getFluid().getDensity(stack),
                "viscosity", stack.getFluid().getViscosity(stack), "luminosity", stack.getFluid().getLuminosity(stack),
                "gaseous", stack.getFluid().isGaseous(stack), "icon", null);
        batch.icons.add(new Icon("fluids", record, null, stack, registry));
        return id;
    }

    void row(String kind, JsonObject value) { batch.records.add(new Record(kind, value)); }
    void scene(Scene scene) { batch.scenes.add(scene); }
    void picture(Picture picture) { batch.pictures.add(picture); }
    void model(Models.Draft model) { batch.models.add(model); }
    Batch drain() { Batch result = batch; batch = new Batch(); return result; }

    static final class Batch {
        final List<Record> records = new ArrayList<>();
        final List<Icon> icons = new ArrayList<>();
        final List<Scene> scenes = new ArrayList<>();
        final List<Picture> pictures = new ArrayList<>();
        final List<Models.Draft> models = new ArrayList<>();
    }
    static final class Record {
        final String kind; final JsonObject value;
        Record(String kind, JsonObject value) { this.kind = kind; this.value = value; }
    }
    static final class Icon {
        final String kind; final JsonObject record; final ItemStack item; final FluidStack fluid; final String registry;
        Icon(String kind, JsonObject record, ItemStack item, FluidStack fluid, String registry) {
            this.kind = kind; this.record = record; this.item = item; this.fluid = fluid; this.registry = registry;
        }
    }

    static final class Picture {
        final JsonObject record;
        final String field, location;
        final int width, height, scale;
        final Runnable draw;
        Picture(JsonObject record, String field, String location, Runnable draw) {
            this(record, field, location, 16, 16, 4, draw);
        }
        Picture(JsonObject record, String field, String location, int width, int height, Runnable draw) {
            this(record, field, location, width, height, 2, draw);
        }
        private Picture(JsonObject record, String field, String location, int width, int height, int scale, Runnable draw) {
            this.record = record; this.field = field; this.location = location; this.width = width; this.height = height;
            this.scale = scale; this.draw = draw;
        }
    }

    static final class Scene {
        final JsonObject recipe;
        final JsonArray elements;
        final int width, height, z;
        final String location;
        final Runnable draw;
        Scene(JsonObject recipe, JsonArray elements, int width, int height, int z, String location, Runnable draw) {
            this.recipe = recipe; this.elements = elements; this.width = width; this.height = height;
            this.z = z; this.location = location; this.draw = draw;
        }
    }
}
