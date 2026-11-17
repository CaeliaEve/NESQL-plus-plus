package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Raw trade facts must survive without evaluating randomized display stacks. */
final class VillagerTradesTest {
    static void run() throws Exception {
        Class<?> reader = Class.forName(
                "com.github.dcysteine.nesql.exporter.capture.VillagerTradeReferences");
        Method read = reader.getDeclaredMethod("snapshot", List.class, JsonObject.class, Map.class);
        read.setAccessible(true);
        JsonObject source = object("owner", "mobsinfo", "handler",
                "com.kuba6000.mobsinfo.nei.VillagerTradesHandler", "key", "mobsinfo.villagertrades");
        Page page = new Page();
        Map<String, ItemStack> specimens = new HashMap<>();
        JsonArray rows = (JsonArray) read.invoke(null, Collections.singletonList(page), source, specimens);
        JsonObject offer = rows.get(1).getAsJsonObject();
        if (rows.size() != 2 || !offer.get("nativeFrequency").getAsString().equals("1.25")
                || offer.getAsJsonArray("inputs").size() != 2
                || !offer.getAsJsonObject("output").get("enchantmentPower").getAsString().equals("30")
                || !offer.getAsJsonArray("inputs").get(0).getAsJsonObject().getAsJsonArray("sizes").toString().equals("[-2147483648,-3,0,2,5,2147483647]"))
            throw new AssertionError("Trade semantics were flattened or frequency clamped");
        String before = CanonicalJson.digest(rows);
        page.tradeList.get(0).getRight().firstInput.possibleSizes.add(8);
        JsonArray changed = (JsonArray) read.invoke(null, Collections.singletonList(page), source, new HashMap<>());
        if (before.equals(CanonicalJson.digest(changed))) throw new AssertionError("Quantity drift invisible");
        page.tradeList.get(0).getRight().firstInput.possibleSizes.remove(8);
        page.tradeList.get(0).getRight().chance = Double.NaN;
        try {
            read.invoke(null, Collections.singletonList(page), source, new HashMap<>());
            throw new AssertionError("Non-finite frequency accepted");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof Jobs.Fault)) throw expected;
        }
        page.tradeList.get(0).getRight().chance = 1.25;
        page.tradeList.get(0).getRight().output.possibleSizes = new HashSet<>();
        try {
            read.invoke(null, Collections.singletonList(page), source, new HashMap<>());
            throw new AssertionError("Empty quantity domain accepted");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof Jobs.Fault)) throw expected;
        }
        JsonArray records = array();
        String domain = Identity.content("reference", object("kind", "mobsInfoVillagerTrades", "records", rows));
        for (int i = 0; i < rows.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", rows.get(i));
            row.addProperty("id", Identity.content("refrow", row)); records.add(row);
        }
        JsonArray items = array();
        for (Map.Entry<String, ItemStack> entry : new TreeMap<>(specimens).entrySet()) {
            ItemStack stack = entry.getValue();
            items.add(object("id", entry.getKey(), "registry", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                    "meta", stack.getItemDamage(), "nbt", JsonNull.INSTANCE, "tags", array()));
        }
        java.nio.file.Path target = java.nio.file.Paths.get("build/native-tests/villager-trades-contract.json");
        java.nio.file.Files.createDirectories(target.getParent());
        java.nio.file.Files.write(target, CanonicalJson.bytes(object("records", records, "items", items)));
        System.out.println("Villager references: quantity alternatives, raw frequency, enchantment and drift passed");
    }
    public static final class Page {
        final int professionID = 3;
        final String profession = "Blacksmith", mod = "Minecraft";
        final List<TradePair> tradeList = Collections.singletonList(new TradePair());
    }
    public static final class TradePair {
        final Trade trade = new Trade();
        public Trade getRight() { return trade; }
    }
    public static final class Trade {
        final TradeItem firstInput = new TradeItem(new ItemStack(Items.emerald, 2), new HashSet<>(Arrays.asList(5, 0, 2, -3, Integer.MIN_VALUE, Integer.MAX_VALUE)), null);
        final TradeItem secondInput = new TradeItem(new ItemStack(Items.book), null, null);
        final TradeItem output = new TradeItem(new ItemStack(Items.enchanted_book), null, 30);
        double chance = 1.25;
        public TradeItem getFirstInput() { return firstInput; }
        public TradeItem getSecondInput() { return secondInput; }
        public TradeItem getOutput() { return output; }
        public double getChance() { return chance; }
    }
    public static final class TradeItem {
        final ItemStack stack; Set<Integer> possibleSizes; final Integer enchantability;
        TradeItem(ItemStack stack, Set<Integer> sizes, Integer power) {
            this.stack = stack; this.possibleSizes = sizes; this.enchantability = power;
        }
    }
}
