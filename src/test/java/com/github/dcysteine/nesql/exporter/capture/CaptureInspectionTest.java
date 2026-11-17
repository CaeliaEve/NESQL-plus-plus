package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.Callable;

/** Inspection reports handler readiness without hiding a conservative refusal. */
public final class CaptureInspectionTest {
    private CaptureInspectionTest() {}

    public static void main(String[] args) {
        run();
        System.out.println("Capture inspection: handler refusal is structured and non-fatal");
    }

    static void run() {
        JsonObject ready = Capture.inspectHandlers(() -> new JsonArray());
        require(ready.get("handlersReady").getAsBoolean(), "successful handler inspection");
        require(ready.getAsJsonArray("handlers").size() == 0, "successful handler list");
        require(!ready.has("handlersError"), "successful inspection has no error");

        JsonObject refused = Capture.inspectHandlers(() -> {
            throw new Jobs.Fault("target_family_unverified", "fixture family audit pending");
        });
        require(!refused.get("handlersReady").getAsBoolean(), "refused handler inspection remains visible");
        require("target_family_unverified".equals(
                refused.getAsJsonObject("handlersError").get("code").getAsString()), "refusal code");
        require(refused.getAsJsonArray("handlers").size() == 0, "refused handler list is empty");

        JsonObject unexpected = Capture.inspectHandlers(new Callable<JsonArray>() {
            @Override public JsonArray call() {
                throw new IllegalStateException("fixture inspection failure");
            }
        });
        require(!unexpected.get("handlersReady").getAsBoolean(), "unexpected failure is non-fatal");
        require("inspect_handlers_failed".equals(
                unexpected.getAsJsonObject("handlersError").get("code").getAsString()), "unexpected failure code");

        JsonObject section = Capture.inspectSection("structures", () -> {
            throw new Jobs.Fault("target_artifact_unverified", "fixture structure audit pending");
        });
        require(!section.get("structuresReady").getAsBoolean(), "section refusal is non-fatal");
        require(section.get("structures").isJsonNull(), "failed section has no fabricated data");
        require("target_artifact_unverified".equals(
                section.getAsJsonObject("structuresError").get("code").getAsString()), "section refusal code");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
