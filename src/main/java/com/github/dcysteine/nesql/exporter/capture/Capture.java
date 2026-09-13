package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.CollapsibleItems;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.main.Main;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Dataset;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.Rows;
import com.github.dcysteine.nesql.exporter.task.ClientThread;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** One source pipeline shared by MCP and the in-game command. No database or legacy exporter is involved. */
public final class Capture implements Jobs.Task {
    private final Path instance, directory;
    private final ClientThread client;

    public Capture(Path instance, ClientThread client) {
        this.instance = instance.toAbsolutePath().normalize();
        this.directory = this.instance.resolve("nesql");
        this.client = client;
    }

    public JsonObject inspect() {
        JsonArray profiles = new JsonArray();
        for (String profile : new String[] {"full", "data", "images"}) profiles.add(value(profile));
        JsonObject result = object("game", "Minecraft 1.7.10", "target", "GTNH 2.8.4", "ready", client.ready(),
                "exporter", Main.MOD_VERSION, "revision", Dataset.REVISION,
                "reason", client.reason(), "profiles", profiles, "queued", client.queued(),
                "longestClientMicros", Long.toString(client.longestMicros()));
        if (client.ready()) {
            try {
                JsonObject state = client.call(() -> {
                    net.minecraft.server.integrated.IntegratedServer server = net.minecraft.client.Minecraft.getMinecraft().getIntegratedServer();
                    JsonArray handlers = new JsonArray();
                    for (Recipes.Handler handler : Recipes.handlers()) handlers.add(handler.describe());
                    return object("handlers", handlers, "itemsReady", ItemList.loadFinished && !ItemList.items.isEmpty(),
                            "world", object("folder", server.getFolderName(), "name", server.getWorldName()), "items", ItemList.items.size(),
                            "research", Studies.capture().describe(), "materials", GtMaterials.inspect());
                });
                state.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
                JsonObject sources = client.call(Sources::capture).inspect();
                sources.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
            } catch (Jobs.Fault error) { throw error; }
            catch (Exception error) { throw new Jobs.Fault("inspect_failed", error.getMessage()); }
        }
        return result;
    }

    @Override public void run(Jobs.Context context) throws Exception {
        ClientThread.Session session = client.session();
        Jobs.Request request = context.request();
        session.call(() -> { request.checkWorld(net.minecraft.client.Minecraft.getMinecraft().getIntegratedServer().getFolderName()); return null; });
        context.progress("plan", 0, 1, "Checking NEI data and requested handler coverage");
        List<Recipes.Handler> handlers = session.call(() -> {
            if (!ItemList.loadFinished || ItemList.items.isEmpty()) throw new Jobs.Fault("items_unavailable", "Wait for NEI to finish loading its item list");
            List<Recipes.Handler> selected = new ArrayList<>();
            if (request.profile.equals("images")) return selected;
            Set<String> remaining = new HashSet<>(request.handlers);
            for (Recipes.Handler handler : Recipes.handlers()) {
                if (request.handlers.isEmpty() || remaining.remove(handler.id)) {
                    if (!handler.supported) throw new Jobs.Fault("handler_unsupported", "No adapter for " + handler.name + ": " + handler.id);
                    selected.add(handler);
                }
            }
            if (!remaining.isEmpty()) throw new Jobs.Fault("handler_missing", "Requested handler is not registered: " + remaining.iterator().next());
            return selected;
        });
        context.progress("registry", 0, 1, "Checking research and material registries");
        Magic magic = session.call(Magic::new);
        List<gregtech.api.enums.Materials> materials = session.call(GtMaterials::all);
        context.progress("environment", 0, 1, "Fingerprinting loaded mods, configuration, scripts and resource packs");
        JsonObject environment = Environment.capture(session, instance, request);
        Facts facts = new Facts(environment.get("locale").getAsString());
        Path workRoot = directory.resolve("work");
        Dataset.directory(workRoot);
        Path work = workRoot.resolve(context.id());
        Images images = new Images();
        Models models = request.profile.equals("data") ? null : session.call(Models::new);
        try (Dataset dataset = new Dataset(work, Main.MOD_VERSION, environment,
                request.profile.equals("full") && request.handlers.isEmpty())) {
            try (Rows rows = new Rows(workRoot.resolve(context.id() + "-sort"));
                 AutoCloseable visuals = () -> client.cleanup(() -> { try (Models owned = models) { images.close(); } })) {
                Sink sink = new Sink(dataset, rows, images, session, !request.profile.equals("data"));
                for (int index = 0; index < magic.aspectCount(); index++) {
                    final int aspect = index;
                    session.call(() -> { magic.aspect(aspect, facts); return null; });
                    sink.write(facts.drain());
                }
                context.progress("aspects", magic.aspectCount(), magic.aspectCount(), "Captured registered aspect definitions");
                captureItems(session, facts, sink, context);
                Map<String, Fluid> fluids = session.call(() -> new TreeMap<>(FluidRegistry.getRegisteredFluids()));
                int fluidCount = 0;
                for (Map.Entry<String, Fluid> entry : fluids.entrySet()) {
                    session.call(() -> {
                        if (FluidRegistry.getFluid(entry.getKey()) != entry.getValue()) throw new Jobs.Fault("registry_changed", "Fluid registry changed during export");
                        facts.fluid(new FluidStack(entry.getValue(), 1));
                        return null;
                    });
                    sink.write(facts.drain());
                    fluidCount++;
                    if (fluidCount % 64 == 0 || fluidCount == fluids.size()) context.progress("fluids", fluidCount, fluids.size(), "Captured registered fluids");
                }
                for (int index = 0; index < materials.size(); index++) {
                    final gregtech.api.enums.Materials material = materials.get(index);
                    GtMaterials.Cursor cursor = session.call(() -> new GtMaterials.Cursor(material, facts));
                    boolean done;
                    do {
                        done = session.call(() -> cursor.capture(facts));
                        sink.write(facts.drain());
                    } while (!done);
                    if ((index + 1) % 16 == 0 || index + 1 == materials.size()) context.progress("materials", index + 1, materials.size(), "Captured material forms and composition");
                }
                GtCircuits circuits = session.call(GtCircuits::new);
                for (int index = 0; index < circuits.size(); index++) {
                    final int family = index;
                    session.call(() -> { circuits.capture(family, facts); return null; });
                    sink.write(facts.drain());
                }
                context.progress("circuits", circuits.size(), circuits.size(), "Captured circuit definitions from NEICustomDiagram");
                for (Forestry genetics : session.call(Forestry::all)) {
                    for (int index = 0; index < genetics.speciesCount(); index++) {
                        final int species = index;
                        session.call(() -> { genetics.captureSpecies(species, facts); return null; });
                        sink.write(facts.drain());
                        if ((index + 1) % 16 == 0 || index + 1 == genetics.speciesCount()) {
                            context.progress("species", index + 1, genetics.speciesCount(), genetics.name());
                        }
                    }
                    for (int index = 0; index < genetics.mutationCount(); index++) {
                        final int mutation = index;
                        session.call(() -> { genetics.captureMutation(mutation, facts); return null; });
                        sink.write(facts.drain());
                        if ((index + 1) % 64 == 0 || index + 1 == genetics.mutationCount()) {
                            context.progress("mutations", index + 1, genetics.mutationCount(), genetics.name());
                        }
                    }
                }
                List<Structures.Machine> machines = session.call(Structures::all);
                for (int index = 0; index < magic.researchCount(); index++) {
                    final int study = index;
                    session.call(() -> { magic.research(study, facts); return null; });
                    sink.write(facts.drain());
                    if ((index + 1) % 32 == 0 || index + 1 == magic.researchCount()) context.progress("research", index + 1, magic.researchCount(), "Captured research prerequisites and observed knowledge");
                }
                for (int index = 0; index < machines.size(); index++) {
                    final Structures.Machine machine = machines.get(index);
                    Structures.Cursor cursor = session.call(() -> new Structures.Cursor(machine, facts, models, request.probes, request.profile.equals("full") && request.handlers.isEmpty()));
                    try {
                        boolean done;
                        do {
                            done = session.call(cursor::capture);
                            sink.write(facts.drain());
                        } while (!done);
                    } finally { client.cleanup(cursor::close); }
                    if ((index + 1) % 16 == 0 || index + 1 == machines.size()) context.progress("structures", index + 1, machines.size(), "Captured registered structure definitions");
                }
                for (Recipes.Handler handler : handlers) {
                    Recipes.Cursor cursor = session.call(() -> handler.open(facts, !request.profile.equals("data")));
                    try {
                        sink.write(facts.drain());
                        int size = session.call(cursor::size);
                        context.progress("recipes", 0, size, handler.name);
                        for (int index = 0; index < size; index++) {
                            final int recipe = index;
                            session.call(() -> { cursor.capture(recipe); return null; });
                            sink.write(facts.drain());
                            if ((index + 1) % 64 == 0 || index + 1 == size) context.progress("recipes", index + 1, size, handler.name);
                        }
                    } finally { client.cleanup(cursor::close); }
                }
                context.progress("records", 0, 1, "Sorting records, checking duplicate identities and writing bounded shards");
                rows.write(dataset);
                context.progress("verify", 0, 1, "Checking that the game environment did not change during capture");
                JsonObject current = Environment.capture(session, instance, request);
                if (!CanonicalJson.digest(environment).equals(CanonicalJson.digest(current))) {
                    throw new Jobs.Fault("environment_changed", "Mods, resources, configuration or player knowledge changed during export");
                }
            }
            dataset.seal();
            java.util.concurrent.Callable<Jobs.Result> publication = dataset.prepare(directory.resolve("datasets"));
            session.call(() -> null);
            context.publish(publication);
        }
    }

    private void captureItems(ClientThread.Session session, Facts facts, Sink sink, Jobs.Context context) throws Exception {
        List<ItemStack> items = session.call(() -> ItemList.items);
        Map<Integer, LinkedHashSet<String>> memberships = new TreeMap<>();
        for (int start = 0; start < items.size(); start += 16) {
            final int offset = start;
            session.call(() -> {
                if (!ItemList.loadFinished || ItemList.items != items) throw new Jobs.Fault("items_changed", "NEI item list changed during export");
                for (int index = offset; index < Math.min(offset + 16, items.size()); index++) {
                    ItemStack item = items.get(index);
                    String id = facts.item(item, index);
                    int group = CollapsibleItems.getGroupIndex(item);
                    if (group >= 0) memberships.computeIfAbsent(group, ignored -> new LinkedHashSet<>()).add(id);
                }
                return null;
            });
            sink.write(facts.drain());
            if (start % 128 == 0 || start + 16 >= items.size()) context.progress("items", Math.min(start + 16, items.size()), items.size(), "Captured NEI item facts and textures");
        }
        session.call(() -> {
            List<?> groups = ReflectionHelper.getPrivateValue(CollapsibleItems.class, null, "groups");
            for (Map.Entry<Integer, LinkedHashSet<String>> group : memberships.entrySet()) {
                Object source = groups.get(group.getKey());
                java.lang.reflect.Field field = source.getClass().getField("guid");
                field.setAccessible(true);
                String guid = (String) field.get(source);
                if (guid == null || guid.isEmpty()) throw new Jobs.Fault("group_identity", "NEI browser group has no stable guid");
                JsonObject origin = object("owner", "NotEnoughItems", "handler", "collapsible", "key", guid);
                String name = CollapsibleItems.getDisplayName(group.getKey());
                JsonArray members = new JsonArray();
                for (String member : group.getValue()) members.add(value(member));
                facts.row("groups", object("id", Identity.origin("group", origin), "source", origin,
                        "name", name == null ? null : facts.text(name), "members", members,
                        "representative", group.getValue().iterator().next(), "collapsed", !CollapsibleItems.isExpanded(group.getKey()), "order", group.getKey()));
            }
            return null;
        });
        sink.write(facts.drain());
    }

    private static final class Sink {
        final Dataset dataset;
        final Rows rows;
        final Images images;
        final ClientThread.Session client;
        final boolean textures;
        final Map<Object, String> paints = new java.util.HashMap<>();

        Sink(Dataset dataset, Rows rows, Images images, ClientThread.Session client, boolean textures) {
            this.dataset = dataset; this.rows = rows; this.images = images; this.client = client; this.textures = textures;
        }

        void write(Facts.Batch batch) throws Exception {
            for (Models.Draft model : batch.models) {
                JsonArray faces = new JsonArray();
                for (Models.Face face : model.faces) {
                    String texture = paints.get(face.paint.key());
                    if (texture == null) {
                        texture = asset(images.paint(face.paint, client));
                        paints.put(face.paint.key(), texture);
                    }
                    face.record.addProperty("texture", texture); faces.add(face.record);
                }
                JsonObject record = object("faces", faces, "hidden", faces.size() == 0);
                String id = Identity.content("model", record); record.addProperty("id", id);
                rows.add("models", record); model.appearance.addProperty("model", id);
            }
            if (textures) for (Facts.Picture picture : batch.pictures) picture.record.addProperty(picture.field, asset(images.picture(picture, client)));
            for (Facts.Icon icon : batch.icons) {
                if (textures) icon.record.addProperty("icon", asset(images.capture(icon, client)));
                rows.add(icon.kind, icon.record);
            }
            for (Facts.Scene scene : batch.scenes) {
                String background = asset(images.scene(scene, client));
                JsonArray elements = new JsonArray();
                elements.add(object("kind", "sprite", "asset", background, "x", 0, "y", 0,
                        "width", scene.width, "height", scene.height, "z", scene.z));
                for (JsonElement element : scene.elements) elements.add(element);
                JsonObject view = object("width", scene.width, "height", scene.height, "elements", elements);
                String id = Identity.content("view", view);
                view.addProperty("id", id);
                scene.recipe.addProperty("view", id);
                rows.add("views", view);
            }
            for (Facts.Record record : batch.records) rows.add(record.kind, record.value);
        }

        String asset(Images.Image image) throws IOException {
            Jobs.checkpoint();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image.pixels, "png", output)) throw new IOException("PNG encoder is unavailable");
            String path = dataset.asset(output.toByteArray(), "png");
            JsonObject asset = object("path", path, "width", image.pixels.getWidth(), "height", image.pixels.getHeight(),
                    "frames", image.frames, "interpolate", false, "source", object("kind", image.kind, "location", image.location));
            String id = Identity.content("asset", asset);
            asset.addProperty("id", id);
            rows.add("assets", asset);
            return id;
        }
    }
}
