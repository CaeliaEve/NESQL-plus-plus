package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/** Audited finite Diagram content. No draw, dimension, font, description or GUI callbacks. */
final class NativeDiagramContents {
    static final String API = "com.github.dcysteine.neicustomdiagram.api.";
    private static final int MAX = 262144;
    final Map<String, ItemStack> items = new LinkedHashMap<>();
    final Map<String, FluidStack> fluids = new LinkedHashMap<>();
    private final Class<?> noOpInteraction;
    private final Class<?> stringInteraction;
    private int nodes;
    private long textBytes;

    NativeDiagramContents() {
        // Obtain the exact native lambda classes without executing either callback.
        Class<?> custom = type(API + "diagram.interactable.CustomInteractable");
        Object builder =
                invoke(
                        custom,
                        null,
                        "builder",
                        new Class<?>[] {type(API + "draw.BoundedDrawable")},
                        new Object[] {null});
        noOpInteraction = field(builder, "interact").getClass();
        stringInteraction =
                invoke(
                                custom,
                                null,
                                "buildInteractionLambda",
                                new Class<?>[] {String.class},
                                "nesql.inspect-only")
                        .getClass();
    }

    JsonObject read(Object diagram) {
        Jobs.checkpoint();
        nodes = 0;
        textBytes = 0;
        if (diagram == null || !diagram.getClass().getName().equals(API + "diagram.Diagram"))
            throw fault("Unknown state-dependent native diagram class");
        Object layout = field(diagram, "layout");
        Map<?, ?> slots = map(call(layout, "slots")), groups = map(call(layout, "slotGroups"));
        JsonArray cells = array(), logicalGroups = array(), labels = array(), annotations = array();
        IdentityHashMap<Object, JsonObject> byPosition = new IdentityHashMap<>();
        Map<Object, List<JsonObject>> equalPositions = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (Map.Entry<?, ?> entry : slots.entrySet()) {
            String key = key(entry.getKey());
            if (!keys.add(key)) throw fault("Ambiguous native layout key");
            logicalGroups.add(object("key", key, "columns", 1, "rows", 1));
            addCell(cells, byPosition, equalPositions, key, 0, entry.getValue());
        }
        for (Map.Entry<?, ?> entry : groups.entrySet()) {
            String key = key(entry.getKey());
            if (!keys.add(key)) throw fault("Ambiguous native layout key");
            Object group = entry.getValue();
            int width = (Integer) call(group, "width"), height = (Integer) call(group, "height");
            if (width <= 0 || height <= 0 || (long) width * height > MAX)
                throw fault("Invalid logical group shape");
            List<?> members = list(call(group, "slots"));
            if (members.size() != width * height) throw fault("Incomplete native logical group");
            logicalGroups.add(object("key", key, "columns", width, "rows", height));
            for (int i = 0; i < members.size(); i++)
                addCell(cells, byPosition, equalPositions, key, i, members.get(i));
        }
        for (Object label : list(call(layout, "labels"))) labels.add(label(label));
        for (Object interactable : list(field(diagram, "interactables"))) {
            count();
            String cls = interactable.getClass().getName();
            if (cls.equals(API + "diagram.interactable.InteractiveComponentGroup")) {
                Object position = field(interactable, "position");
                JsonObject cell = byPosition.get(position);
                if (cell == null) {
                    List<JsonObject> equal = equalPositions.get(position);
                    if (equal == null || equal.size() != 1)
                        throw fault("Unbound or ambiguous native component group");
                    cell = equal.get(0);
                }
                if (cell.getAsJsonArray("alternatives").size() != 0)
                    throw fault("Multiple insertions into native logical cell");
                List<?> choices = list(field(interactable, "components"));
                if (choices.isEmpty()) throw fault("Empty native component group");
                for (Object choice : choices)
                    cell.getAsJsonArray("alternatives").add(display(choice, 0));
                cell.add("tooltip", tooltip(field(interactable, "slotTooltip"), 0));
            } else if (cls.equals(API + "diagram.interactable.CustomInteractable")
                    || cls.equals(API + "diagram.interactable.AllDiagramsButton")) {
                annotations.add(
                        object(
                                "label",
                                label(field(interactable, "drawable")),
                                "tooltip",
                                tooltip(field(interactable, "tooltip"), 0),
                                "action",
                                action(field(interactable, "interact"))));
            } else throw fault("Unknown native interactable: " + cls);
        }
        return object(
                "groups",
                logicalGroups,
                "cells",
                cells,
                "labels",
                labels,
                "annotations",
                annotations);
    }

    JsonObject component(Object component) {
        return component(component, true);
    }

    JsonObject component(Object component, boolean retain) {
        if (component == null) throw fault("Null diagram component");
        Object stack = call(component, "stack");
        if (type(API + "diagram.component.ItemComponent").isInstance(component)) {
            if (!(stack instanceof ItemStack)) throw fault("Invalid native item component");
            if ((Integer) call(component, "damage") != Items.feather.getDamage((ItemStack) stack))
                throw fault("Native item matcher key differs from its display stack metadata");
            JsonObject value = stack(stack, retain);
            return retain ? compact(value) : value;
        }
        if (type(API + "diagram.component.FluidComponent").isInstance(component)) {
            if (!(stack instanceof FluidStack)) throw fault("Invalid native fluid component");
            return stack(stack, retain);
        }
        throw fault("Unknown native diagram component");
    }

    JsonObject stack(Object stack, boolean retain) {
        count();
        if (stack instanceof ItemStack) {
            ItemStack item = ((ItemStack) stack).copy();
            String registry = Item.itemRegistry.getNameForObject(item.getItem());
            if (registry == null || Item.itemRegistry.getObject(registry) != item.getItem())
                throw fault("Unregistered native diagram item");
            int meta = Items.feather.getDamage(item);
            JsonElement nbt = TypedNbt.encode(item.getTagCompound());
            String id = Identity.item(registry, meta, nbt);
            boolean template = meta == OreDictionary.WILDCARD_VALUE;
            // A native wildcard is a symbolic matcher. Never call its display-name/render callback
            // and never substitute an invented meta-zero appearance.
            if (!template && retain) items.putIfAbsent(id, item);
            return object(
                    "kind",
                    "item",
                    "registry",
                    registry,
                    "meta",
                    meta,
                    "nbt",
                    nbt,
                    "fact",
                    template ? null : id);
        }
        if (stack instanceof FluidStack) {
            FluidStack fluid = ((FluidStack) stack).copy();
            String registry = fluid.getFluid().getName();
            if (FluidRegistry.getFluid(registry) != fluid.getFluid())
                throw fault("Unregistered native diagram fluid");
            JsonElement nbt = TypedNbt.encode(fluid.tag);
            String id = Identity.fluid(registry, nbt);
            if (retain) fluids.putIfAbsent(id, fluid);
            return object("kind", "fluid", "registry", registry, "nbt", nbt, "fact", id);
        }
        throw fault("Unknown native query stack");
    }

    /** Concrete item identity already owns the complete registry/metadata/NBT fact.
     * Query edges reference it once; wildcard templates remain literal predicates. */
    static JsonObject compact(JsonObject component) {
        if (component.get("kind").getAsString().equals("item")
                && component.has("fact") && !component.get("fact").isJsonNull())
            return object("kind","itemFact","fact",component.get("fact"));
        return component;
    }

    void beginQuery() {
        nodes = 0;
        textBytes = 0;
    }

    private void addCell(
            JsonArray cells,
            IdentityHashMap<Object, JsonObject> positions,
            Map<Object, List<JsonObject>> equalPositions,
            String key,
            int index,
            Object slot) {
        count();
        Object position = call(slot, "position");
        JsonObject cell =
                object(
                        "group",
                        key,
                        "index",
                        index,
                        "alternatives",
                        array(),
                        "tooltip",
                        tooltip(call(slot, "tooltip"), 0));
        if (positions.put(position, cell) != null)
            throw fault("Ambiguous native slot position identity");
        equalPositions.computeIfAbsent(position, ignored -> new ArrayList<>()).add(cell);
        cells.add(cell);
    }

    private JsonObject label(Object label) {
        count();
        if (type(API + "diagram.layout.Text").isInstance(label))
            return object("kind", "text", "text", text((String) call(label, "text")));
        if (type(API + "diagram.layout.ComponentLabel").isInstance(label))
            return object("kind", "component", "component", component(call(label, "component")));
        throw fault(
                "Unknown native content label: "
                        + (label == null ? "null" : label.getClass().getName()));
    }

    private JsonObject display(Object value, int depth) {
        depth(depth);
        count();
        Optional<?> amount = optional(call(value, "stackSize")),
                info = optional(call(value, "additionalInfo"));
        if (amount.isPresent()
                && (!(amount.get() instanceof Integer) || (Integer) amount.get() <= 0))
            throw fault("Invalid native display quantity");
        return object(
                "component",
                component(call(value, "component")),
                "amount",
                amount.isPresent() ? amount.get().toString() : null,
                "note",
                info.isPresent() ? text((String) info.get()) : null,
                "tooltip",
                tooltip(call(value, "additionalTooltip"), depth + 1));
    }

    private JsonArray tooltip(Object tooltip, int depth) {
        depth(depth);
        JsonArray lines = array();
        for (Object line : list(call(tooltip, "lines"))) {
            count();
            JsonArray elements = array();
            for (Object element : list(call(line, "elements"))) {
                count();
                String kind = ((Enum<?>) call(element, "type")).name();
                switch (kind) {
                    case "SPACING":
                        break;
                    case "TEXT":
                        elements.add(
                                object(
                                        "kind",
                                        "text",
                                        "text",
                                        text((String) call(element, "text"))));
                        break;
                    case "COMPONENT_DESCRIPTION":
                        elements.add(
                                object(
                                        "kind",
                                        "description",
                                        "component",
                                        component(call(element, "componentDescription"))));
                        break;
                    case "DISPLAY_COMPONENT_ICON":
                        elements.add(
                                object(
                                        "kind",
                                        "display",
                                        "display",
                                        display(call(element, "displayComponentIcon"), depth + 1)));
                        break;
                    case "FORMATTING":
                        JsonArray codes = array();
                        for (Object code :
                                collection(call(call(element, "formatting"), "formatting"))) {
                            count();
                            codes.add(value(((Enum<?>) code).name()));
                        }
                        elements.add(object("kind", "formatting", "codes", codes));
                        break;
                    default:
                        throw fault("Unknown native tooltip element");
                }
            }
            // Preserve paragraph order and meaningful formatting, not pixel spacing/font size.
            lines.add(elements);
        }
        return lines;
    }

    private JsonElement action(Object interaction) {
        if (interaction.getClass() == noOpInteraction) return JsonNull.INSTANCE;
        if (interaction.getClass() == stringInteraction) {
            List<Object> captured = captured(interaction);
            if (captured.size() != 1 || !(captured.get(0) instanceof String))
                throw fault("Invalid native string navigation capture");
            return object(
                    "kind",
                    "group",
                    "group",
                    text((String) captured.get(0)),
                    "craftingOpens",
                    "usage",
                    "usageOpens",
                    "crafting");
        }
        throw fault("Unknown native diagram interaction: " + interaction.getClass().getName());
    }

    static List<Object> captured(Object callback) {
        List<Object> result = new ArrayList<>();
        for (Field f : callback.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                result.add(f.get(callback));
            } catch (ReflectiveOperationException error) {
                throw fault("Cannot inspect native callback capture: " + error);
            }
        }
        return result;
    }

    private String key(Object key) {
        return text((String) call(key, "key"));
    }

    private String text(String value) {
        if (value == null || value.length() > 65536) throw fault("Invalid native diagram text");
        try {
            int bytes =
                    StandardCharsets.UTF_8
                            .newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .encode(CharBuffer.wrap(value))
                            .remaining();
            if (bytes > 65536 || (textBytes += bytes) > 32L * 1024 * 1024)
                throw fault("Native diagram text budget exceeded");
        } catch (CharacterCodingException error) {
            throw fault("Malformed native diagram UTF-8 text");
        }
        return value;
    }

    private void count() {
        Jobs.checkpoint();
        if (++nodes > MAX) throw fault("Native diagram content budget exceeded");
    }

    private static void depth(int value) {
        if (value > 16) throw fault("Native diagram tooltip recursion budget exceeded");
    }

    static Object call(Object target, String method) {
        if (target == null) throw fault("Missing native content object");
        return invoke(target.getClass(), target, method, new Class<?>[0]);
    }

    static List<?> list(Object value) {
        if (!(value instanceof List) || ((List<?>) value).size() > MAX)
            throw fault("Invalid native diagram list");
        return (List<?>) value;
    }

    static Collection<?> collection(Object value) {
        if (!(value instanceof Collection) || ((Collection<?>) value).size() > MAX)
            throw fault("Invalid native diagram collection");
        return (Collection<?>) value;
    }

    static Map<?, ?> map(Object value) {
        if (!(value instanceof Map) || ((Map<?, ?>) value).size() > MAX)
            throw fault("Invalid native diagram map");
        return (Map<?, ?>) value;
    }

    private static Optional<?> optional(Object value) {
        if (!(value instanceof Optional)) throw fault("Invalid native component optional");
        return (Optional<?>) value;
    }

    static Jobs.Fault fault(String message) {
        return new Jobs.Fault("reference_unsupported", message);
    }
}
