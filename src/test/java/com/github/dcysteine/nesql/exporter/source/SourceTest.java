package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.github.dcysteine.nesql.exporter.source.Json.object;
import static com.github.dcysteine.nesql.exporter.source.Json.uint;
import static com.github.dcysteine.nesql.exporter.source.Json.array;
import static com.github.dcysteine.nesql.exporter.source.Json.value;

/** One cross-language source fixture plus lossless identity and publication checks. */
public final class SourceTest {
    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Files.createTempDirectory("nesql-source-") : Paths.get(args[0]);
        Files.createDirectories(root);
        identities();
        probabilities();
        geometry();
        com.github.dcysteine.nesql.exporter.capture.ModelsTest.run();
        com.github.dcysteine.nesql.exporter.capture.UiTest.run();
        com.github.dcysteine.nesql.exporter.capture.ProductsTest.run();
        com.github.dcysteine.nesql.exporter.capture.EnvironmentTest.run(root.resolve("environment"));
        com.github.dcysteine.nesql.exporter.capture.StudiesTest.run();
        sorting(root);
        Jobs.Result first = fixture(root.resolve("first"), root.resolve("datasets"));
        Jobs.Result repeated = fixture(root.resolve("repeat"), root.resolve("datasets"));
        require(first.id.equals(repeated.id), "Identical source content changed its id");
        require(!Files.exists(root.resolve("repeat")), "Repeated publication left staging files");
        JsonObject manifest = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(first.path).resolve("manifest.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        require(manifest.get("format").getAsString().equals(Dataset.FORMAT), "Wrong source format");
        require(manifest.getAsJsonArray("files").size() == Dataset.COLLECTIONS.size() + 2, "Missing environment, record or asset descriptors");
        JsonObject original = new JsonParser().parse(manifest.toString()).getAsJsonObject();
        original.remove("id");
        require(CanonicalJson.digest(original).equals(first.id), "Manifest id does not cover its content");

        try (Dataset dataset = new Dataset(root.resolve("invalid"), "fixture", environment(), false)) {
            Dataset.Records records = dataset.records("examples");
            records.write(object("id", "b"));
            expectFailure(() -> records.write(object("id", "a")));
            expectFailure(() -> records.write(object("id", "b", "different", true)));
        }
        require(!Files.exists(root.resolve("invalid")), "Aborted source left a staging directory");
        System.out.println(first.path);
    }

    public static Jobs.Result fixture(Path staging, Path datasets) throws Exception {
        return fixture(staging, datasets, "fixture");
    }

    public static Jobs.Result fixture(Path staging, Path datasets, String producer) throws Exception {
        Path image = Files.createTempFile(staging.getParent(), "source-icon-", ".png");
        BufferedImage pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xff706050);
        pixels.setRGB(1, 0, 0xff807060);
        pixels.setRGB(0, 1, 0xff908070);
        pixels.setRGB(1, 1, 0xffa09080);
        ImageIO.write(pixels, "png", image.toFile());
        try (Dataset dataset = new Dataset(staging, producer, environment(), true)) {
            String iconPath = dataset.asset(image, "png");
            JsonObject assetRecord = object("path", iconPath, "width", 2, "height", 2,
                    "frames", new JsonArray(), "interpolate", false, "source", object("kind", "capture", "location", "fixture"));
            String asset = Identity.content("asset", assetRecord);
            assetRecord.addProperty("id", asset);
            String stone = Identity.item("minecraft:stone", 0, JsonNull.INSTANCE);
            String paper = Identity.item("minecraft:paper", 0, JsonNull.INSTANCE);
            String water = Identity.fluid("water", JsonNull.INSTANCE);
            String light = Identity.origin("aspect", object("owner", "fixture", "handler", "aspects", "key", "light"));
            java.util.TreeMap<String, JsonObject> strings = new java.util.TreeMap<>();
            java.util.function.Function<String, String> text = value -> {
                JsonObject record = object("locale", "en_US", "text", value);
                String id = Identity.content("text", record);
                record.addProperty("id", id);
                strings.put(id, record);
                return id;
            };
            java.util.List<JsonObject> items = new java.util.ArrayList<>(Arrays.asList(object("id", stone, "registry", "minecraft:stone", "meta", 0,
                    "nbt", JsonNull.INSTANCE, "name", text.apply("Stone 石头"), "tooltip", new JsonArray(), "stackLimit", 64,
                    "durability", 0, "tools", new JsonObject(), "tags", new JsonArray(), "icon", asset, "order", 0,
                    "aspects", array(object("aspect", light, "amount", "3"))),
                    object("id", paper, "registry", "minecraft:paper", "meta", 0, "nbt", null, "name", text.apply("Paper 纸"),
                            "tooltip", new JsonArray(), "stackLimit", 64, "durability", 0, "tools", new JsonObject(), "tags", array("paper"),
                            "icon", asset, "order", 1, "aspects", new JsonArray())));
            dataset.records("fluids").write(object("id", water, "registry", "water", "nbt", JsonNull.INSTANCE,
                    "name", text.apply("Water"), "temperature", 300, "density", 1000, "viscosity", 1000,
                    "luminosity", 0, "gaseous", false, "icon", asset));
            JsonObject origin = object("owner", "fixture", "handler", "fixture:machine", "key", "machine");
            recipes(dataset, stone, paper, water, asset, text, items);
            for (JsonObject item : items) if (!item.has("armor")) item.addProperty("armor", false);
            records(dataset, "items", items);
            JsonArray members = new JsonArray();
            members.add(new JsonPrimitive(stone));
            dataset.records("groups").write(object("id", Identity.origin("group", origin), "source", origin, "name", JsonNull.INSTANCE,
                    "members", members, "representative", stone, "collapsed", true, "order", 0));
            dataset.records("assets").write(assetRecord);
            JsonObject baseSource = object("owner", "fixture", "handler", "materials", "key", "base");
            String base = Identity.origin("material", baseSource);
            JsonObject alloySource = object("owner", "fixture", "handler", "materials", "key", "alloy");
            String alloy = Identity.origin("material", alloySource);
            JsonArray parts = new JsonArray();
            parts.add(object("key", "plate", "target", object("kind", "item", "id", stone), "content", object("numerator", "1", "denominator", "9")));
            parts.add(object("key", "fluid", "target", object("kind", "fluid", "id", water), "content", JsonNull.INSTANCE));
            JsonArray components = new JsonArray();
            components.add(object("material", base, "amount", "9007199254740993"));
            java.util.TreeMap<String, JsonObject> materials = new java.util.TreeMap<>();
            materials.put(base, object("id", base, "source", baseSource, "name", text.apply("Fixture base 基材"), "formula", "X", "color", uint(0xff706050L),
                    "components", new JsonArray(), "parts", new JsonArray()));
            materials.put(alloy, object("id", alloy, "source", alloySource, "name", text.apply("Fixture alloy 合金"), "formula", "X9", "color", uint(0xff807060L),
                    "components", components, "parts", parts));
            Dataset.Records materialRows = dataset.records("materials");
            for (JsonObject material : materials.values()) materialRows.write(material);
            JsonObject circuitSource = object("owner", "fixture", "handler", "circuits", "key", "line");
            JsonArray steps = new JsonArray();
            steps.add(object("item", stone, "tier", object("level", 1, "name", text.apply("LV"), "voltage", "32")));
            dataset.records("circuits").write(object("id", Identity.origin("circuit", circuitSource), "source", circuitSource,
                    "name", text.apply("Fixture circuit 电路"), "kind", "line", "boards", new JsonArray(), "steps", steps, "order", 0));
            genetics(dataset, stone, text);
            structures(dataset, stone, asset, text);
            magic(dataset, stone, asset, text);
            Dataset.Records textRecords = dataset.records("strings");
            for (JsonObject record : strings.values()) textRecords.write(record);
            dataset.seal();
            return dataset.prepare(datasets).call();
        } finally { Files.delete(image); }
    }

    private static void records(Dataset dataset, String kind, java.util.List<JsonObject> rows) throws Exception {
        rows.sort(java.util.Comparator.comparing(row -> row.get("id").getAsString()));
        Dataset.Records writer = dataset.records(kind);
        for (JsonObject row : rows) writer.write(row);
    }

    private static void recipes(Dataset dataset, String stone, String paper, String water, String texture,
                                java.util.function.Function<String, String> text, java.util.List<JsonObject> items) throws Exception {
        java.util.List<JsonObject> recipes = new java.util.ArrayList<>(), categories = new java.util.ArrayList<>(), views = new java.util.ArrayList<>();
        String aspect = Identity.origin("aspect", object("owner", "fixture", "handler", "aspects", "key", "fire"));
        String research = Identity.origin("research", object("owner", "fixture", "handler", "research", "key", "BASICS"));
        String[] kinds = {"machine", "arcane", "shapeless", "crucible", "infusion"};
        String[] names = {"Fixture machine", "Arcane 奥术合成", "Shapeless 无序奥术", "Crucible 坩埚炼金", "Infusion 注魔"};
        for (int order = 0; order < kinds.length; order++) {
            String kind = kinds[order]; boolean machine = order == 0, infusion = order == 4;
            String item = machine ? stone : paper;
            JsonObject origin = object("owner", "fixture", "handler", "fixture:" + kind, "key", kind);
            String category = Identity.origin("category", origin);
            JsonArray inputs = new JsonArray(), outputs = new JsonArray(), elements = new JsonArray();
            JsonArray choices = array(object("id", item, "amount", machine ? "9007199254740993" : "1", "consume", object("kind", "consume"),
                    "returns", new JsonArray(), "rule", machine ? object("kind", "exact") : object("kind", "tags", "meta", false, "keys", new JsonArray(), "present", new JsonArray(), "absent", new JsonArray())));
            if (!machine) choices.add(object("id", item, "amount", "1", "consume", object("kind", "consume"), "returns", new JsonArray(),
                    "rule", object("kind", "ore", "name", "paper", "exclusive", true)));
            inputs.add(object("slot", 0, "kind", "item", "choices", choices));
            outputs.add(object("slot", 0, "kind", machine ? "fluid" : "item", "id", machine ? water : paper,
                    "amount", machine ? "1000" : "2", "chance", object("numerator", "1", "denominator", machine ? "3" : "1"), "role", "result", "change", null));
            elements.add(object("kind", "slot", "direction", "input", "substance", "item", "slot", 0, "x", 8, "y", 20, "width", 18, "height", 18, "z", 1));
            elements.add(object("kind", "slot", "direction", "output", "substance", machine ? "fluid" : "item", "slot", 0,
                    "x", 110, "y", 20, "width", 18, "height", 18, "z", 1));
            if (machine) {
                JsonObject track = object("frames", array(object("ticks", 4, "areas", new JsonArray()),
                            object("ticks", 4, "areas", array(array("0.0", "0.0", "0.5", "1.0"))),
                            object("ticks", 4, "areas", array(array("0.5", "0.0", "1.0", "0.5"), array("0.0", "0.5", "0.5", "1.0"))),
                            object("ticks", 4, "areas", array(array("0.0", "0.0", "1.0", "1.0")))));
                String trackId = Identity.content("track", track); track.addProperty("id", trackId); dataset.records("tracks").write(track);
                elements.add(object("kind", "clip", "asset", texture, "x", 48, "y", 20, "width", 48, "height", 18, "z", 0, "track", trackId));
                elements.add(object("kind", "clip", "asset", texture, "x", 48, "y", 42, "width", 48, "height", 18, "z", 0, "track", trackId));
            }
            if (!machine) elements.add(object("kind", "cost", "index", 0, "x", 54, "y", 50, "width", 16, "height", 16, "z", 2));
            JsonObject view = object("width", 176, "height", 85, "elements", elements);
            String viewId = Identity.content("view", view); view.addProperty("id", viewId); views.add(view);
            categories.add(object("id", category, "name", text.apply(names[order]), "source", origin, "icon", object("kind", "item", "id", item),
                    "machines", new JsonArray(), "view", null, "order", order));
            JsonObject recipe = object("category", category, "source", origin, "inputs", inputs, "outputs", outputs,
                    "duration", machine ? "20" : null, "energy", machine ? "9223372036854775807" : null,
                    "grid", order == 1 ? object("width", 2, "height", 2, "cells", array(0, null, null, null), "mirror", true) : null,
                    "magic", machine ? null : object("kind", order <= 2 ? "arcane" : kind, "payment", null, "creative", false, "aspects", array(object("aspect", aspect, "amount", "7")),
                            "research", array(object("key", "BASICS", "id", research, "completed", null), object("key", "@fixture_scanned", "id", null, "completed", false)),
                            "instability", infusion ? 5 : null, "central", infusion ? 0 : null),
                    "properties", new JsonObject(), "view", viewId, "order", 0);
            recipe.addProperty("id", Identity.recipe(recipe)); recipes.add(recipe);
        }
        Changes.fixture(items, recipes, categories, views, paper, texture, text);
        records(dataset, "recipes", recipes); records(dataset, "categories", categories);
        java.util.TreeMap<String, JsonObject> unique = new java.util.TreeMap<>();
        for (JsonObject view : views) unique.put(view.get("id").getAsString(), view);
        records(dataset, "views", new java.util.ArrayList<>(unique.values()));
    }

    private static void magic(Dataset dataset, String item, String image, java.util.function.Function<String, String> text) throws Exception {
        java.util.TreeMap<String, JsonObject> aspects = new java.util.TreeMap<>();
        String[] ids = new String[3];
        String[] keys = {"air", "fire", "light"}, names = {"Aer 气", "Ignis 火", "Lux 光"};
        for (int index = 0; index < keys.length; index++) {
            JsonObject source = object("owner", "fixture", "handler", "aspects", "key", keys[index]);
            ids[index] = Identity.origin("aspect", source);
            aspects.put(ids[index], object("id", ids[index], "source", source, "name", text.apply(names[index]),
                    "description", text.apply("Fixture aspect " + keys[index]), "color", uint(0xffb8a455L),
                    "components", index == 2 ? array(ids[0], ids[1]) : new JsonArray(), "icon", image,
                    "discovered", index == 0 ? null : index == 1));
        }
        Dataset.Records aspectRows = dataset.records("aspects");
        for (JsonObject aspect : aspects.values()) aspectRows.write(aspect);
        JsonObject base = object("owner", "fixture", "handler", "research", "key", "BASICS");
        String baseId = Identity.origin("research", base);
        java.util.TreeMap<String, JsonObject> studies = new java.util.TreeMap<>();
        for (int index = 0; index < 2; index++) {
            JsonObject source = index == 0 ? base : object("owner", "fixture", "handler", "research", "key", "ALCHEMY");
            String id = Identity.origin("research", source);
            studies.put(id, object("id", id, "source", source, "name", text.apply(index == 0 ? "Fixture basics 基础" : "Fixture alchemy 炼金"),
                    "text", text.apply("研究定义与当前快照的知识记录。"), "category", "fixture", "categoryName", text.apply("Fixture category 研究"),
                    "position", array(index * 2, -index), "complexity", index, "warp", index,
                    "flags", index == 0 ? array("virtual") : array("hidden", "secondary"), "completed", index == 0 ? null : false,
                    "parents", index == 0 ? new JsonArray() : array(object("key", "BASICS", "id", baseId, "completed", null)),
                    "hiddenParents", index == 0 ? new JsonArray() : array(object("key", "@fixture_scanned", "id", null, "completed", false)),
                    "siblings", new JsonArray(), "aspects", array(object("aspect", ids[index], "amount", "2")),
                    "itemTriggers", index == 0 ? new JsonArray() : array(item), "entityTriggers", index == 0 ? new JsonArray() : array("Cow"),
                    "aspectTriggers", index == 0 ? new JsonArray() : array(ids[2]), "icon", index == 0 ? null : item, "texture", index == 0 ? image : null));
        }
        Dataset.Records researchRows = dataset.records("research");
        for (JsonObject study : studies.values()) researchRows.write(study);
    }

    private static void structures(Dataset dataset, String item, String texture, java.util.function.Function<String, String> text) throws Exception {
        JsonObject source = object("owner", "fixture", "handler", "structures", "key", "furnace");
        JsonArray rules = new JsonArray(), pieces = new JsonArray(), chunks = new JsonArray();
        JsonArray placements = new JsonArray(); placements.add(new JsonPrimitive(item));
        rules.add(object("symbol", "C", "kind", "element", "implementation", "fixture.Casing", "placements", placements));
        rules.add(object("symbol", "-", "kind", "air", "implementation", "fixture.Air", "placements", JsonNull.INSTANCE));
        java.util.TreeMap<String, JsonObject> shapes = new java.util.TreeMap<>();
        int count = 0;
        for (int z = 0; z < 3; z++) {
            JsonArray cells = new JsonArray();
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                if (x == 1 && y == 1 && z == 0) continue;
                JsonArray at = new JsonArray(); at.add(new JsonPrimitive(x)); at.add(new JsonPrimitive(y)); at.add(new JsonPrimitive(z));
                cells.add(object("at", at, "index", x == 1 && y == 1 && z == 1 ? 1 : 0)); count++;
            }
            JsonObject shape = object("cells", cells); String id = Identity.content("shape", shape);
            shape.addProperty("id", id); shapes.put(id, shape); chunks.add(new JsonPrimitive(id));
        }
        JsonArray size = new JsonArray(), anchor = new JsonArray(), anchors = new JsonArray();
        for (int n : new int[] {3, 3, 3}) size.add(new JsonPrimitive(n));
        for (int n : new int[] {1, 1, 0}) anchor.add(new JsonPrimitive(n)); anchors.add(anchor);
        pieces.add(object("name", "main", "size", size, "anchors", anchors, "rules", rules, "chunks", chunks, "cells", count));
        JsonArray description = new JsonArray(); description.add(new JsonPrimitive(text.apply("合成坐标样本：控制器在前，中央留空。")));
        String structure = Identity.origin("structure", source);
        JsonObject casing = object("registry", "minecraft:stone", "meta", 0, "nbt", null, "item", item);
        String casingId = Identity.content("block", casing); casing.addProperty("id", casingId);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "fixture.controller"); tile.setInteger("x", 0); tile.setInteger("y", 64); tile.setInteger("z", 0);
        tile.setLong("energy", 9007199254740993L);
        JsonObject controller = object("registry", "fixture:controller", "meta", 1, "nbt", TypedNbt.encode(tile), "item", item);
        String controllerId = Identity.content("block", controller); controller.addProperty("id", controllerId);
        records(dataset, "blocks", Arrays.asList(casing, controller));
        JsonObject cube = model(texture, 0), slab = model(texture, .5f), panel = model(texture, 0);
        JsonArray glass = new JsonArray();
        for (int corner : new int[] {3, 2, 1, 0}) {
            float x = corner == 0 || corner == 3 ? .15f : .85f, y = corner < 2 ? .15f : .85f;
            glass.add(object("at", array(Float.toString(x), Float.toString(y), "-0.002"), "uv", array(corner == 0 || corner == 3 ? "0.0" : "1.0", corner < 2 ? "0.0" : "1.0"), "color", uint(0x4be8db80L)));
        }
        panel.getAsJsonArray("faces").add(object("texture", texture, "pass", "blend", "vertices", glass));
        JsonObject hidden = object("hidden", true, "faces", new JsonArray());
        java.util.List<JsonObject> models = Arrays.asList(cube, slab, panel, hidden);
        for (JsonObject model : models) model.addProperty("id", Identity.content("model", model));
        records(dataset, "models", models);
        JsonArray variants = new JsonArray();
        java.util.List<JsonObject> builds = new java.util.ArrayList<>();
        for (Probe probe : probes()) {
            int depth = probe.count == 1 ? 3 : 4, total = 0;
            JsonArray builtChunks = new JsonArray();
            for (int z = 0; z < depth; z++) {
                JsonArray built = new JsonArray();
                for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                    if (x == 1 && y == 1 && z > 0 && z < depth - 1) continue;
                    built.add(object("at", array(x, y, z), "index", depth == 4 && x == 2 && y == 2 && z == 3 ? 2 : x == 1 && y == 1 && z == 0 ? 1 : 0)); total++;
                }
                JsonObject shape = object("cells", built); String id = Identity.content("shape", shape);
                shape.addProperty("id", id); shapes.put(id, shape); builtChunks.add(value(id));
            }
            JsonArray palette = array(object("block", casingId, "model", (depth == 3 ? cube : slab).get("id"), "problem", null),
                    object("block", controllerId, "model", panel.get("id"), "problem", null));
            if (depth == 4) palette.add(object("block", casingId, "model", hidden.get("id"), "problem", null));
            JsonObject build = object("structure", structure, "probe", probe.json(), "method", "survival", "result", -1,
                    "size", array(3, 3, depth), "origin", array(-1, 65, 0), "controller", array(1, 1, 0),
                    "palette", palette, "rendered", true,
                    "chunks", builtChunks, "cells", total, "notes", array(text.apply("构建预览不等同于运行时成型判定。")));
            String buildId = Identity.content("build", build); build.addProperty("id", buildId); builds.add(build);
            variants.add(object("probe", probe.json(), "build", buildId, "problem", null));
        }
        records(dataset, "builds", builds);
        Dataset.Records geometry = dataset.records("shapes");
        for (JsonObject shape : shapes.values()) geometry.write(shape);
        dataset.records("structures").write(object("id", structure, "source", source, "name", text.apply("Fixture furnace 多方块"),
                "controller", item, "description", description, "probe", probes().get(0).json(), "pieces", pieces, "variants", variants, "problem", JsonNull.INSTANCE));
    }

    private static JsonObject model(String texture, float top) {
        JsonArray faces = new JsonArray();
        for (int axis = 0; axis < 3; axis++) for (int side = 0; side < 2; side++) {
            JsonArray vertices = new JsonArray();
            for (int step = 0; step < 4; step++) {
                int corner = side == 0 ? 3 - step : step;
                float[] at = new float[3]; at[axis] = side;
                at[(axis + 1) % 3] = corner == 0 || corner == 3 ? 0 : 1;
                at[(axis + 2) % 3] = corner < 2 ? 0 : 1;
                at[1] = top + at[1] * (1 - top);
                vertices.add(object("at", array(Float.toString(at[0]), Float.toString(at[1]), Float.toString(at[2])),
                        "uv", array(corner == 0 || corner == 3 ? "0.0" : "1.0", corner < 2 ? "0.0" : "1.0"), "color", uint(0xffffffffL)));
            }
            faces.add(object("texture", texture, "pass", "solid", "vertices", vertices));
        }
        return object("hidden", false, "faces", faces);
    }

    private static void geometry() {
        net.minecraft.client.model.ModelBase base = new net.minecraft.client.model.ModelBase() {};
        net.minecraft.client.model.ModelRenderer parent = new net.minecraft.client.model.ModelRenderer(base, 0, 0);
        parent.addBox(0, 0, 0, 16, 8, 4); parent.setRotationPoint(16, 0, 0);
        net.minecraft.client.model.ModelRenderer child = new net.minecraft.client.model.ModelRenderer(base, 0, 0);
        child.addBox(0, 0, 0, 4, 4, 4); child.setRotationPoint(0, 8, 0); parent.addChild(child);
        Geometry pose = new Geometry().move(0, 1, 1).scale(1, -1, -1);
        JsonArray translated = Geometry.boxes(Arrays.asList(parent), pose, 1f / 16);
        require(translated.size() == 12, "Model hierarchy lost a box");
        bounds(translated, new float[] {1, 0, 0}, new float[] {2, .75f, .25f});
        parent.rotateAngleZ = (float) Math.PI / 2;
        bounds(Geometry.boxes(Arrays.asList(parent), pose, 1f / 16), new float[] {.25f, 0, 0}, new float[] {1, 1, .25f});
        child.isHidden = true;
        require(Geometry.boxes(Arrays.asList(parent), pose, 1f / 16).size() == 6, "Hidden model child was emitted");
        child.isHidden = false; child.addChild(parent);
        try { Geometry.boxes(Arrays.asList(parent), pose, 1f / 16); throw new AssertionError("Cyclic model was accepted"); }
        catch (IllegalArgumentException expected) { /* rejects cyclic hierarchy */ }
        net.minecraft.client.model.ModelChest chest = new net.minecraft.client.model.ModelChest();
        JsonArray closed = Geometry.boxes(Arrays.asList(chest.chestLid, chest.chestBelow, chest.chestKnob), pose, 1f / 16);
        require(closed.size() == 18, "Native chest definition was not captured");
        chest.chestLid.rotateAngleX = -(float) Math.PI / 2;
        JsonArray opened = Geometry.boxes(Arrays.asList(chest.chestLid), pose, 1f / 16);
        boolean above = false;
        for (com.google.gson.JsonElement face : opened) for (com.google.gson.JsonElement vertex : face.getAsJsonArray()) {
            if (Float.parseFloat(vertex.getAsJsonObject().getAsJsonArray("at").get(1).getAsString()) < 0) above = true;
        }
        require(above, "Chest lid rotation did not extend above the body");
    }

    private static void bounds(JsonArray faces, float[] expectedMin, float[] expectedMax) {
        float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (com.google.gson.JsonElement face : faces) for (com.google.gson.JsonElement vertex : face.getAsJsonArray()) {
            JsonArray at = vertex.getAsJsonObject().getAsJsonArray("at");
            for (int axis = 0; axis < 3; axis++) { float value = Float.parseFloat(at.get(axis).getAsString()); min[axis] = Math.min(min[axis], value); max[axis] = Math.max(max[axis], value); }
        }
        for (int axis = 0; axis < 3; axis++) require(Math.abs(min[axis] - expectedMin[axis]) < .00001f && Math.abs(max[axis] - expectedMax[axis]) < .00001f, "Model hierarchy transform changed its bounds");
    }

    private static void genetics(Dataset dataset, String item, java.util.function.Function<String, String> text) throws Exception {
        java.util.TreeMap<String, JsonObject> species = new java.util.TreeMap<>();
        String[] ids = new String[3];
        JsonArray genes = new JsonArray();
        genes.add(object("key", "flower_provider", "allele", "fixture.flowers", "name", text.apply("Fixture flowers"), "dominant", true, "value", JsonNull.INSTANCE));
        genes.add(object("key", "speed", "allele", "fixture.speed", "name", text.apply("Fixture speed"), "dominant", true,
                "value", object("kind", "decimal", "value", "0.3")));
        for (int index = 0; index < 3; index++) {
            boolean bee = index < 2;
            JsonObject source = object("owner", "fixture", "handler", bee ? "rootBees" : "rootTrees", "key", "species." + index);
            ids[index] = Identity.origin("species", source);
            JsonArray members = new JsonArray(), products = new JsonArray();
            members.add(object("form", bee ? "queen" : "sapling", "item", item));
            products.add(object("item", item, "amount", "2", "chance", bee ? Chance.decimal(0.3f, 1) : JsonNull.INSTANCE));
            String name = index == 0 ? "Fixture bee 蜜蜂" : index == 1 ? "Fixture hybrid 杂交蜂" : "Fixture tree 树木";
            species.put(ids[index], object("id", ids[index], "source", source, "kind", bee ? "bee" : "tree", "name", text.apply(name),
                    "description", text.apply("Synthetic contract fixture"), "binomial", "fixture", "authority", "fixture",
                    "temperature", "normal", "humidity", "normal", "dominant", true, "secret", index == 1, "blacklisted", !bee, "counted", true,
                    "nocturnal", bee ? new JsonPrimitive(false) : JsonNull.INSTANCE, "fruitCompatible", bee ? JsonNull.INSTANCE : new JsonPrimitive(false),
                    "members", members, "genes", genes, "products", products, "specialties", new JsonArray()));
        }
        Dataset.Records speciesRows = dataset.records("species");
        for (JsonObject row : species.values()) speciesRows.write(row);
        java.util.TreeMap<String, JsonObject> mutations = new java.util.TreeMap<>();
        for (int index = 0; index < 2; index++) {
            String[] pair = {ids[0], ids[index]};
            Arrays.sort(pair);
            JsonArray parents = new JsonArray(), conditions = new JsonArray();
            for (String parent : pair) parents.add(new JsonPrimitive(parent));
            conditions.add(new JsonPrimitive(text.apply("Fixture warm biome condition")));
            JsonArray resultGenes = new JsonArray();
            resultGenes.add(object("key", "speed", "allele", "fixture.fast", "name", text.apply("Fixture fast allele"), "dominant", false,
                    "value", object("kind", "decimal", "value", "2")));
            JsonObject mutation = object("handler", "fixture.mutation", "occurrence", 0, "parents", parents, "result", ids[1], "chance", Chance.decimal(index == 0 ? 7.5f : 10f, 100),
                    "conditions", conditions, "secret", index == 1, "genes", resultGenes);
            String id = Identity.content("mutation", mutation);
            mutation.addProperty("id", id);
            mutations.put(id, mutation);
        }
        Dataset.Records mutationRows = dataset.records("mutations");
        for (JsonObject row : mutations.values()) mutationRows.write(row);
    }

    private static void probabilities() {
        require(Chance.decimal(0.3f, 1).equals(object("numerator", "3", "denominator", "10")), "Bee rate lost its decimal precision");
        require(Chance.decimal(7.5f, 100).equals(object("numerator", "3", "denominator", "40")), "Mutation percentage was treated as a unit fraction");
        require(Chance.of(0, 10000).equals(object("numerator", "0", "denominator", "1")), "Zero probability was not reduced");
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f}) {
            try { Chance.decimal(invalid, 1); }
            catch (IllegalArgumentException expected) { continue; }
            throw new AssertionError("Accepted invalid probability");
        }
    }

    private static void sorting(Path root) throws Exception {
        String padding = String.join("", java.util.Collections.nCopies(300, "x"));
        Path staging = root.resolve("sorted"), work = root.resolve("sort-work");
        Jobs.Result result;
        try (Dataset dataset = new Dataset(staging, "sort", environment(), true);
             Rows rows = new Rows(work, 256)) {
            // A small memory budget exercises multiple merge passes without a large fixture.
            for (int index = 40; index >= 0; index--) {
                JsonObject row = object("id", String.format(java.util.Locale.ROOT, "text_%03d", index), "text", padding);
                rows.add("strings", row);
                rows.add("strings", row);
            }
            rows.write(dataset);
            dataset.seal();
            result = dataset.prepare(root.resolve("sort-datasets")).call();
        }
        require(!Files.exists(work), "Sorting left its workspace open");
        Path directory = Paths.get(result.path);
        JsonObject manifest = new JsonParser().parse(new String(Files.readAllBytes(directory.resolve("manifest.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        int count = 0;
        String previous = "";
        for (com.google.gson.JsonElement entry : manifest.getAsJsonArray("files")) {
            JsonObject file = entry.getAsJsonObject();
            if (!file.get("kind").getAsString().equals("strings")) continue;
            try (java.io.BufferedReader input = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.util.zip.GZIPInputStream(Files.newInputStream(directory.resolve(file.get("path").getAsString()))), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    String id = new JsonParser().parse(line).getAsJsonObject().get("id").getAsString();
                    require(previous.compareTo(id) < 0, "External sorting lost order or emitted duplicate identities");
                    previous = id; count++;
                }
            }
        }
        require(count == 41, "External sorting lost unique records");
        Path aborted = root.resolve("sort-conflict"), conflictWork = root.resolve("conflict-work");
        try (Dataset dataset = new Dataset(aborted, "sort", environment(), false);
             Rows rows = new Rows(conflictWork, 256)) {
            rows.add("strings", object("id", "text_same", "text", padding));
            for (int index = 0; index < 40; index++) rows.add("strings", object("id", "text_" + index, "text", padding));
            rows.add("strings", object("id", "text_same", "text", padding + "different"));
            expectFailure(() -> rows.write(dataset));
        }
        require(!Files.exists(aborted) && !Files.exists(conflictWork), "Conflicting source left open files");
    }

    private static void identities() {
        NBTTagCompound first = new NBTTagCompound();
        first.setLong("long", Long.MAX_VALUE);
        first.setTag("float", new NBTTagFloat(Float.intBitsToFloat(0x80000000)));
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagInt(7));
        list.appendTag(new NBTTagInt(-4));
        first.setTag("list", list);
        NBTTagCompound second = new NBTTagCompound();
        second.setTag("list", list.copy());
        second.setTag("float", first.getTag("float").copy());
        second.setLong("long", Long.MAX_VALUE);
        byte[] encoded = CanonicalJson.bytes(TypedNbt.encode(first));
        require(Arrays.equals(encoded, CanonicalJson.bytes(TypedNbt.encode(second))), "Compound insertion order changed identity");
        require(list.tagCount() == 2, "NBT encoding mutated the game list");
        String value = new String(encoded, StandardCharsets.UTF_8);
        require(value.contains("9223372036854775807") && value.contains("80000000"), "NBT lost integer precision or signed zero");
        String id = Identity.item("minecraft:stone", 0, TypedNbt.encode(first));
        first.setInteger("RepairCost", 3);
        require(!id.equals(Identity.item("minecraft:stone", 0, TypedNbt.encode(first))), "Identity erased meaningful NBT");
        require(!Identity.item("minecraft:stone", 0, JsonNull.INSTANCE).equals(Identity.item("minecraft:stone", 1, JsonNull.INSTANCE)), "Metadata variants collided");
    }

    private static JsonObject environment() {
        JsonArray parameters = new JsonArray();
        for (Probe probe : probes()) parameters.add(probe.json());
        return object("game", "Minecraft 1.7.10", "loader", "Forge 10.13.4.1614", "locale", "en_US", "mods", new JsonArray(),
                "inputs", new JsonArray(), "resources", new JsonArray(), "settings", new JsonObject(), "knowledge", new JsonObject(), "probes", parameters);
    }
    private static java.util.List<Probe> probes() { return Arrays.asList(Probe.defaults(), new Probe(4, java.util.Collections.singletonMap("coil", 2))); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expectFailure(Checked action) throws Exception {
        try { action.run(); }
        catch (java.io.IOException expected) { return; }
        throw new AssertionError("Expected invalid source data to be rejected");
    }
    private interface Checked { void run() throws Exception; }
}
