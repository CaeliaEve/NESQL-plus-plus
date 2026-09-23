package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Dataset;
import com.github.dcysteine.nesql.exporter.source.Rows;
import com.github.dcysteine.nesql.exporter.task.Checks;
import com.github.dcysteine.nesql.exporter.task.ClientThread;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Runs the production cursors in independent scopes; findings never publish a dataset. */
final class Audit {
    private final Path instance, directory;
    private final ClientThread client;
    Audit(Path instance, ClientThread client) { this.instance = instance; this.directory = instance.resolve("nesql"); this.client = client; }

    void run(Jobs.Context context) throws Exception {
        Jobs.Request request = context.request();
        ClientThread.Session session = client.session();
        List<ItemStack> items = session.call("check readiness", () -> {
            request.checkWorld(Minecraft.getMinecraft().getIntegratedServer().getFolderName());
            if (!ItemList.loadFinished || ItemList.items.isEmpty()) throw new Jobs.Fault("items_unavailable", "Wait for NEI to load");
            return ItemList.items;
        });
        context.progress("check_plan", 0, 0, "Fingerprinting the diagnostic environment");
        JsonObject environment = Environment.capture(session, instance, request);
        String fingerprint = CanonicalJson.digest(environment), locale = environment.get("locale").getAsString();
        Map<Integer, Structures.Machine> structures = new TreeMap<>();
        Map<String, Recipes.Handler> handlers = new TreeMap<>();
        JsonArray targets = new JsonArray();
        JsonArray inventory = new JsonArray();
        if (request.check.domain.equals("structures")) {
            for (Structures.Machine machine : session.call("structure inventory", Structures::all)) structures.put(machine.id, machine);
            Iterable<Integer> selected = request.check.controllers.isEmpty() ? structures.keySet() : request.check.controllers;
            for (int id : selected) {
                Structures.Machine machine = structures.get(id);
                targets.add(object("controller", id, "type", machine == null ? null : machine.machine.getClass().getName(), "status", "pending"));
            }
        } else {
            for (Recipes.Handler handler : session.call("handler inventory", Recipes::handlers)) handlers.put(handler.id, handler);
            Iterable<String> selected = request.handlers.isEmpty() ? handlers.keySet() : request.handlers;
            for (Recipes.Handler handler : handlers.values()) inventory.add(handler.describe());
            for (String id : selected) {
                Recipes.Handler handler = handlers.get(id);
                targets.add(object("handler", id, "name", handler == null ? null : handler.name,
                        "source", handler == null ? null : handler.origin, "status", "pending"));
            }
        }
        if (targets.size() == 0 || targets.size() > 8192) throw new Jobs.Fault("check_targets", "No diagnostic targets or too many targets");
        Path scratch = directory.resolve("check-work"); Dataset.directory(scratch);
        Path work = scratch.resolve(context.id()); Dataset.directory(work);
        Checks.Report report = new Checks.Report(directory.resolve("checks"), context, environment, targets);
        Checks.Guard guard = () -> health(session, request, items, environment);
        try {
            if (!handlers.isEmpty()) report.inventory(inventory);
            report.planning(context.timings());
            report.save();
            Checks.sweep(context, report, guard, (index, row) -> {
                    if (row.has("controller")) {
                        Structures.Machine machine = structures.get(row.get("controller").getAsInt());
                        if (machine == null) throw new Jobs.Fault("controller_missing", "Controller is not registered");
                        structure(session, request, machine, row, locale, work.resolve("structure-" + machine.id));
                    } else {
                        Recipes.Handler handler = handlers.get(row.get("handler").getAsString());
                        if (handler == null) throw new Jobs.Fault("handler_missing", "Handler is not registered");
                        if (!handler.supported) {
                            row.addProperty("status", "unsupported"); row.addProperty("reason", "No production adapter");
                        } else recipes(session, handler, row, locale, work.resolve("handler-" + index), context, guard, report);
                    }
            });
            if (!fingerprint.equals(CanonicalJson.digest(Environment.capture(session, instance, request)))) {
                throw new Jobs.Fault("environment_changed", "The game environment changed during the diagnostic sweep");
            }
            context.checked(report.finish("complete", null));
        } catch (Exception | Error error) {
            try { report.finish(error instanceof CancellationException || error instanceof InterruptedException ? "cancelled" : "stopped", error); }
            catch (IOException reporting) { error.addSuppressed(reporting); }
            throw error;
        }
    }

    private void health(ClientThread.Session session, Jobs.Request request, List<ItemStack> items, JsonObject environment) throws Exception {
        session.call("check environment", () -> {
            request.checkWorld(Minecraft.getMinecraft().getIntegratedServer().getFolderName());
            if (!ItemList.loadFinished || ItemList.items != items) throw new Jobs.Fault("items_changed", "NEI item list changed during checks");
            if (!environment.get("locale").getAsString().equals(Minecraft.getMinecraft().gameSettings.language)) throw new Jobs.Fault("environment_changed", "Language changed during checks");
            String knowledge = Environment.knowledge(Minecraft.getMinecraft().thePlayer.getCommandSenderName());
            if (!knowledge.equals(environment.getAsJsonObject("knowledge").get("thaumcraft").getAsString())) throw new Jobs.Fault("environment_changed", "Player knowledge changed during checks");
            return null;
        });
    }

    private void structure(ClientThread.Session session, Jobs.Request request, Structures.Machine machine, JsonObject result, String locale, Path path) throws Exception {
        Facts facts = new Facts(locale);
        String name = "structure " + machine.id;
        try (Buffer buffer = new Buffer(path)) {
            Structures.Cursor cursor = session.call(name + " open", () -> new Structures.Cursor(machine, facts, null, request.probes, false));
            try (AutoCloseable owned = () -> release(name, cursor::close)) {
                boolean done;
                do {
                    done = session.call(name + " capture", cursor::capture);
                    buffer.write(facts.drain());
                } while (!done);
            }
            result.add("counts", buffer.check());
            result.add("limitations", buffer.limitations());
            result.addProperty("status", buffer.problems.isEmpty() ? "passed" : "unsupported");
        }
    }

    private void recipes(ClientThread.Session session, Recipes.Handler handler, JsonObject result,
                         String locale, Path path, Jobs.Context context, Checks.Guard guard, Checks.Report report) throws Exception {
        Dataset.directory(path);
        Facts opening = new Facts(locale);
        Recipes.Cursor cursor = session.call("handler " + handler.id + " open", () -> handler.open(opening, false));
        try (AutoCloseable owned = () -> release("handler " + handler.id, cursor::close)) {
            try (Buffer buffer = new Buffer(path.resolve("category"))) { buffer.write(opening.drain()); result.add("categoryCounts", buffer.check()); }
            int total = session.call("handler " + handler.id + " size", cursor::size);
            Checks.recipes(context, report, guard, result, total, index -> {
                Facts facts = new Facts(locale);
                try (Buffer buffer = new Buffer(path.resolve("recipe-" + index))) {
                    session.call("recipe " + handler.id + " index=" + index, () -> { cursor.capture(index, facts); return null; });
                    buffer.write(facts.drain());
                    return buffer.check().has("recipes");
                }
            });
        }
    }

    private void release(String name, Runnable release) throws Exception {
        try { client.cleanup(name + " release", release); }
        catch (Exception error) {
            Jobs.Fault failure = new Jobs.Fault("check_cleanup", "Diagnostic scope did not release safely: " + name);
            failure.initCause(error); throw failure;
        }
    }

    private static final class Buffer implements AutoCloseable {
        final Rows rows;
        final Map<String, String> texts = new TreeMap<>();
        final List<String> problems = new ArrayList<>();
        Buffer(Path path) throws IOException { rows = new Rows(path); }
        void write(Facts.Batch batch) throws IOException {
            if (!batch.scenes.isEmpty() || !batch.models.isEmpty()) throw new IOException("Data checks unexpectedly requested graphics");
            for (Facts.Icon icon : batch.icons) rows.add(icon.kind, icon.record);
            for (Facts.Record record : batch.records) {
                rows.add(record.kind, record.value);
                if (record.kind.equals("strings") && texts.size() < 4096) texts.put(record.value.get("id").getAsString(), record.value.get("text").getAsString());
                if (record.kind.equals("structures")) {
                    problem(record.value.get("problem"));
                    for (JsonElement variant : record.value.getAsJsonArray("variants")) problem(variant.getAsJsonObject().get("problem"));
                }
            }
        }
        void problem(JsonElement id) { if (id != null && !id.isJsonNull()) problems.add(id.getAsString()); }
        JsonArray limitations() { JsonArray result = new JsonArray(); for (String id : problems) result.add(value(texts.getOrDefault(id, id))); return result; }
        JsonObject check() throws IOException {
            JsonObject counts = new JsonObject(); rows.check().forEach(counts::addProperty); return counts;
        }
        @Override public void close() throws IOException { rows.close(); }
    }
}
