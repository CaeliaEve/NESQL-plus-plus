package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.source.Probe;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Edits a draft list; only Done replaces the export request's parameters. */
final class ProbeScreen extends GuiScreen {
    private final GuiScreen parent;
    private final Consumer<List<Probe>> accept;
    private final List<Probe> probes;
    private GuiTextField count, channels;
    private GuiButton previous, next, add, remove;
    private int index;
    private String error;

    ProbeScreen(GuiScreen parent, List<Probe> probes, Consumer<List<Probe>> accept) {
        this.parent = parent; this.probes = new ArrayList<>(probes); this.accept = accept;
    }

    @Override public void initGui() {
        int x = width / 2 - 145;
        String oldCount = count == null ? null : count.getText(), oldChannels = channels == null ? null : channels.getText();
        count = new GuiTextField(fontRendererObj, x, 66, 60, 20);
        count.setMaxStringLength(2);
        channels = new GuiTextField(fontRendererObj, x, 106, 290, 20);
        channels.setMaxStringLength(2304);
        if (oldCount == null) show();
        else { count.setText(oldCount); channels.setText(oldChannels); }
        buttonList.clear();
        previous = new GuiButton(0, x, 140, 65, 20, "Previous");
        next = new GuiButton(1, x + 75, 140, 65, 20, "Next");
        add = new GuiButton(2, x + 150, 140, 65, 20, "Add");
        remove = new GuiButton(3, x + 225, 140, 65, 20, "Remove");
        buttonList.add(previous); buttonList.add(next); buttonList.add(add); buttonList.add(remove);
        buttonList.add(new GuiButton(4, x, height - 30, 140, 20, "Done"));
        buttonList.add(new GuiButton(5, x + 150, height - 30, 140, 20, "Discard changes"));
        enabled();
    }

    private void show() {
        Probe probe = probes.get(index);
        count.setText(Integer.toString(probe.count));
        channels.setText(probe.channels.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", ")));
    }

    private void save() {
        if (!count.getText().matches("[1-9][0-9]?")) throw new IllegalArgumentException("Count must be a whole number from 1 to 64.");
        Map<String, Integer> values = new TreeMap<>();
        if (!channels.getText().trim().isEmpty()) {
            for (String field : channels.getText().split(",", -1)) {
                String[] pair = field.trim().split("=", -1);
                if (pair.length != 2 || !pair[1].trim().matches("[1-9][0-9]{0,4}")) {
                    throw new IllegalArgumentException("Use channel=value, for example coil=2, glass=4.");
                }
                if (values.put(pair[0].trim(), Integer.parseInt(pair[1].trim())) != null) throw new IllegalArgumentException("Channel names must be unique.");
            }
        }
        probes.set(index, new Probe(Integer.parseInt(count.getText()), values));
    }

    @Override protected void actionPerformed(GuiButton button) {
        error = null;
        if (button.id == 5) { mc.displayGuiScreen(parent); return; }
        try {
            // A malformed draft can still be removed without first repairing it.
            if (button.id != 3) save();
            if (button.id == 0) index--;
            if (button.id == 1) index++;
            if (button.id == 2) {
                int count = 1;
                while (probes.contains(new Probe(count, java.util.Collections.emptyMap()))) count++;
                probes.add(new Probe(count, java.util.Collections.emptyMap())); index = probes.size() - 1;
            }
            if (button.id == 3) { probes.remove(index); index = Math.min(index, probes.size() - 1); }
            if (button.id == 4) { accept.accept(Probe.order(probes)); mc.displayGuiScreen(parent); return; }
            show(); enabled();
        } catch (IllegalArgumentException failure) { error = failure.getMessage(); }
    }

    private void enabled() {
        previous.enabled = index > 0; next.enabled = index + 1 < probes.size();
        add.enabled = probes.size() < 16; remove.enabled = probes.size() > 1;
    }

    @Override public void updateScreen() { count.updateCursorCounter(); channels.updateCursorCounter(); }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 145;
        drawCenteredString(fontRendererObj, "Construction parameters  " + (index + 1) + " / " + probes.size(), width / 2, 18, 0xffffff);
        drawString(fontRendererObj, "Hologram count (1-64)", x, 52, 0xb8b8b8);
        drawString(fontRendererObj, "Channels: coil=2, glass=4 (optional)", x, 92, 0xb8b8b8);
        count.drawTextBox(); channels.drawTextBox();
        fontRendererObj.drawSplitString(error == null ? "Up to 16 distinct sets. Use lowercase channel names and values 1-65535. Each set builds a separate preview." : error,
                x, 172, 290, error == null ? 0xb8b8b8 : 0xff7777);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button); count.mouseClicked(x, y, button); channels.mouseClicked(x, y, button);
    }
    @Override protected void keyTyped(char character, int key) {
        if (key == 1) { mc.displayGuiScreen(parent); return; }
        if (key == 15) { boolean focused = count.isFocused(); count.setFocused(!focused); channels.setFocused(focused); return; }
        if (!count.textboxKeyTyped(character, key)) channels.textboxKeyTyped(character, key);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
