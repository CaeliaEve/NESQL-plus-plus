package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/** Native documentation and matching links, without font wrapping or recipe GUI callbacks. */
final class NativeInformationReferences implements NativeReferences {
    static final String HANDLER = "codechicken.nei.recipe.InformationHandler";
    static final String EXTRA_UTILS_HANDLER = "com.rwtema.extrautils.nei.InfoHandler";
    private static final int MAX = 262144;
    private final ICraftingHandler handler;
    private final JsonObject source;
    private final List<?> pages;
    private final List<ItemStack> panel;
    private final Snapshot snapshot;
    private final String signature;
    private boolean closed;

    static boolean supports(ICraftingHandler value) {
        return value != null
                && (HANDLER.equals(value.getClass().getName())
                        || EXTRA_UTILS_HANDLER.equals(value.getClass().getName()));
    }

    NativeInformationReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)
                || !handler.getClass().getName().equals(source.get("handler").getAsString()))
            throw fault("Unknown NEI information source");
        if (!ItemList.loadFinished || ItemList.items == null) throw unready();
        this.handler = handler;
        this.source = copy(source);
        panel = ItemList.items;
        Object raw =
                HANDLER.equals(handler.getClass().getName())
                        ? field(type(HANDLER), null, "ITEM_INFO")
                        : null;
        if (raw != null && !(raw instanceof List)
                || HANDLER.equals(handler.getClass().getName()) && raw == null) throw unready();
        pages = raw == null ? null : (List<?>) raw;
        snapshot = read();
        signature = CanonicalJson.digest(snapshot.records);
    }

    public int size() {
        ensureOpen();
        return snapshot.records.size();
    }

    public String domain() {
        ensureOpen();
        return snapshot.records.get(0).getAsJsonObject().get("domain").getAsString();
    }

    public JsonObject counts() {
        ensureOpen();
        return copy(
                snapshot.records
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("data")
                        .getAsJsonObject("counts"));
    }

    public void verify() {
        ensureOpen();
        Jobs.checkpoint();
        if (!ItemList.loadFinished
                || ItemList.items != panel
                || panel.size() != snapshot.scanned
                || pages != null
                        && (field(type(HANDLER), null, "ITEM_INFO") != pages
                                || pages.size() != snapshot.nativeEntries)) throw drift();
    }

    public void finishVerification() {
        verify();
        if (!signature.equals(CanonicalJson.digest(read().records))) throw drift();
    }

    public void capture(int index, Facts facts) {
        verify();
        JsonObject row = copy(snapshot.records.get(index).getAsJsonObject());
        JsonObject data = row.getAsJsonObject("data");
        if (index > 0) {
            for (JsonElement raw : data.getAsJsonArray("items")) {
                String id = raw.getAsJsonObject().get("item").getAsString();
                ItemStack stack = snapshot.specimens.get(id);
                if (stack == null || !facts.item(stack.copy()).equals(id)) throw drift();
            }
            for (JsonElement raw : data.getAsJsonArray("lines")) facts.text(raw.getAsString());
        }
        facts.row("references", row);
    }

    public void close() {
        if (!closed) {
            snapshot.specimens.clear();
            closed = true;
        }
    }

    private void ensureOpen() {
        if (closed) throw new Jobs.Fault("capture_closed", "Information cursor is closed");
    }

    private Snapshot read() {
        Jobs.checkpoint();
        if (panel.size() > MAX || pages != null && pages.size() > MAX)
            throw fault("Native information entry budget exceeded");
        Snapshot result = new Snapshot();
        result.scanned = panel.size();
        result.nativeEntries = pages == null ? panel.size() : pages.size();
        List<JsonObject> data = new ArrayList<>();
        if (pages != null) {
            for (int index = 0; index < pages.size(); index++) {
                Jobs.checkpoint();
                Object entry = pages.get(index);
                if (entry == null) throw fault("Null native information registration");
                Object rawItems = field(entry, "items"),
                        rawInfo = field(entry, "info"),
                        filter = field(entry, "filter");
                if (!(rawItems instanceof List)
                        || !(rawInfo instanceof String)
                        || ((String) rawInfo).isEmpty()
                        || !(filter instanceof ItemFilter))
                    throw fault("Malformed native information registration");
                List<?> members = (List<?>) rawItems;
                if (members.size() > MAX || (result.links += members.size()) > MAX)
                    throw fault("Native information item-link budget exceeded");
                JsonArray items = array();
                for (Object member : members) {
                    if (!(member instanceof ItemStack))
                        throw fault("Invalid native documentation item link");
                    ItemStack stack = owned((ItemStack) member);
                    if (!((ItemFilter) filter).matches(stack.copy()))
                        throw fault("Native documentation link no longer matches its filter");
                    items.add(link(stack, result.specimens));
                }
                JsonArray text = array();
                text.add(text((String) rawInfo));
                append(
                        result,
                        data,
                        page(
                                data.size(),
                                index,
                                items,
                                text,
                                filter.getClass().getName(),
                                null,
                                true));
            }
        } else {
            for (int index = 0; index < panel.size(); index++) {
                Jobs.checkpoint();
                ItemStack candidate = owned(panel.get(index));
                Object valid =
                        invoke(
                                handler.getClass(),
                                handler,
                                "isValidItem",
                                new Class<?>[] {ItemStack.class},
                                candidate.copy());
                if (!(valid instanceof Boolean))
                    throw fault("Invalid native documentation predicate result");
                if (!((Boolean) valid)) continue;
                String specific = candidate.copy().getUnlocalizedName();
                boolean precise =
                        StatCollector.canTranslate(specific + ".documentation")
                                || StatCollector.canTranslate(specific + ".documentation.0");
                String base = precise ? specific : candidate.getItem().getUnlocalizedName();
                JsonArray text = array();
                if (StatCollector.canTranslate(base + ".documentation"))
                    text.add(text(base + ".documentation"));
                else
                    for (int part = 0;
                            StatCollector.canTranslate(base + ".documentation." + part);
                            part++) {
                        if (part >= 4096)
                            throw fault("Native documentation paragraph budget exceeded");
                        text.add(text(base + ".documentation." + part));
                    }
                if (text.size() == 0)
                    throw fault("Native valid documentation item has no readable text");
                candidate.stackSize = 1; // The installed constructor changes display quantity only.
                JsonArray items = array();
                items.add(link(candidate, result.specimens));
                if (++result.links > MAX)
                    throw fault("Native information item-link budget exceeded");
                append(result, data, page(data.size(), index, items, text, null, precise, false));
            }
        }
        int listed = 0;
        for (JsonObject row : data) if (row.get("listed").getAsBoolean()) listed++;
        data.add(
                0,
                object(
                        "kind",
                        "neiInformationDefinition",
                        "source",
                        source,
                        "backend",
                        pages == null ? "extraUtilities" : "nei",
                        "ready",
                        true,
                        "consuming",
                        false,
                        "representation",
                        "unwrappedNativeText",
                        "linePolicy",
                        pages == null ? "plainNativeText" : "neiEscapedNewlines",
                        "counts",
                        object(
                                "nativeEntries",
                                result.nativeEntries,
                                "scannedItems",
                                result.scanned,
                                "pages",
                                data.size(),
                                "listed",
                                listed,
                                "itemLinks",
                                result.links)));
        JsonArray all = array();
        for (JsonObject row : data) all.add(row);
        String domain =
                Identity.content("reference", object("kind", "neiInformation", "records", all));
        long bytes = 0;
        for (int i = 0; i < data.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", data.get(i));
            row.addProperty("id", Identity.content("refrow", row));
            int length = CanonicalJson.bytes(row).length;
            if (length > 1024 * 1024 || (bytes += length) > 32L * 1024 * 1024)
                throw fault("Native information byte budget exceeded");
            result.records.add(row);
        }
        return result;
    }

    private JsonObject page(
            int page,
            int nativeIndex,
            JsonArray items,
            JsonArray text,
            String filterOwner,
            Boolean precise,
            boolean escaped) {
        JsonArray lines = array();
        for (JsonElement entry : text) {
            String content = entry.getAsJsonObject().get("text").getAsString();
            if (escaped) content = content.replace("\\n", "\n");
            for (String line : content.replace("\r\n", "\n").split("\n", -1))
                lines.add(value(line));
        }
        return object(
                "kind",
                "neiInformationPage",
                "source",
                source,
                "page",
                page,
                "nativeIndex",
                nativeIndex,
                "items",
                items,
                "listed",
                items.size() > 0,
                "textSources",
                text,
                "lines",
                lines,
                "filterOwner",
                filterOwner,
                "precise",
                precise,
                "consuming",
                false);
    }

    private static JsonObject text(String key) {
        validateText(key);
        boolean translated = StatCollector.canTranslate(key);
        String text = StatCollector.translateToLocal(key);
        validateText(text);
        return object("key", key, "translated", translated, "text", text);
    }

    private static void append(Snapshot snapshot, List<JsonObject> pages, JsonObject page) {
        int bytes = CanonicalJson.bytes(page).length;
        if (bytes > 1024 * 1024 || (snapshot.bytes += bytes) > 32L * 1024 * 1024)
            throw fault("Native information byte budget exceeded");
        pages.add(page);
    }

    private static void validateText(String value) {
        if (value == null) throw fault("Null native information text");
        try {
            if (StandardCharsets.UTF_8
                            .newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .encode(CharBuffer.wrap(value))
                            .remaining()
                    > 65536) throw fault("Native information text budget exceeded");
        } catch (CharacterCodingException error) {
            throw fault("Invalid native information UTF-8 text");
        }
    }

    private static ItemStack owned(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0)
            throw fault("Invalid documentation stack");
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        if (name == null || Item.itemRegistry.getObject(name) != stack.getItem())
            throw fault("Unregistered documentation item");
        return stack.copy();
    }

    private static JsonObject link(ItemStack stack, Map<String, ItemStack> specimens) {
        String id =
                Identity.item(
                        Item.itemRegistry.getNameForObject(stack.getItem()),
                        Items.feather.getDamage(stack),
                        TypedNbt.encode(stack.getTagCompound()));
        specimens.putIfAbsent(id, stack.copy());
        return object("item", id, "amount", Integer.toString(stack.stackSize));
    }

    static final class Snapshot {
        final JsonArray records = array();
        final Map<String, ItemStack> specimens = new LinkedHashMap<>();
        int nativeEntries, scanned, links;
        long bytes;
    }

    private static JsonObject copy(JsonObject value) {
        return new JsonParser().parse(value.toString()).getAsJsonObject();
    }

    private static Jobs.Fault fault(String message) {
        return new Jobs.Fault("reference_invalid", message);
    }

    private static Jobs.Fault unready() {
        return new Jobs.Fault(
                "handler_not_ready",
                "Native information item catalogue or registrations are not ready");
    }

    private static Jobs.Fault drift() {
        return new Jobs.Fault(
                "environment_changed",
                "Native documentation, filters, quantities or language changed");
    }
}
