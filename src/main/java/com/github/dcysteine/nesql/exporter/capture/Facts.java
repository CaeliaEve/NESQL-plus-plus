package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/** Game-thread facts. No database, file I/O, hidden NBT normalization or renderer queue. */
final class Facts {
    private final String locale;
    private final Set<String> items = new HashSet<>();
    private final Set<String> fluids = new HashSet<>();
    private final Set<String> strings = new HashSet<>();
    private final Set<String> programs = new HashSet<>();
    private Batch batch = new Batch();
    private Transaction transaction;

    Facts(String locale) {
        this.locale = locale;
    }

    String locale() { return locale; }

    /** Only used before native capture starts, while replaying verified immutable records. */
    void restore(String kind, JsonObject record) {
        if (transaction != null) throw new IllegalStateException("Restore during native capture");
        String id = record.get("id").getAsString();
        switch (kind) {
            case "items":
                items.add(id);
                break;
            case "fluids":
                fluids.add(id);
                break;
            case "strings":
                strings.add(id);
                break;
            case "programs":
                programs.add(record.get("program").getAsString());
                break;
            default:
                break;
        }
    }

    String text(String value) {
        if (value == null) throw new IllegalArgumentException("A game text value was null");
        JsonObject record = object("locale", locale, "text", value);
        String id = Identity.content("text", record);
        if (remember(strings, id)) {
            record.addProperty("id", id);
            row("strings", record);
        }
        return id;
    }

    String item(ItemStack stack) {
        return item(stack, null);
    }

    /** A display-only stack may have no safe name or tooltip; only reuse an existing fact. */
    String known(ItemStack stack) {
        Jobs.checkpoint();
        if (stack == null || stack.getItem() == null)
            throw new Jobs.Fault("research_icon", "Empty display item");
        String registry = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) {
            throw new Jobs.Fault(
                    "unregistered_item",
                    "Display uses an unregistered item: " + stack.getItem().getClass().getName());
        }
        String id =
                Identity.item(
                        registry,
                        Items.feather.getDamage(stack),
                        TypedNbt.encode(stack.getTagCompound()));
        return items.contains(id) ? id : null;
    }

    String squeezerProgram(JsonObject rules) {
        String id = Identity.content("program", object("kind", "forestrySqueezer", "rules", rules));
        if (!programs.contains(id)) {
            // Chunk validation can fail after earlier sections. Publish only a
            // complete context, and never memoize an unsuccessful attempt.
            List<JsonObject> records = new ArrayList<>();
            com.github.dcysteine.nesql.exporter.source.Programs.squeezer(rules, records::add);
            for (JsonObject record : records) row("programs", record);
            remember(programs, id);
        }
        return id;
    }

    String beePoolProgram(JsonObject rules) {
        String id = Identity.content("program", object("kind", "magicBeesPool", "rules", rules));
        if (!programs.contains(id)) {
            List<JsonObject> records = new ArrayList<>();
            com.github.dcysteine.nesql.exporter.source.Programs.beePool(rules, records::add);
            for (JsonObject record : records) row("programs", record);
            remember(programs, id);
        }
        return id;
    }

    String item(ItemStack original, Integer order) {
        Jobs.checkpoint();
        if (original == null || original.getItem() == null)
            throw new IllegalArgumentException("Empty item fact");
        ItemStack stack = original.copy();
        stack.stackSize = 1;
        String registry = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) {
            throw new Jobs.Fault(
                    "unregistered_item",
                    "The source uses an unregistered item: "
                            + stack.getItem().getClass().getName());
        }
        int meta = Items.feather.getDamage(stack);
        com.google.gson.JsonElement nbt = TypedNbt.encode(stack.getTagCompound());
        String id = Identity.item(registry, meta, nbt);
        if (items.contains(id)) return id;
        try {
            JsonArray tooltip = new JsonArray();
            for (Object line :
                    tooltipStack(stack.copy()).getTooltip(Minecraft.getMinecraft().thePlayer, true))
                tooltip.add(value(text((String) line)));
            JsonObject tools = new JsonObject();
            ItemStack toolQuery=stack.copy();
            for (String tool : toolQuery.getItem().getToolClasses(toolQuery))
                tools.addProperty(tool, toolQuery.getItem().getHarvestLevel(toolQuery, tool));
            TreeSet<String> tags = new TreeSet<>();
            for (int ore : OreDictionary.getOreIDs(stack.copy())) tags.add(OreDictionary.getOreName(ore));
            JsonArray memberships = new JsonArray();
            for (String tag : tags) memberships.add(value(tag));
            JsonElement aspects = Aspects.item(stack.copy());
            JsonObject record =
                    object(
                            "id",
                            id,
                            "registry",
                            registry,
                            "meta",
                            meta,
                            "nbt",
                            nbt,
                            "name",
                            text(stack.copy().getDisplayName()),
                            "tooltip",
                            tooltip,
                            "stackLimit",
                            stack.copy().getMaxStackSize(),
                            "durability",
                            stack.copy().getMaxDamage(),
                            "tools",
                            tools,
                            "armor",
                            stack.getItem() instanceof net.minecraft.item.ItemArmor,
                            "tags",
                            memberships,
                            "icon",
                            null,
                            "order",
                            order,
                            "aspects",
                            aspects);
            batch.icons.add(new Icon("items", record, stack, null, registry));
            remember(items, id);
        } catch (java.util.concurrent.CancellationException error) {
            throw error;
        } catch (RuntimeException error) {
            Jobs.Fault fault =
                    new Jobs.Fault(
                            error instanceof Jobs.Fault
                                    ? ((Jobs.Fault) error).code
                                    : "item_capture",
                            "Item "
                                    + registry
                                    + "; meta="
                                    + meta
                                    + "; id="
                                    + id
                                    + (order == null ? "" : "; NEI index=" + order)
                                    + ": "
                                    + error
                                    + (error.getStackTrace().length == 0
                                            ? ""
                                            : "; at " + error.getStackTrace()[0]));
            fault.initCause(error);
            throw fault;
        }
        return id;
    }

    /**
     * EnderIO's creative entry initializes coordinates; some crafting templates do not. Initialize
     * only a tooltip copy with its native default. Identity, aspects and icon retain the original
     * null NBT; configured or empty-but-present tags are never replaced.
     */
    static ItemStack tooltipStack(ItemStack stack) {
        if (stack.getTagCompound() != null
                || !stack.getItem()
                        .getClass()
                        .getName()
                        .equals("crazypants.enderio.teleport.telepad.ItemCoordSelector"))
            return stack;
        MagicApi.version("Facts","EnderIO");
        ItemStack display = stack.copy();
        MagicApi.invoke(
                stack.getItem().getClass(),
                null,
                "init",
                new Class<?>[] {ItemStack.class},
                display);
        return display;
    }

    String fluid(FluidStack original) {
        Jobs.checkpoint();
        if (original == null || original.getFluid() == null)
            throw new IllegalArgumentException("Empty fluid fact");
        FluidStack stack = original.copy();
        stack.amount = 1;
        String registry = stack.getFluid().getName();
        if (FluidRegistry.getFluid(registry) != stack.getFluid())
            throw new Jobs.Fault("unregistered_fluid", "Fluid key is not registered: " + registry);
        com.google.gson.JsonElement nbt = TypedNbt.encode(stack.tag);
        String id = Identity.fluid(registry, nbt);
        if (fluids.contains(id)) return id;
        JsonObject record =
                object(
                        "id",
                        id,
                        "registry",
                        registry,
                        "nbt",
                        nbt,
                        "name",
                        text(stack.getLocalizedName()),
                        "temperature",
                        stack.getFluid().getTemperature(stack),
                        "density",
                        stack.getFluid().getDensity(stack),
                        "viscosity",
                        stack.getFluid().getViscosity(stack),
                        "luminosity",
                        stack.getFluid().getLuminosity(stack),
                        "gaseous",
                        stack.getFluid().isGaseous(stack),
                        "icon",
                        null);
        batch.icons.add(new Icon("fluids", record, null, stack, registry));
        remember(fluids, id);
        return id;
    }

    void row(String kind, JsonObject value) {
        batch.records.add(new Record(kind, value));
    }

    void picture(Picture picture) {
        batch.pictures.add(picture);
    }

    void model(Models.Draft model) {
        batch.models.add(model);
    }

    Batch drain() {
        Batch result = batch;
        batch = new Batch();
        if (transaction != null) transaction.frames.add(new Frame(batch));
        return result;
    }

    private boolean remember(Set<String> memo, String id) {
        if (!memo.add(id)) return false;
        if (transaction != null) transaction.additions.add(new Memo(memo, id));
        return true;
    }

    Transaction begin() {
        if (transaction != null) throw new IllegalStateException("Nested facts transaction");
        transaction = new Transaction();
        return transaction;
    }

    /** Journal only new identities and pending output; never clone the entire export memo. */
    final class Transaction implements AutoCloseable {
        private final List<Memo> additions = new ArrayList<>();
        private final List<Frame> frames = new ArrayList<>();
        private boolean done;

        private Transaction() {
            frames.add(new Frame(batch));
        }

        int records(String kind) {
            int count = 0;
            for (Frame frame : frames)
                for (int index = frame.records; index < frame.batch.records.size(); index++)
                    if (frame.batch.records.get(index).kind.equals(kind)) count++;
            return count;
        }

        void commit() {
            if (done || transaction != this)
                throw new IllegalStateException("Facts transaction already released");
            done = true;
            transaction = null;
        }

        @Override
        public void close() {
            if (done) return;
            if (transaction != this)
                throw new IllegalStateException("Facts transaction ownership changed");
            for (Memo added : additions) added.memo.remove(added.id);
            for (Frame frame : frames) frame.rollback();
            batch = frames.get(0).batch;
            done = true;
            transaction = null;
        }
    }

    private static final class Memo {
        final Set<String> memo;
        final String id;

        Memo(Set<String> memo, String id) {
            this.memo = memo;
            this.id = id;
        }
    }

    private static final class Frame {
        final Batch batch;
        final int records, icons, pictures, models;

        Frame(Batch batch) {
            this.batch = batch;
            records = batch.records.size();
            icons = batch.icons.size();
            pictures = batch.pictures.size();
            models = batch.models.size();
        }

        void rollback() {
            truncate(batch.records, records);
            truncate(batch.icons, icons);
            truncate(batch.pictures, pictures);
            truncate(batch.models, models);
        }

        private static void truncate(List<?> list, int size) {
            if (list.size() > size) list.subList(size, list.size()).clear();
        }
    }

    static final class Batch {
        final JsonSnapshot json = new JsonSnapshot();
        final List<Record> records = new ArrayList<>();
        final List<Icon> icons = new ArrayList<>();
        final List<Picture> pictures = new ArrayList<>();
        final List<Models.Draft> models = new ArrayList<>();
        long freezeRecords() {
            long bytes = 0;
            for (int index = 0; index < records.size(); index++) {
                Record record = records.get(index);
                String json = record.value.toString(); bytes += 128L + json.length() * 3L;
                records.set(index, new Record(record.kind, new com.google.gson.JsonParser().parse(json).getAsJsonObject()));
            }
            return bytes;
        }
        boolean hasVisuals() { return !icons.isEmpty() || !pictures.isEmpty() || !models.isEmpty(); }
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

}
