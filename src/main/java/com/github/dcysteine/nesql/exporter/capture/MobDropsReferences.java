package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native client drop rules and discovery state; never evaluate world callbacks or display permutations. */
final class MobDropsReferences extends ReaderSnapshot implements NativeReferences {
    static final String HANDLER = "com.kuba6000.mobsinfo.nei.MobHandler";
    private  Class<?> owner;
    private  JsonObject source;
    private  List<?> pages;
    private  Map<?, ?> rules;
    private final boolean hidden;
    private final Map<String, ItemStack> specimens = new TreeMap<>();
    private  JsonArray records;
    private final String signature, domain;

    static boolean supports(ICraftingHandler handler) { return handler != null && HANDLER.equals(handler.getClass().getName()); }
    MobDropsReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown native mob drop handler");
        owner = handler.getClass(); this.source = copy(source);
        pages = pages(); rules = rules(); hidden = hidden();
        // An initially empty cache is not proof of a received server configuration.
        if (pages.isEmpty()) throw new Jobs.Fault("handler_not_ready", "Mobs Info empty client cache has no readiness proof");
        records = snapshot(pages, rules, hidden, this.source, specimens);
        signature = CanonicalJson.digest(records); domain = records.get(0).getAsJsonObject().get("domain").getAsString();
    }
    public int size() { checkOpen(); return records.size(); }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen(); return object("mobs", pages.size(), "drops", records.size() - pages.size() - 1, "items", specimens.size()); }
    public void verify() { checkOpen();
        List<?> current = pages();
        if (rules() != rules || rules.size() != pages.size() || hidden() != hidden || current.size() != pages.size()) throw drift();
        for (int i = 0; i < current.size(); i++) if (current.get(i) != pages.get(i)) throw drift();
    }
    public void finishVerification() { checkOpen();
        if (!signature.equals(CanonicalJson.digest(snapshot(pages(), rules(), hidden(), source, new HashMap<>())))) throw drift();
    }
    public void capture(int index, Facts facts) { checkOpen();
        Jobs.checkpoint(); JsonObject row = copy(records.get(index).getAsJsonObject()), data = row.getAsJsonObject("data");
        if (data.has("item")) captureItem(data.get("item").getAsString(), facts);
        if (data.has("representations")) for (JsonElement id : data.getAsJsonArray("representations")) captureItem(id.getAsString(), facts);
        facts.row("references", row);
    }
    private void captureItem(String id, Facts facts) {
        ItemStack stack = specimens.get(id);
        if (stack == null || !id.equals(facts.item(stack.copy()))) throw drift();
    }
    private List<?> pages() {
        Object raw = field(owner, null, "cachedRecipes");
        if (!(raw instanceof List)) throw fault("Missing native mob pages");
        return new ArrayList<>((List<?>) raw);
    }
    private static Map<?, ?> rules() {
        Object raw = field(type("com.kuba6000.mobsinfo.api.MobRecipe"), null, "MobNameToRecipeMap");
        if (!(raw instanceof Map)) throw fault("Missing authoritative mob rules");
        return (Map<?, ?>) raw;
    }
    private static boolean hidden() { return bool(field(type("com.kuba6000.mobsinfo.config.Config$MobHandler"), null, "hiddenMode")); }

    static JsonArray snapshot(List<?> pages, Map<?, ?> rules, boolean hidden, JsonObject source, Map<String, ItemStack> specimens) {
        if (pages.size() > 16384 || rules.size() != pages.size()) throw fault("Native mob page/rule coverage differs");
        long[] bytes = {0};
        JsonArray data = array(); append(data, object("kind", "mobDropsDefinition", "source", source, "mobs", pages.size(),
                "drops", 0, "hiddenMode", hidden, "ready", true, "consuming", false), bytes);
        Set<String> entities = new HashSet<>(); int total = 0;
        for (Object page : pages) {
            Jobs.checkpoint(); if (page == null) throw fault("Null native mob page");
            String displayEntity = text(field(page, "mobname")); Object entity = field(page, "mob");
            Object rule = rules.get(displayEntity); String key = displayEntity;
            // Native aliases (e.g. skeleton variants) can share EntityList names. Bind by the published entity object.
            if (rule == null || field(rule, "entity") != entity) {
                rule = null;
                for (Map.Entry<?, ?> candidate : rules.entrySet()) if (field(candidate.getValue(), "entity") == entity) {
                    if (rule != null || !(candidate.getKey() instanceof String)) throw fault("Ambiguous native mob alias");
                    rule = candidate.getValue(); key = (String) candidate.getKey();
                }
            }
            if (rule == null || !key.equals(text(field(rule, "entityName"))) || !entities.add(key)) throw fault("Native mob identity/rule missing or duplicate");
            List<?> drops = list(field(rule, "mOutputs"), 262144);
            total = Math.addExact(total, drops.size());
            if (data.size() + 1L + drops.size() > 262144) throw fault("Native mob reference entry budget exceeded");
            float health = ((Number) field(page, "maxHealth")).floatValue();
            if (!Float.isFinite(health) || health <= 0) throw fault("Invalid native mob health");
            int infernal = integer(field(page, "infernaltype"));
            if (infernal < -1 || infernal > 2) throw fault("Unknown native infernal eligibility");
            boolean visible = bool(call(page, "isUnlocked", new Class<?>[0]));
            if (!hidden && !visible) throw fault("Native discovery contradicts hidden-mode policy");
            JsonArray inputs = array();
            for (Object input : list(field(page, "mInput"), 4096)) inputs.add(value(item(input, specimens)));
            Object spawn = field(page, "spawnList"); JsonArray spawns = array();
            if (spawn != null) {
                if (!(spawn instanceof Set) || ((Set<?>) spawn).size() > 16384) throw fault("Invalid native spawn catalogue");
                // Spawn order is presentation only; native HashSet iteration must not create identity drift.
                List<JsonObject> ordered = new ArrayList<>();
                for (Object info : (Set<?>) spawn) ordered.add(object("nativeClass", info.getClass().getName(), "description", text(call(info, "getInfo", new Class<?>[0]))));
                ordered.sort(Comparator.comparing(CanonicalJson::digest)); for (JsonObject info : ordered) spawns.add(info);
            }
            int damageTotal = integer(field(rule, "mMaxDamageChance"));
            if (damageTotal < 0) throw fault("Invalid native damage weight total");
            append(data, object("kind", "mobDefinition", "entity", key, "displayEntity", displayEntity, "entityClass", entity.getClass().getName(),
                    "name", text(field(page, "localizedName")), "mod", text(field(page, "mod")), "maxHealth", Float.toString(health),
                    "infernalType", infernal, "peaceful", bool(field(page, "isPeacefulAllowed")), "vial", bool(field(page, "isUsableInVial")),
                    "visible", visible, "drops", drops.size(), "damageWeightTotal", damageTotal, "representations", inputs,
                    "spawnKnown", spawn != null, "spawns", spawns, "additionalInfo", texts(field(page, "additionalInformation"))), bytes);
            long observedDamageTotal = 0;
            for (int i = 0; i < drops.size(); i++) {
                Jobs.checkpoint(); Object drop = drops.get(i);
                if (drop == null || !drop.getClass().getName().equals("com.kuba6000.mobsinfo.api.MobDrop")) throw fault("Unknown native mob drop rule");
                ItemStack stack = (ItemStack) field(drop, "stack"); String item = item(stack, specimens);
                int chance = integer(field(drop, "chance")); if (chance < 0) throw fault("Negative native drop chance");
                Object kind = field(drop, "type");
                if (!(kind instanceof Enum)) throw fault("Missing native drop type"); String dropType = ((Enum<?>) kind).name();
                if (!Arrays.asList("Normal", "Rare", "Additional", "Infernal").contains(dropType)) throw fault("Unknown native drop type");
                Object enchant = field(drop, "enchantable"); if (enchant != null && integer(enchant) < 0) throw fault("Negative native enchantment power");
                Object damage = field(drop, "damages"); JsonElement weights = JsonNull.INSTANCE;
                if (damage != null) {
                    if (!(damage instanceof Map) || ((Map<?, ?>) damage).isEmpty() || ((Map<?, ?>) damage).size() > 65536) throw fault("Invalid native damage distribution");
                    JsonArray values = array();
                    // Runtime traverses this map with a shared weight total. Keep its order and weights, do not normalize locally.
                    for (Map.Entry<?, ?> weight : ((Map<?, ?>) damage).entrySet()) {
                        int meta = integer(weight.getKey()), count = integer(weight.getValue());
                        if (meta < 0 || count <= 0) throw fault("Invalid native damage weight");
                        observedDamageTotal += count; values.add(object("meta", meta, "weight", count));
                    }
                    weights = values;
                }
                JsonArray modifiers = array(); boolean variable = bool(field(drop, "variableChance"));
                for (Object modifier : list(field(drop, "chanceModifiers"), 256)) {
                    List<String> description = new ArrayList<>(); call(modifier, "applyTooltip", new Class<?>[]{List.class}, description);
                    modifiers.add(object("nativeClass", modifier.getClass().getName(), "priority", integer(call(modifier, "getPriority", new Class<?>[0])),
                            "description", texts(description), "payload", payload(modifier)));
                }
                if (!variable && modifiers.size() != 0) throw fault("Inactive native chance modifier state");
                Object additionalInfo = field(drop, "additionalInfo");
                append(data, object("kind", "mobDrop", "entity", key, "drop", i, "item", item, "amount", Integer.toString(stack.stackSize),
                        "dropType", dropType, "baseChance", chance, "chanceScale", 10000, "variableChance", variable,
                        "lootable", bool(field(drop, "lootable")), "playerOnly", bool(field(drop, "playerOnly")), "enchantmentPower", enchant,
                        "damages", weights, "modifiers", modifiers, "additionalInfo", additionalInfo == null ? array() : texts(additionalInfo)), bytes);
            }
            if (observedDamageTotal != damageTotal) throw fault("Native damage weight snapshot is inconsistent");
        }
        data.get(0).getAsJsonObject().addProperty("drops", total);
        String domain = Identity.content("reference", object("kind", "mobsInfoDrops", "records", data)); JsonArray rows = array();
        for (int i = 0; i < data.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", data.get(i)); row.addProperty("id", Identity.content("refrow", row));
            if (CanonicalJson.bytes(row).length > 1048576) throw fault("Native mob reference leaf exceeds byte budget"); rows.add(row);
        }
        return rows;
    }
    private static String item(Object value, Map<String, ItemStack> specimens) {
        if (!(value instanceof ItemStack)) throw fault("Missing native mob item"); ItemStack stack = ((ItemStack) value).copy();
        if (stack.getItem() == null || stack.stackSize <= 0) throw fault("Invalid native mob item amount");
        String registry = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) throw fault("Unregistered native mob item");
        String id = Identity.item(registry, stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound()));
        stack.stackSize = 1; specimens.put(id, stack); return id;
    }
    private static String payload(Object modifier) {
        Class<?> bufferType = type("io.netty.buffer.ByteBuf");
        Object buffer = call(type("io.netty.buffer.Unpooled"), "buffer", new Class<?>[]{int.class, int.class}, 256, 65536);
        Throwable failure = null;
        try {
            call(modifier, "writeToByteBuf", new Class<?>[]{bufferType}, buffer);
            int length = integer(call(buffer, "readableBytes", new Class<?>[0]));
            if (length < 0 || length > 65536 || integer(call(buffer, "readerIndex", new Class<?>[0])) != 0)
                throw fault("Native modifier payload budget or writer contract violated");
            byte[] bytes = new byte[length]; call(buffer, "getBytes", new Class<?>[]{int.class, byte[].class}, 0, bytes);
            char[] digits = "0123456789abcdef".toCharArray();
            StringBuilder hex = new StringBuilder(length * 2);
            for (byte b : bytes) { hex.append(digits[(b & 255) >>> 4]); hex.append(digits[b & 15]); }
            return hex.toString();
        } catch (RuntimeException | Error error) { failure = error; throw error; }
        finally {
            try { call(buffer, "release", new Class<?>[0]); }
            catch (RuntimeException | Error cleanup) { if (failure != null) failure.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    private static Object call(Object target, String name, Class<?>[] arguments, Object... values) {
        if (target == null) throw fault("Missing native mob callback owner"); Class<?> owner = target instanceof Class ? (Class<?>) target : target.getClass();
        try {
            Method method = owner.getMethod(name, arguments); method.setAccessible(true); return method.invoke(target instanceof Class ? null : target, values);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause(); if (cause instanceof Error) throw (Error) cause; if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            Jobs.Fault failed = fault("Native mob callback failed: " + name); failed.initCause(cause); throw failed;
        } catch (ReflectiveOperationException error) { Jobs.Fault failed = fault("Missing pinned native mob callback: " + name); failed.initCause(error); throw failed; }
    }
    private static List<?> list(Object value, int limit) { if (!(value instanceof List) || ((List<?>) value).size() > limit) throw fault("Invalid native mob list"); return (List<?>) value; }
    private static void append(JsonArray data, JsonObject row, long[] bytes) {
        int weight = CanonicalJson.bytes(row).length;
        bytes[0] += weight;
        // Reserve a bounded envelope for the row/domain hashes and ordinal.
        if (weight > 1024 * 1024 - 1024 || bytes[0] > 64L * 1024 * 1024) throw fault("Native mob reference exceeds byte budget");
        data.add(row);
    }
    private static JsonArray texts(Object raw) {
        JsonArray values = array();
        for (Object line : list(raw, 4096)) {
            if (!(line instanceof String) || ((String) line).length() > 65536) throw fault("Invalid native mob description line");
            values.add(value(line));
        }
        return values;
    }
    private static String text(Object raw) { if (!(raw instanceof String) || ((String) raw).isEmpty() || ((String) raw).length() > 65536) throw fault("Invalid native mob text"); return (String) raw; }
    private static int integer(Object raw) { if (!(raw instanceof Integer)) throw fault("Invalid native mob integer"); return (Integer) raw; }
    private static boolean bool(Object raw) { if (!(raw instanceof Boolean)) throw fault("Invalid native mob boolean"); return (Boolean) raw; }
    private static JsonObject copy(JsonObject row) { return new JsonParser().parse(row.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("reference_unsupported", message); }
    private static Jobs.Fault drift() { return new Jobs.Fault("environment_changed", "Native mob rules or discovery changed"); }

    @Override protected void release(){specimens.clear();owner=null;source=null;pages=null;rules=null;records=null;}
}
