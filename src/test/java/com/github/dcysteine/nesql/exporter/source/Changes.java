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

    static void soul(List<JsonObject> items,List<JsonObject> recipes,List<JsonObject> categories,String texture,Function<String,String> text){
        JsonObject origin=object("owner","fixture","handler","soul","key","soul");String category=Identity.origin("category",origin);
        String vessel=item(items,"fixture:soul_vial",0,null,false,"Soul vial 灵魂瓶",texture,text);
        NBTTagCompound zombie=new NBTTagCompound();zombie.setString("id","Zombie");
        NBTTagCompound sheep=new NBTTagCompound();sheep.setString("id","Sheep");
        String zv=item(items,"fixture:soul_vial",0,zombie,false,"Zombie soul 僵尸灵魂",texture,text),sv=item(items,"fixture:soul_vial",0,sheep,false,"Sheep soul 绵羊灵魂",texture,text);
        String product=item(items,"fixture:soul_product",0,null,false,"Soul bound product 灵魂绑定产物",texture,text);
        String base=item(items,"fixture:soul_spawner",0,null,false,"Broken spawner 破损刷怪笼",texture,text);
        NBTTagCompound ztag=new NBTTagCompound();ztag.setString("mobType","Zombie");NBTTagCompound stag=new NBTTagCompound();stag.setString("mobType","Sheep");
        String zo=item(items,"fixture:soul_spawner",0,ztag,false,"Zombie bound spawner 僵尸刷怪笼",texture,text),so=item(items,"fixture:soul_spawner",0,stag,false,"Sheep bound spawner 绵羊刷怪笼",texture,text);
        categories.add(object("id",category,"source",origin,"name",text.apply("EnderIO Soul Binder 灵魂绑定机"),"icon",object("kind","item","id",vessel),"machines",array(),"view",null,"order",categories.size()));
        for(int n=0;n<2;n++){
            boolean spawner=n==1;
            JsonObject filter=object("vessel",vessel,"names",spawner?array(null,"Forbidden"):array(null,"Zombie"),"exclude",spawner);
            JsonArray choices=array();for(String id:spawner?new String[]{zv,sv}:new String[]{vessel,zv}){JsonObject c=choice(id);c.add("rule",object("kind","soul","filter",filter));choices.add(c);}
            JsonObject material=choice(spawner?base:vessel);material.add("rule",object("kind","wildcard","meta",spawner,"nbt",true));
            JsonArray inputs=array(object("kind","item","slot",0,"choices",choices),object("kind","item","slot",1,"choices",array(material)));
            JsonArray outputs=array();
            outputs.add(object("kind","item","slot",0,"id",vessel,"amount",spawner?"1":null,"change",null,"role","result","chance",Chance.of(1,1),"quantity",spawner?null:object("kind","soul","nominal","1")));
            outputs.add(object("kind","item","slot",1,"id",spawner?zo:product,"amount",spawner?"1":null,"change",spawner?object("input",0,"action",object("kind","soul","base",base),"samples",array(object("id",zo,"amount","1"),object("id",so,"amount","1"))):null,"role","result","chance",Chance.of(1,1),"quantity",spawner?null:object("kind","soul","nominal","2")));
            JsonArray earlier=spawner?array(object("soul",object("vessel",vessel,"names",array(null,"Zombie"),"exclude",false),"material",object("id",vessel,"rule",object("kind","wildcard","meta",false,"nbt",true)))):array();
            JsonObject row=object("source",origin,"category",category,"order",n,"inputs",inputs,"outputs",outputs,"process",object("kind","soul","energy",1000,"levels",16,"experience",272,"capacity",825,"drains",spawner,"spawner",spawner,"earlier",earlier),"duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",new JsonObject());
            row.addProperty("id",Identity.recipe(row));recipes.add(row);
        }
    }

    static void sag(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                    String texture, Function<String,String> text) {
        JsonObject origin=object("owner","fixture","handler","sag","key","sag");
        String category=Identity.origin("category",origin);
        String material=item(items,"fixture:sag_input",0,null,false,"SAG input 磨粉材料",texture,text);
        String result=item(items,"fixture:sag_output",0,null,false,"SAG output 磨粉产物",texture,text);
        String ball=item(items,"fixture:sag_ball",0,null,false,"SAG ball 研磨珠",texture,text);
        String prior=item(items,"fixture:sag_input",1,null,false,"SAG earlier 前序材料",texture,text);
        categories.add(object("id",category,"source",origin,"name",text.apply("EnderIO SAG Mill 磨粉机"),"icon",object("kind","item","id",material),"machines",array(),"view",null,"order",categories.size()));
        JsonArray inputs=array();
        for(int i=0;i<2;i++){
            JsonObject c=choice(i==0?material:ball);c.add("rule",object("kind","wildcard","meta",i==0,"nbt",true));c.add("consume",object("kind",i==0?"allocated":"reserve"));
            inputs.add(object("kind","item","slot",i,"choices",array(c)));
        }
        JsonObject ballCase=object("id",ball,"rule",object("kind","wildcard","meta",false,"nbt",true));
        JsonObject priorCase=object("id",prior,"rule",object("kind","wildcard","meta",false,"nbt",true));
        JsonObject process=object("kind","sag","energy",1000,"slot",-1,"bonus",true,
            "earlier",array(object("amount","2","choices",array(priorCase))),
            "balls",array(object("choices",array(ballCase),"grinding","2.5","chance","2.0","power","0.5","duration",10000)),
            "blocked",array(priorCase),"oreBlocked",array(priorCase));
        JsonObject output=object("kind","item","slot",0,"id",result,"amount",null,"change",null,"role","result","chance",Chance.of(1,1),"quantity",object("kind","grinding","nominal","2","threshold","0.5"));
        JsonObject row=object("source",origin,"category",category,"order",0,"inputs",inputs,"outputs",array(output),"process",process,"duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",new JsonObject());
        row.addProperty("id",Identity.recipe(row));recipes.add(row);
    }

    static void splice(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                       String texture, Function<String,String> text) {
        JsonObject origin=object("owner","fixture","handler","splice","key","splice");
        String category=Identity.origin("category",origin);
        String material=item(items,"fixture:splice_input",0,null,false,"Splice input 装配材料",texture,text);
        String result=item(items,"fixture:splice_output",0,null,false,"Splice output 头颅装配产物",texture,text);
        String axe=item(items,"fixture:splice_axe",0,null,false,"Splice axe 装配斧头",texture,text);
        String shears=item(items,"fixture:splice_shears",0,null,false,"Splice shears 装配剪刀",texture,text);
        categories.add(object("id",category,"source",origin,"name",text.apply("EnderIO Slice and Splice 头颅装配机"),"icon",object("kind","item","id",material),"machines",array(),"view",null,"order",categories.size()));
        JsonArray inputs=array();
        for(int i=0;i<4;i++){
            JsonObject c=choice(i<2?material:i==2?axe:shears);
            c.add("consume",object("kind",i<2?"allocated":"wear"));c.add("rule",object("kind","wildcard","meta",i>=2,"nbt",true));
            inputs.add(object("kind","item","slot",i<2?i:i+4,"choices",array(c)));
        }
        JsonObject output=object("kind","item","slot",0,"id",result,"amount",null,"change",null,"role","result","chance",Chance.of(1,1),"quantity",object("kind","sharedRoll","nominal","1","threshold","1.0"));
        JsonObject row=object("source",origin,"category",category,"order",0,"inputs",inputs,"outputs",array(output),"process",object("kind","splice","energy",2000,"slots",array(5,0)),"duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",new JsonObject());
        row.addProperty("id",Identity.recipe(row));recipes.add(row);
    }

    static void alloy(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                      String texture, Function<String,String> text) {
        JsonObject origin=object("owner","fixture","handler","alloy","key","alloy");
        String category=Identity.origin("category",origin);
        String iron=item(items,"fixture:alloy_input",0,null,false,"Alloy input 合金材料",texture,text);
        String gold=item(items,"fixture:alloy_output",0,null,false,"Alloy output 合金产物",texture,text);
        String rare=item(items,"fixture:alloy_output",1,null,false,"Alloy rare 稀有产物",texture,text);
        categories.add(object("id",category,"source",origin,"name",text.apply("EnderIO Alloy Smelter 合金炉"),"icon",object("kind","item","id",iron),"machines",array(),"view",null,"order",categories.size()));
        JsonArray inputs=array(),outputs=array();
        for(int i=0;i<2;i++){
            JsonObject c=choice(iron);c.addProperty("amount",Integer.toString(i+1));c.add("consume",object("kind","allocated"));
            c.add("rule",object("kind","wildcard","meta",false,"nbt",true));inputs.add(object("kind","item","slot",i,"choices",array(c)));
            outputs.add(object("kind","item","slot",i,"id",i==0?gold:rare,"amount",null,"change",null,"role","result","chance",Chance.of(1,1),
                "quantity",object("kind","sharedRoll","nominal",Integer.toString(i+2),"threshold",i==0?"0.5":"0.0")));
        }
        JsonObject row=object("source",origin,"category",category,"order",0,"inputs",inputs,"outputs",outputs,"process",object("kind","alloy","energy",1200,"slots",array(1,0)),
            "duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",new JsonObject());
        row.addProperty("id",Identity.recipe(row));recipes.add(row);
    }

    static void vat(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories,
                    String texture, Function<String,String> text) {
        JsonObject origin=object("owner","fixture","handler","vat","key","vat");
        String category=Identity.origin("category",origin);
        String left=item(items,"fixture:vat_reagent",0,null,false,"Vat reagent 储液罐反应物",texture,text);
        String right=item(items,"fixture:vat_reagent",1,null,false,"Vat catalyst 储液罐催化物",texture,text);
        String extra=item(items,"fixture:vat_optional",0,null,false,"Vat optional 额外材料",texture,text);
        categories.add(object("id",category,"source",origin,"name",text.apply("EnderIO Vat 末影接口储液罐"),"icon",object("kind","item","id",left),"machines",array(),"view",null,"order",categories.size()));
        for(int mode=0;mode<3;mode++) {
            JsonArray inputs=array(), rules=array();
            for(int slot=0;slot<(mode==0?2:1);slot++) {
                JsonObject c=choice(slot==0?left:right);c.addProperty("amount",slot==0?"3":"5");
                c.add("consume",object("kind","upto"));c.add("rule",object("kind","wildcard","meta",false,"nbt",true));
                JsonArray alternatives=array(c);
                if(slot==0) {JsonObject kept=choice(right);kept.add("consume",object("kind","keep"));kept.add("rule",object("kind","wildcard","meta",false,"nbt",true));alternatives.add(kept);}
                inputs.add(object("kind","item","slot",slot,"choices",alternatives));
            }
            JsonObject fluid=choice(Identity.fluid("water",null));fluid.addProperty("amount","1375");
            inputs.add(object("kind","fluid","slot",0,"choices",array(fluid)));
            if(mode!=0) {rules.add(object("id",left,"rule",object("kind","wildcard","meta",false,"nbt",true),"amount",3));rules.add(object("id",extra,"rule",object("kind","wildcard","meta",true,"nbt",true),"amount",-1));}
            JsonArray outputs=mode==2?array():array(object("slot",0,"kind","fluid","id",Identity.fluid("honey",null),"amount","1788","quantity",null,"chance",Chance.of(1,1),"role","result","change",null));
            JsonObject row=object("source",origin,"category",category,"order",mode,"inputs",inputs,"outputs",outputs,
                "process",object("kind","vat","energy",1200,"extra",rules,"zeroOutput",mode==2?Identity.fluid("honey",null):null),
                "duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",new JsonObject());
            row.addProperty("id",Identity.recipe(row));recipes.add(row);
        }
    }

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

    static void rolling(List<JsonObject> items, List<JsonObject> recipes, List<JsonObject> categories, String texture, Function<String, String> text) {
        String stone = Identity.item("minecraft:stone", 0, null), paper = Identity.item("minecraft:paper", 0, null);
        String priorItem = item(items, "fixture:rolling_prior", 0, null, false, "Earlier rolling template", texture, text);
        JsonObject origin = object("owner", "fixture", "handler", "fixture:rolling", "key", "rolling");
        String category = Identity.origin("category", origin);
        categories.add(object("id", category, "source", origin, "name", text.apply("Rolling machine 轧制机"), "icon", object("kind","item","id",stone),
                "machines", array(), "view", null, "order", categories.size()));
        JsonObject priorGrid = object("width",2,"height",1,"cells",array(0,null),"mirror",true);
        JsonObject predicate = object("kind","wildcard","meta",false,"nbt",true);
        for (boolean shaped : new boolean[] {true, false}) {
            JsonObject first = choice(stone), second = choice(paper); first.add("rule",predicate); second.add("rule",predicate);
            JsonObject recipe = object("source",origin,"category",category,
                    "inputs",array(object("slot",0,"kind","item","choices",array(first)),object("slot",1,"kind","item","choices",array(second))),
                    "outputs",array(object("slot",0,"kind","item","id",paper,"amount","2","quantity",null,"chance",object("numerator","1","denominator","1"),"role","result","change",null)),
                    "duration",null,"energy",null,"grid",shaped ? object("width",2,"height",2,"cells",array(0,null,null,1),"mirror",false) : null,
                    "properties",object(),"magic",null,"view",null,"order",shaped ? 0 : 1,
                    "process",object("kind","rolling","powered",shaped,"earlier",array(object("grid",priorGrid,"inputs",array(array(object("id",priorItem,"rule",predicate)))))));
            recipe.addProperty("id", Identity.recipe(recipe)); recipes.add(recipe);
        }
    }

    static void buildcraft(List<JsonObject> recipes, List<JsonObject> categories, Function<String,String> text) {
        String stone=Identity.item("minecraft:stone",0,null), paper=Identity.item("minecraft:paper",0,null);
        JsonObject origin=object("owner","fixture","handler","fixture:buildcraft","key","assembly");
        String category=Identity.origin("category",origin);
        categories.add(object("id",category,"source",origin,"name",text.apply("BuildCraft Assembly 激光装配台"),"icon",object("kind","item","id",stone),"machines",array(),"view",null,"order",categories.size()));
        JsonObject first=choice(stone), second=choice(paper), last=choice(paper);
        for(JsonObject c:Arrays.asList(first,second,last)) {
            c.add("rule",object("kind","buildcraft","wildcard",c==second,"subtypes",c==first));
            c.add("consume",object("kind","allocated"));c.addProperty("amount",c==last?"1":"3");
        }
        JsonObject recipe=object("source",origin,"category",category,"order",0,
            "inputs",array(object("kind","item","slot",0,"choices",array(first,second)),object("kind","item","slot",1,"choices",array(last))),
            "outputs",array(object("kind","item","slot",0,"id",paper,"amount","2","quantity",null,"change",null,"role","result","chance",Chance.of(1,1))),
            "process",object("kind","buildcraftAssembly","energy",700),"duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",object());
        recipe.addProperty("id",Identity.recipe(recipe));recipes.add(recipe);
    }

    static void refining(List<JsonObject> recipes,List<JsonObject> categories,Function<String,String> text) {
        String water=Identity.fluid("water",null),honey=Identity.fluid("honey",null);
        JsonObject origin=object("owner","fixture","handler","fixture:refining","key","refining");
        String category=Identity.origin("category",origin);
        categories.add(object("id",category,"source",origin,"name",text.apply("BuildCraft Refinery 精炼厂"),"icon",object("kind","fluid","id",water),"machines",array(),"view",null,"order",categories.size()));
        JsonArray inputs=new JsonArray();
        for(int slot=0;slot<2;slot++) {JsonObject c=choice(water);c.addProperty("amount","700");c.add("consume",object("kind","allocated"));inputs.add(object("kind","fluid","slot",slot,"choices",array(c)));}
        JsonObject recipe=object("source",origin,"category",category,"order",0,"inputs",inputs,
            "outputs",array(object("kind","fluid","slot",0,"id",honey,"amount","50","quantity",null,"change",null,"role","result","chance",Chance.of(1,1))),
            "process",object("kind","buildcraftRefinery","energy",30,"delay","5","capacity",4000,
                "earlier",array(array(object("id",Identity.fluid("hydrogen",null),"amount","200"))),"filling",array(array(Identity.fluid("helium",null),water),array(water))),
            "duration",null,"energy",null,"grid",null,"magic",null,"view",null,"properties",object());
        recipe.addProperty("id",Identity.recipe(recipe));recipes.add(recipe);
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
