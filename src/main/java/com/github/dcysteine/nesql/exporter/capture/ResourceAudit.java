package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Resources;
import com.github.dcysteine.nesql.exporter.task.Checks;
import com.github.dcysteine.nesql.exporter.task.ClientThread;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import java.nio.file.Path;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Native resource-manager observations, isolated from recipe coverage and Source publication. */
final class ResourceAudit {
    private final Path instance;
    private final ClientThread client;
    ResourceAudit(Path instance, ClientThread client) { this.instance = instance; this.client = client; }

    void run(Jobs.Context context) throws Exception {
        ClientThread.Session session = client.session();
        Jobs.Request request = context.request();
        Checks.Guard guard = () -> session.call("resource world", () -> {
            request.checkWorld(Minecraft.getMinecraft().getIntegratedServer().getFolderName()); return null;
        });
        guard.check();
        context.progress("check_plan", 0, 1, "Fingerprinting native resource environment");
        JsonObject environment = Environment.capture(session, instance, request);
        context.provenance(environment, session.identity());
        JsonArray rows = new JsonArray();
        for (String resource : request.check.resources) rows.add(object("resource", resource, "status", "pending"));
        Checks.Report report = new Checks.Report(instance.resolve("nesql/checks"), context, environment, rows);
        try {
            report.planning(context.timings()); report.save();
            Checks.sweep(context, report, guard, (index, row) -> {
                JsonObject evidence = read(session, row.get("resource").getAsString());
                evidence.entrySet().forEach(entry -> row.add(entry.getKey(), entry.getValue()));
                row.addProperty("status", "passed");
            });
            // Reloads can keep the same resource-manager object. Re-resolve every
            // successful observation as well as comparing the environment files.
            for (JsonElement value : rows) {
                JsonObject row = value.getAsJsonObject();
                if (!row.get("status").getAsString().equals("passed")) continue;
                guard.check();
                JsonObject repeated = read(session, row.get("resource").getAsString());
                if (!row.get("sha256").equals(repeated.get("sha256")) || !row.get("bytes").equals(repeated.get("bytes"))) {
                    throw new Jobs.Fault("resource_changed", "Native resource changed during the check: " + row.get("resource").getAsString());
                }
            }
            if (!CanonicalJson.digest(environment).equals(CanonicalJson.digest(Environment.capture(session, instance, request)))) {
                throw new Jobs.Fault("environment_changed", "Resource environment changed during the check");
            }
            context.checked(report.finish("complete", null));
        } catch (Exception | Error error) {
            try { report.finish(error instanceof java.util.concurrent.CancellationException || error instanceof InterruptedException ? "cancelled" : "stopped", error); }
            catch (Exception reporting) { error.addSuppressed(reporting); }
            throw error;
        }
    }

    private JsonObject read(ClientThread.Session session, String location) throws Exception {
        return session.call("resource " + location, () -> Resources.read(location,
                Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(location)).getInputStream()));
    }
}
