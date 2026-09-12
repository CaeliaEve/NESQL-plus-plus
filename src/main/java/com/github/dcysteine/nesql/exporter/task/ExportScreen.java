package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.source.Probe;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** A small job client. Closing the screen does not own or cancel the export task. */
public final class ExportScreen extends GuiScreen {
    private final Exports exports;
    private final String[] profiles = {"full", "data", "images"};
    private int profile;
    private boolean selection;
    private GuiTextField name;
    private GuiButton start, cancel, scope, mode, refresh, parameters;
    private List<Probe> probes = java.util.Collections.singletonList(Probe.defaults());
    private JsonObject game;
    private CompletableFuture<JsonObject> inspection;
    private CompletableFuture<Jobs.Job> pending;
    private Jobs.Job job;
    private String error;
    private long nextPoll;

    public ExportScreen(Exports exports) { this.exports = exports; }

    @Override public void initGui() {
        int x = width / 2 - 145, y = 48;
        String previous = name == null ? "catalog" : name.getText();
        name = new GuiTextField(fontRendererObj, x, y, 290, 20);
        name.setMaxStringLength(80); name.setText(previous);
        buttonList.clear();
        mode = new GuiButton(0, x, y + 28, 140, 20, "Profile: " + profiles[profile]);
        scope = new GuiButton(1, x + 150, y + 28, 140, 20, scopeLabel());
        start = new GuiButton(2, x, y + 56, 140, 20, "Export");
        cancel = new GuiButton(3, x + 150, y + 56, 140, 20, "Cancel job");
        buttonList.add(mode); buttonList.add(scope); buttonList.add(start); buttonList.add(cancel);
        parameters = new GuiButton(6, x + 100, height - 30, 90, 20, "Parameters: " + probes.size());
        buttonList.add(parameters);
        refresh = new GuiButton(4, x, height - 30, 90, 20, "Refresh game");
        buttonList.add(refresh);
        buttonList.add(new GuiButton(5, x + 200, height - 30, 90, 20, "Close"));
        if (inspection == null) inspection = exports.inspect();
        if (pending == null) pending = exports.current();
        enabled();
    }

    @Override protected void actionPerformed(GuiButton button) {
        error = null;
        if (button.id == 0) { profile = (profile + 1) % profiles.length; mode.displayString = "Profile: " + profiles[profile]; }
        if (button.id == 1) { selection = !selection; scope.displayString = scopeLabel(); }
        if (button.id == 2) {
            try {
                List<String> handlers = new ArrayList<>();
                if (selection && !profiles[profile].equals("images")) {
                    for (JsonElement entry : game.getAsJsonArray("handlers")) {
                        JsonObject handler = entry.getAsJsonObject();
                        if (handler.get("supported").getAsBoolean()) handlers.add(handler.get("id").getAsString());
                    }
                    if (handlers.isEmpty()) throw new Jobs.Fault("handler_missing", "No supported handlers are available");
                }
                pending = exports.start(new Jobs.Request(UUID.randomUUID().toString(), name.getText(), profiles[profile], handlers, probes));
            } catch (RuntimeException failure) { error = failure.getMessage(); }
        }
        if (button.id == 3 && job != null) pending = exports.cancel(job.id);
        if (button.id == 4 && inspection == null) inspection = exports.inspect();
        if (button.id == 5) mc.displayGuiScreen(null);
        if (button.id == 6) mc.displayGuiScreen(new ProbeScreen(this, probes, value -> probes = value));
    }

    @Override public void updateScreen() {
        name.updateCursorCounter();
        if (inspection != null && inspection.isDone()) {
            try { game = inspection.join(); }
            catch (CompletionException failure) { error = failure.getCause().getMessage(); }
            inspection = null;
        }
        if (pending != null && pending.isDone()) {
            try { job = pending.join(); }
            catch (CompletionException failure) { error = failure.getCause().getMessage(); }
            pending = null;
            nextPoll = System.currentTimeMillis() + 500;
        }
        if (pending == null && System.currentTimeMillis() >= nextPoll) pending = exports.current();
        enabled();
    }

    private void enabled() {
        boolean active = job != null && !terminal();
        start.enabled = pending == null && inspection == null && !active && game != null
                && game.get("ready").getAsBoolean() && game.has("itemsReady") && game.get("itemsReady").getAsBoolean();
        cancel.enabled = pending == null && active;
        mode.enabled = pending == null && !active;
        scope.enabled = pending == null && !active && !profiles[profile].equals("images");
        parameters.enabled = pending == null && !active;
        refresh.enabled = inspection == null;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "NESQL - Local source export", width / 2, 18, 0xffffff);
        int x = width / 2 - 145, y = 138;
        drawString(fontRendererObj, "Dataset name", x, 36, 0xb8b8b8);
        name.drawTextBox();
        if (game != null && !game.get("ready").getAsBoolean()) {
            fontRendererObj.drawSplitString(game.get("reason").getAsString(), x, y, 290, 0xffcc66);
        } else if (game != null && game.has("itemsReady") && !game.get("itemsReady").getAsBoolean()) {
            drawString(fontRendererObj, "Wait for NEI to finish loading, then refresh.", x, y, 0xffcc66);
        } else if (game != null && game.has("handlers")) {
            int total = game.getAsJsonArray("handlers").size(), supported = 0;
            for (JsonElement entry : game.getAsJsonArray("handlers")) if (entry.getAsJsonObject().get("supported").getAsBoolean()) supported++;
            drawString(fontRendererObj, "Handler adapters: " + supported + " / " + total, x, y, 0xb8b8b8);
            drawString(fontRendererObj, selection ? "Selected handlers produce a selection snapshot." : "All handlers must be supported for a full export.", x, y + 14, 0xb8b8b8);
        }
        y += 38;
        if (job != null) {
            drawString(fontRendererObj, job.state + " / " + job.stage + "  " + job.completed + " / " + job.total, x, y, 0xffffff);
            if (!job.events.isEmpty()) fontRendererObj.drawSplitString(job.events.get(job.events.size() - 1).message, x, y + 14, 290, 0xb8b8b8);
            if (job.error != null) fontRendererObj.drawSplitString(job.error.get("message"), x, y + 40, 290, 0xff7777);
            if (job.result != null) fontRendererObj.drawSplitString(job.result.path, x, y + 40, 290, 0x88dd99);
        }
        if (error != null) fontRendererObj.drawSplitString(error, x, Math.max(y + 66, height - 76), 290, 0xff7777);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void mouseClicked(int x, int y, int button) { super.mouseClicked(x, y, button); name.mouseClicked(x, y, button); }
    @Override protected void keyTyped(char character, int key) { if (!name.textboxKeyTyped(character, key)) super.keyTyped(character, key); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() {
        if (inspection != null) { inspection.cancel(false); inspection = null; }
        // Closing the observer must not cancel an export or discard an accepted start request.
    }
    private String scopeLabel() { return selection ? "Handlers: supported" : "Handlers: all"; }
    private boolean terminal() { return job.state.equals("succeeded") || job.state.equals("failed") || job.state.equals("cancelled"); }
}
