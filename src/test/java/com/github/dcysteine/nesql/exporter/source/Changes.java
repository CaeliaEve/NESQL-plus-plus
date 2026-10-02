package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Shared, explicit expected outcomes for tag replacement and the pinned inheritance rules. */
final class Changes {
    private Changes() {}

    static void enchanter(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                         String texture, Function<String,String> text) {
        JsonObject origin=object("owner","fixture","handler","enchanter","key","enchanter");
        String category=Identity.origin("category",origin);
        String book=item(items,"minecraft:writable_book",0,null,false,"Book and Quill 书与笔",texture,text);
        String material=item(items,"fixture:enchanter_material",4,null,false,"Enchanter material 附魔材料",texture,text);
        categories.add(object("id",category,"source",origin,"name",text.apply("EnderIO Enchanter 末影接口附魔器"),"icon",object("kind","item","id",book),"machines",array(),"view",null,"order",categories.size()));
        for(int level:new int[]{1,2,5}) {
            NBTTagCompound tag=new NBTTagCompound(),ench=new NBTTagCompound();NBTTagList list=new NBTTagList();
            ench.setShort("id",(short)16);ench.setShort("lvl",(short)level);list.appendTag(ench);tag.setTag("StoredEnchantments",list);
            String output=item(items,"minecraft:enchanted_book",0,tag,false,"Enchanted Book 附魔书 "+level,texture,text);
            JsonObject bookChoice=choice(book),materialChoice=choice(material);
            bookChoice.add("rule",object("kind","wildcard","meta",true,"nbt",true));
            materialChoice.add("rule",object("kind","wildcard","meta",false,"nbt",true));materialChoice.addProperty("amount",Integer.toString(3*level));
            JsonObject row=object("source",origin,"category",category,"order",level-1,
                "inputs",array(object("slot",0,"kind","item","choices",array(bookChoice)),object("slot",1,"kind","item","choices",array(materialChoice))),
                "outputs",array(object("slot",0,"kind","item","id",output,"amount","1","quantity",null,"chance",Chance.of(1,1),"role","result","change",null)),
                "process",object("kind","enchanter","level",level,"maxLevel",5,"itemsPerLevel",3,"cost",7+2*level*level),
                "duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",new JsonObject());
            row.addProperty("id",Identity.recipe(row));recipes.add(row);
        }
    }

    static void inscriber(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                          String texture, Function<String, String> text) {
        JsonObject origin = object("owner", "fixture", "handler", "inscriber", "key", "inscriber");
        String category = Identity.origin("category", origin);
        String center = item(items,"fixture:inscriber_input",0,null,false,"Inscriber input 压印材料",texture,text);
        String top = item(items,"fixture:inscriber_top",0,null,false,"Inscriber top 上模板",texture,text);
        String bottom = item(items,"fixture:inscriber_bottom",0,null,false,"Inscriber bottom 下模板",texture,text);
        String name = item(items,"fixture:name_press",0,null,false,"Name press 命名模板",texture,text);
        String output = item(items,"fixture:processor",0,null,false,"Inscriber output 压印产物",texture,text);
        categories.add(object("id",category,"source",origin,"name",text.apply("Inscriber 压印器"),"icon",object("kind","item","id",center),"machines",array(),"view",null,"order",categories.size()));
        for(int mode=0;mode<3;mode++) {
            JsonArray inputs=array();
            for(int slot=0;slot<3;slot++) {
                if(mode==2&&slot!=2)continue;
                JsonObject choice=choice(slot==0?top:slot==1?bottom:center);
                choice.add("rule",object("kind","ae"));
                choice.add("consume",object("kind",mode==0&&slot!=2?"keep":"consume"));
                inputs.add(object("kind","item","slot",slot,"choices",array(choice)));
            }
            JsonObject row=object("source",origin,"category",category,"order",mode,"inputs",inputs,
                "outputs",array(object("slot",0,"kind","item","id",output,"amount","3","quantity",null,"chance",Chance.of(1,1),"role","result","change",null)),
                "duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",new JsonObject(),
                "process",object("kind","inscriber","mode",mode==0?"inscribe":"press","top",mode==2?null:top,"bottom",bottom,"namePress",name));
            row.addProperty("id",Identity.recipe(row)); recipes.add(row);
        }
    }

    static void map(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                    String texture, Function<String, String> text) {
        JsonObject origin = object("owner", "fixture", "handler", "mapScaling", "key", "mapScaling");
        String category = Identity.origin("category", origin);
        NBTTagCompound tags = new NBTTagCompound(); tags.setString("owner", "retained");
        String center = item(items, "minecraft:filled_map", 37, tags, false, "Filled map 已填充地图", texture, text);
        tags = copy(tags); tags.setBoolean("map_is_scaling", true);
        String output = item(items, "minecraft:filled_map", 37, tags, false, "Pending map 待完成地图样本", texture, text);
        String paper = item(items, "minecraft:paper", 0, null, false, "Paper 纸", texture, text);
        JsonArray inputs = array(), cells = array();
        for (int slot = 0; slot < 9; slot++) {
            JsonObject choice = choice(slot == 4 ? center : paper);
            choice.add("rule", object("kind", "wildcard", "meta", slot == 4, "nbt", true));
            inputs.add(object("kind", "item", "slot", slot, "choices", array(choice))); cells.add(value(slot));
        }
        categories.add(object("id", category, "source", origin, "name", text.apply("Map scaling 地图扩展"),
                "icon", object("kind", "item", "id", center), "machines", array(), "view", null, "order", categories.size()));
        JsonObject row = object("source", origin, "category", category, "order", 0, "inputs", inputs,
                "outputs", array(object("slot", 0, "kind", "item", "id", output, "amount", "1", "quantity", null,
                        "chance", Chance.of(1,1), "role", "result", "change", object("input", 4,
                                "action", object("kind", "mapScaling"), "samples", array(stack(output))))),
                "duration", null, "energy", null, "grid", object("width", 3, "height", 3, "cells", cells, "mirror", true),
                "view", null, "properties", new JsonObject(), "process", object("kind", "mapScaling"), "magic", null);
        row.addProperty("id", Identity.recipe(row)); recipes.add(row);
    }

    static void runic(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                      String texture, Function<String, String> text) {
        JsonObject origin = object("owner", "fixture", "handler", "runic", "key", "runic");
        String category = Identity.origin("category", origin);
        NBTTagCompound tags = new NBTTagCompound(); tags.setInteger("RS.HARDEN", 3); tags.setString("owner", "kept");
        String center = item(items, "fixture:runic_armor", 7, tags, true, "Runic armor 符文护甲", texture, text);
        tags = copy(tags); tags.setByte("RS.HARDEN", (byte) 4);
        String output = item(items, "fixture:runic_armor", 7, tags, true, "Runic armor upgraded 符文护甲升级", texture, text);
        String diamond = item(items, "minecraft:diamond", 0, null, false, "Diamond 钻石", texture, text);
        String resource = item(items, "Thaumcraft:ItemResource", 14, null, false, "Runic component 符文材料", texture, text);
        JsonArray inputs = new JsonArray(), costs = new JsonArray();
        String[] ids = {center, diamond, resource};
        for (int slot = 0; slot < ids.length; slot++) {
            JsonObject choice = choice(ids[slot]);
            choice.add("rule", slot == 0 ? object("kind", "wildcard", "meta", true, "nbt", true)
                    : object("kind", "infusion", "template", ids[slot], "ores", array()));
            if (slot == 2) { choice.addProperty("amount", "4"); choice.add("consume", object("kind", "pedestals")); }
            inputs.add(object("kind", "item", "slot", slot, "choices", array(choice)));
        }
        for (String key : new String[] {"tutamen", "praecantatio", "potentia"}) costs.add(object("aspect",
                Identity.origin("aspect", object("owner", "Thaumcraft", "handler", "thaumcraft.api.aspects.Aspect", "key", key)),
                "amount", key.equals("potentia") ? "256" : "128"));
        categories.add(object("id", category, "source", origin, "name", text.apply("Runic 符文注魔"),
                "icon", object("kind", "item", "id", center), "machines", array(), "view", null, "order", categories.size()));
        JsonObject row = object("source", origin, "category", category, "order", 0, "inputs", inputs,
                "outputs", array(object("slot", 0, "kind", "item", "id", output, "amount", "1", "quantity", null,
                        "chance", Chance.of(1,1), "role", "result", "change", object("input", 0, "action", object("kind", "runic"), "samples", array(stack(output))))),
                "duration", null, "energy", null, "grid", null, "view", null, "properties", new JsonObject(),
                "process", object("kind", "runic", "charge", 3), "magic", object("kind", "infusion", "central", 0, "instability", 6,
                        "aspects", costs, "research", array(object("key", "RUNICAUGMENTATION", "id", null, "completed", null)), "payment", null, "creative", false));
        row.addProperty("id", Identity.recipe(row)); recipes.add(row);
    }

    static void scans(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                      String honey, String texture, Function<String, String> text) {
        JsonObject origin = object("owner", "fixture", "handler", "fixture:scanner", "key", "scanner");
        String category = Identity.origin("category", origin);
        JsonArray[] choices = {new JsonArray(), new JsonArray()}, samples = {new JsonArray(), new JsonArray()};
        for (String species : Arrays.asList("oak", "birch")) {
            NBTTagCompound chromosome = new NBTTagCompound(); chromosome.setByte("Slot", (byte) 0);
            chromosome.setString("UID0", "fixture." + species); chromosome.setString("UID1", "fixture." + species);
            NBTTagCompound genome = new NBTTagCompound(); genome.setTag("Chromosomes", list(chromosome));
            String[] ids = new String[2];
            for (int state = 0; state < 2; state++) {
                NBTTagCompound data = new NBTTagCompound(); data.setTag("Genome", genome.copy()); data.setTag("Mate", genome.copy());
                data.setBoolean("IsAnalyzed", state == 1);
                ids[state] = item(items, "fixture:sapling", 0, data, false, (state == 0 ? "Unanalyzed " : "Analyzed ") + species + " 树苗", texture, text);
                choices[state].add(object("id", ids[state], "amount", "1", "consume", object("kind", "stack"), "returns", new JsonArray(),
                        "rule", object("kind", "member", "root", "rootTrees", "analyzed", state == 1)));
            }
            for (JsonArray cases : samples) cases.add(stack(ids[1]));
        }
        categories.add(object("id", category, "source", origin, "name", text.apply("Scanner 基因扫描"), "icon", object("kind", "item", "id", choices[0].get(0).getAsJsonObject().get("id")),
                "machines", new JsonArray(), "view", null, "order", categories.size()));
        for (int state = 0; state < 2; state++) {
            JsonObject output = object("slot", 0, "kind", "item", "id", samples[state].get(0).getAsJsonObject().get("id"), "amount", "1", "quantity", null,
                    "chance", object("numerator", "1", "denominator", "1"), "role", "result", "change", object("input", 0, "action", object("kind", "analyze"), "samples", samples[state]));
            JsonObject row = object("source", origin, "category", category, "inputs", array(object("slot", 0, "kind", "item", "choices", choices[state]),
                    object("slot", 0, "kind", "fluid", "choices", array(object("id", honey, "amount", "100", "consume", object("kind", state == 0 ? "consume" : "keep"), "returns", new JsonArray(), "rule", object("kind", "exact"))))),
                    "outputs", array(output), "duration", state == 0 ? "500" : "1", "energy", state == 0 ? "2" : "1", "grid", null, "magic", null,
                    "properties", new JsonObject(), "view", null, "order", state);
            row.addProperty("id", Identity.recipe(row)); recipes.add(row);
        }
    }

    static void fixture(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories, List<JsonObject> views,
                        String paper, String texture, Function<String, String> text) {
        NBTTagCompound memory = new NBTTagCompound();
        memory.setLong("energy", 9007199254740993L); memory.setInteger("mark", 0);
        NBTTagCompound title = new NBTTagCompound(); title.setString("Name", "Retained custom name"); memory.setTag("display", title);
        memory.setTag("values", list(new NBTTagLong(Long.MAX_VALUE), new NBTTagLong(Long.MIN_VALUE)));
        String remembered = item(items, "minecraft:paper", 3, memory, false, "Memory 记忆纸", texture, text);
        NBTTagCompound plainMark = new NBTTagCompound(); plainMark.setInteger("mark", 1);
        String marked = item(items, "minecraft:paper", 0, plainMark, false, "Marked paper 印记纸", texture, text);
        NBTTagCompound markedMemory = copy(memory); markedMemory.setInteger("mark", 1);
        String markedRemembered = item(items, "minecraft:paper", 3, markedMemory, false, "Marked memory 铭记纸", texture, text);

        JsonArray elements = array(object("kind", "slot", "direction", "input", "substance", "item", "slot", 0, "x", 32, "y", 20, "width", 16, "height", 16, "z", 1),
                object("kind", "slot", "direction", "input", "substance", "item", "slot", 1, "x", 8, "y", 54, "width", 16, "height", 16, "z", 1),
                object("kind", "slot", "direction", "output", "substance", "item", "slot", 0, "x", 112, "y", 20, "width", 16, "height", 16, "z", 1),
                object("kind", "cost", "index", 0, "x", 64, "y", 54, "width", 16, "height", 16, "z", 2));
        JsonObject view = object("width", 176, "height", 85, "elements", elements);
        String viewId = Identity.content("view", view); view.addProperty("id", viewId); views.add(view);
        recipe(recipes, categories, "imprint", "Tag infusion 标签注魔", Arrays.asList(paper, remembered),
                Arrays.asList(marked, markedRemembered), object("kind", "patch", "set", object("mark", TypedNbt.encode(new NBTTagInt(1))), "limits", new JsonObject()), paper, viewId, text);

        String armor = item(items, "fixture:armor", 7, null, true, "Armor blank 空白护甲", texture, text);
        NBTTagCompound input = new NBTTagCompound();
        input.setLong("energy", 9007199254740993L);
        title = new NBTTagCompound(); title.setString("Name", "Retained armor name"); title.setInteger("Color", 8); input.setTag("display", title);
        input.setTag("ench", list(enchantment(16, 2))); input.setTag("notes", list(new NBTTagString("source")));
        input.setInteger("conflict", 8); input.setTag("empty", list(new NBTTagInt(3)));
        String named = item(items, "fixture:armor", 7, input, true, "Armor named 铭名护甲", texture, text);
        NBTTagCompound mold = new NBTTagCompound();
        title = new NBTTagCompound(); title.setInteger("Color", 9); mold.setTag("display", title);
        mold.setTag("ench", list(enchantment(0, 1))); mold.setTag("notes", list(new NBTTagString("base")));
        mold.setString("conflict", "keep"); mold.setTag("empty", new NBTTagList());
        String base = item(items, "fixture:robe", 2, mold, true, "Robe mold 产物模板", texture, text);
        String bare = item(items, "fixture:robe", 2, null, true, "Robe blank 空白法袍", texture, text);
        NBTTagCompound merged = copy(mold);
        merged.setLong("energy", 9007199254740993L); merged.getCompoundTag("display").setString("Name", "Retained armor name");
        merged.setTag("ench", list(enchantment(0, 1), enchantment(16, 2)));
        merged.setTag("notes", list(new NBTTagString("base"), new NBTTagString("source")));
        String inherited = item(items, "fixture:robe", 2, merged, true, "Robe inherited 继承法袍", texture, text);
        String copied = item(items, "fixture:robe", 7, input, true, "Robe copied 记忆法袍", texture, text);
        NBTTagCompound filtered = copy(mold); filtered.getCompoundTag("display").setString("Name", "Retained armor name");
        String selective = item(items, "fixture:robe", 2, filtered, true, "Robe filtered 筛选法袍", texture, text);
        List<String> centers = Arrays.asList(armor, named, remembered);
        recipe(recipes, categories, "inherit", "NBT inheritance 数据继承", centers, Arrays.asList(base, inherited, base),
                object("kind", "merge", "base", stack(base), "keys", null, "tools", true), paper, viewId, text);
        recipe(recipes, categories, "copy", "NBT copy 数据传承", centers, Arrays.asList(bare, copied, bare),
                object("kind", "merge", "base", stack(bare), "keys", null, "tools", true), paper, viewId, text);
        recipe(recipes, categories, "filter", "NBT filter 定向继承", centers, Arrays.asList(base, selective, base),
                object("kind", "merge", "base", stack(base), "keys", array("display"), "tools", true), paper, viewId, text);
        wands(items, recipes, categories, views, texture, text);
    }

    private static void wands(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                              List<JsonObject> views, String texture, Function<String, String> text) {
        String core = item(items, "fixture:core", 0, null, false, "Wand core 杖芯", texture, text);
        String cap = item(items, "fixture:cap", 0, null, false, "Wand cap 杖端", texture, text);
        String screw = item(items, "fixture:screw", 0, null, false, "Wand screw 螺丝", texture, text);
        String conductor = item(items, "fixture:conductor", 0, null, false, "Wand conductor 导体", texture, text);
        NBTTagCompound first = new NBTTagCompound();
        first.setString("cap", "copper"); first.setString("rod", "greatwood"); first.setInteger("fire", 900); first.setInteger("air", 200);
        NBTTagCompound title = new NBTTagCompound(); title.setString("Name", "Kept wand name"); first.setTag("display", title);
        first.setLong("owner", 9007199254740993L);
        NBTTagCompound second = copy(first); second.setDouble("fire", -3.75); second.setLong("air", 4294967301L);
        second.setTag("focus", list(new NBTTagString("Retained focus")));
        NBTTagCompound staff = copy(first); staff.setString("rod", "old_staff"); staff.setBoolean("sceptre", false);
        NBTTagCompound obsolete = new NBTTagCompound(); obsolete.setString("Name", "Obsolete attack modifier");
        staff.setTag("AttributeModifiers", list(obsolete));
        NBTTagCompound staffCopy = copy(staff); staffCopy.setBoolean("sceptre", true); staffCopy.setInteger("fire", 100);
        List<NBTTagCompound> normal = Arrays.asList(first, second), staffs = Arrays.asList(staff, staffCopy);
        List<String> normalIds = Arrays.asList(item(items, "fixture:wand", 17, first, false, "Charged wand 充能法杖", texture, text),
                item(items, "fixture:wand", 23, second, false, "Focused wand 焦点法杖", texture, text));
        List<String> staffIds = Arrays.asList(item(items, "fixture:wand", 17, staff, false, "Staffter false 零值双用杖", texture, text),
                item(items, "fixture:wand", 23, staffCopy, false, "Staffter true 一值双用杖", texture, text));
        String fire = Identity.origin("aspect", object("owner", "fixture", "handler", "aspects", "key", "fire"));
        String air = Identity.origin("aspect", object("owner", "fixture", "handler", "aspects", "key", "air"));
        for (String mode : Arrays.asList("core", "caps", "staff", "clear", "creative")) {
            boolean staffMode = mode.equals("staff"), rod = mode.equals("core") || staffMode, clear = mode.equals("clear");
            List<NBTTagCompound> data = staffMode ? staffs : normal;
            List<String> centers = staffMode ? staffIds : normalIds;
            String titleText = mode.equals("core") ? "Wand core replacement 更换杖芯" : mode.equals("caps") ? "Wand cap replacement 更换杖端"
                    : staffMode ? "Staff replacement 长杖属性替换" : clear ? "Wand reset 清空充能" : "Creative replacement 创造模式替换";
            JsonObject origin = object("owner", "fixture", "handler", "fixture:wand", "key", "wand_" + mode);
            String category = Identity.origin("category", origin);
            categories.add(object("id", category, "source", origin, "name", text.apply(titleText), "icon", object("kind", "item", "id", centers.get(0)),
                    "machines", new JsonArray(), "view", null, "order", categories.size()));
            NBTTagCompound set = new NBTTagCompound(); set.setString(rod ? "rod" : "cap", rod ? staffMode ? "new_staff" : "silverwood" : "gold");
            if (staffMode) {
                NBTTagCompound attribute = new NBTTagCompound(); attribute.setString("AttributeName", "generic.attackDamage");
                attribute.setString("Name", "Weapon modifier"); attribute.setDouble("Amount", 6); attribute.setInteger("Operation", 0);
                java.util.UUID uuid = java.util.UUID.fromString("cb3f55d3-645c-4f38-a497-9c13a33db5cf");
                attribute.setLong("UUIDMost", uuid.getMostSignificantBits()); attribute.setLong("UUIDLeast", uuid.getLeastSignificantBits());
                set.setTag("AttributeModifiers", list(attribute));
            }
            JsonObject limits = new JsonObject(); int capacity = staffMode ? 600 : rod ? 400 : 800;
            for (String key : Arrays.asList("air", "fire")) {
                if (clear) set.setInteger(key, 0);
                else if (rod) limits.addProperty(key, capacity);
            }
            JsonArray samples = new JsonArray(), choices = new JsonArray();
            for (int index = 0; index < data.size(); index++) {
                NBTTagCompound output = copy(data.get(index));
                for (Object key : set.func_150296_c()) output.setTag((String) key, set.getTag((String) key).copy());
                for (Map.Entry<String, com.google.gson.JsonElement> limit : limits.entrySet()) {
                    output.setInteger(limit.getKey(), Math.min(output.getInteger(limit.getKey()), limit.getValue().getAsInt()));
                }
                String result = item(items, "fixture:wand", index == 0 ? 17 : 23, output, false,
                        "Wand " + mode + " " + (index + 1) + " " + titleText.substring(titleText.indexOf(' ') + 1) + (index == 0 ? "甲" : "乙"), texture, text);
                samples.add(stack(result));
                JsonObject choice = choice(centers.get(index));
                choice.add("rule", object("kind", "tags", "meta", true, "keys", array("cap", "rod"),
                        "present", staffMode ? array("sceptre") : new JsonArray(), "absent", staffMode ? new JsonArray() : array("sceptre")));
                choices.add(choice);
            }
            JsonArray inputs = array(object("slot", 0, "kind", "item", "choices", choices));
            List<String> supplies = new java.util.ArrayList<>(); supplies.add(rod ? core : cap);
            if (rod) {
                for (int count = 0; count < (staffMode ? 2 : 4); count++) supplies.add(screw);
                supplies.add(conductor); supplies.add(conductor);
            } else supplies.add(cap);
            for (String supply : supplies) inputs.add(object("slot", inputs.size(), "kind", "item", "choices", array(choice(supply))));
            JsonArray elements = new JsonArray();
            for (int slot = 0; slot < inputs.size(); slot++) elements.add(object("kind", "slot", "direction", "input", "substance", "item", "slot", slot,
                    "x", 8 + slot % 3 * 20, "y", 8 + slot / 3 * 20, "width", 16, "height", 16, "z", 1));
            elements.add(object("kind", "slot", "direction", "output", "substance", "item", "slot", 0, "x", 112, "y", 28, "width", 16, "height", 16, "z", 1));
            JsonObject view = object("width", 160, "height", 82, "elements", elements);
            String viewId = Identity.content("view", view); view.addProperty("id", viewId);
            if (views.stream().noneMatch(existing -> existing.get("id").getAsString().equals(viewId))) views.add(view);
            JsonObject action = object("kind", "patch", "set", TypedNbt.encode(set).getAsJsonObject().get("value"), "limits", limits);
            JsonObject output = object("slot", 0, "kind", "item", "id", samples.get(0).getAsJsonObject().get("id"), "amount", "1",
                    "quantity", null, "chance", object("numerator", "1", "denominator", "1"), "role", "result", "change", object("input", 0, "action", action, "samples", samples));
            JsonObject payment = staffMode ? null : object("input", 0, "charges", object(air, "air", fire, "fire"), "capacity", capacity, "preserve", !clear);
            JsonObject recipe = object("source", origin, "category", category, "inputs", inputs, "outputs", array(output), "duration", null, "energy", null,
                    "grid", null, "properties", new JsonObject(), "view", viewId, "order", 0,
                    "magic", object("kind", "arcane", "central", null, "instability", null, "research", new JsonArray(), "payment", payment, "creative", true,
                            "aspects", mode.equals("creative") ? new JsonArray() : array(object("aspect", fire, "amount", "2"))));
            recipe.addProperty("id", Identity.recipe(recipe)); recipes.add(recipe);
        }
    }

    private static void recipe(List<JsonObject> recipes, List<JsonObject> categories, String key, String name,
                               List<String> inputs, List<String> outputs, JsonObject action, String paper, String view, Function<String, String> text) {
        JsonObject origin = object("owner", "fixture", "handler", "fixture:" + key, "key", key);
        String category = Identity.origin("category", origin);
        categories.add(object("id", category, "source", origin, "name", text.apply(name), "icon", object("kind", "item", "id", inputs.get(0)),
                "machines", new JsonArray(), "view", null, "order", categories.size()));
        JsonArray choices = new JsonArray(), samples = new JsonArray();
        for (String input : inputs) choices.add(choice(input));
        for (String output : outputs) samples.add(stack(output));
        String aspect = Identity.origin("aspect", object("owner", "fixture", "handler", "aspects", "key", "fire"));
        String research = Identity.origin("research", object("owner", "fixture", "handler", "research", "key", "BASICS"));
        JsonObject output = object("slot", 0, "kind", "item", "id", outputs.get(0), "amount", "1",
                "quantity", null, "chance", object("numerator", "1", "denominator", "1"), "role", "result", "change", object("input", 0, "action", action, "samples", samples));
        JsonObject recipe = object("source", origin, "category", category,
                "inputs", array(object("slot", 0, "kind", "item", "choices", choices), object("slot", 1, "kind", "item", "choices", array(choice(paper)))),
                "outputs", array(output), "duration", null, "energy", null, "grid", null, "properties", new JsonObject(), "view", view, "order", 0,
                "magic", object("kind", "infusion", "central", 0, "instability", 4, "payment", null, "creative", false, "aspects", array(object("aspect", aspect, "amount", "2")),
                        "research", array(object("key", "BASICS", "id", research, "completed", null))));
        recipe.addProperty("id", Identity.recipe(recipe)); recipes.add(recipe);
    }

    private static JsonObject choice(String id) {
        return object("id", id, "amount", "1", "rule", object("kind", "exact"), "consume", object("kind", "consume"), "returns", new JsonArray());
    }
    private static JsonObject stack(String id) { return object("id", id, "amount", "1"); }
    private static String item(List<JsonObject> items, String registry, int meta, NBTTagCompound nbt, boolean armor,
                               String name, String texture, Function<String, String> text) {
        String id = Identity.item(registry, meta, TypedNbt.encode(nbt));
        if (items.stream().noneMatch(existing -> existing.get("id").getAsString().equals(id))) {
            items.add(object("id", id, "registry", registry, "meta", meta, "nbt", TypedNbt.encode(nbt), "name", text.apply(name), "tooltip", new JsonArray(),
                    "stackLimit", armor || registry.equals("fixture:wand") ? 1 : 64, "durability", armor ? 100 : 0, "tools", new JsonObject(), "armor", armor, "tags", new JsonArray(),
                    "icon", texture, "order", null, "aspects", new JsonArray()));
        }
        return id;
    }
    private static NBTTagCompound copy(NBTTagCompound tag) { return (NBTTagCompound) tag.copy(); }
    private static NBTTagList list(NBTBase... tags) {
        NBTTagList result = new NBTTagList(); for (NBTBase tag : tags) result.appendTag(tag); return result;
    }
    private static NBTTagCompound enchantment(int id, int level) {
        NBTTagCompound result = new NBTTagCompound(); result.setShort("id", (short) id); result.setShort("lvl", (short) level); return result;
    }
}
