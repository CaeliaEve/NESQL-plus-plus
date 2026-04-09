package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExportProgressGui extends GuiScreen {
    private static final Pattern STAGE_PATTERN =
            Pattern.compile("\\[NESQL\\]\\s*Stage\\s+(\\d+)/(\\d+):\\s+(.+)$");

    // Static reference to the currently active GUI instance for Logger access
    private static ExportProgressGui activeGui = null;

    // Thread-safe list to prevent ConcurrentModificationException
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private String title = "NESQL++";
    private String subtitle = "Export session";
    private String statusLine = "Initializing";
    private String currentStage = "Waiting";
    private String failureSummary = null;
    private int currentStageIndex = 0;
    private int totalStages = 0;
    private int maxMessages = 10;

    public ExportProgressGui() {
        // Set this as the active GUI instance
        activeGui = this;
    }

    /**
     * Get the currently active GUI instance, or null if none is active.
     * This is used by Logger to update the GUI progress.
     */
    public static ExportProgressGui getActiveGui() {
        return activeGui;
    }

    public void clear() {
        messages.clear();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public void addMessage(String message) {
        // Remove color codes for cleaner display
        String cleanMessage = message.replaceAll("\u00A7.", "");
        messages.add(cleanMessage);
        parseStructuredState(cleanMessage);

        // Keep only the most recent messages
        while (messages.size() > maxMessages) {
            messages.remove(0);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Check if fontRendererObj is initialized (may not be if GUI wasn't opened properly)
        if (fontRendererObj == null) {
            return;
        }

        // Draw semi-transparent background
        drawDefaultBackground();

        int panelWidth = Math.min(380, width - 40);
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = 18;
        int panelBottom = height - 18;

        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xC0101010);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, 0xFF5BC0EB);
        drawRect(panelLeft, panelBottom - 1, panelLeft + panelWidth, panelBottom, 0xFF5BC0EB);
        drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF5BC0EB);
        drawRect(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth, panelBottom, 0xFF5BC0EB);

        drawCenteredString(fontRendererObj, title, width / 2, panelTop + 10, 0xFFFFFF);
        drawCenteredString(fontRendererObj, subtitle, width / 2, panelTop + 24, 0x9FD7E8);

        int barLeft = panelLeft + 20;
        int barTop = panelTop + 46;
        int barWidth = panelWidth - 40;
        drawRect(barLeft, barTop, barLeft + barWidth, barTop + 10, 0xFF1B2430);
        int fillWidth = totalStages > 0 ? Math.max(2, (barWidth * currentStageIndex) / totalStages) : 2;
        drawRect(barLeft, barTop, barLeft + fillWidth, barTop + 10, failureSummary == null ? 0xFF4ADE80 : 0xFFF87171);

        drawCenteredString(
                fontRendererObj,
                totalStages > 0
                        ? "Stage " + currentStageIndex + " / " + totalStages
                        : "Preparing export plan",
                width / 2,
                barTop + 14,
                0xE5F9FF);

        drawString(fontRendererObj, "Current Stage", panelLeft + 20, panelTop + 76, 0xF8D66D);
        drawString(fontRendererObj, currentStage, panelLeft + 20, panelTop + 90, 0xFFFFFF);

        drawString(fontRendererObj, "Status", panelLeft + 20, panelTop + 112, 0xF8D66D);
        drawString(fontRendererObj, statusLine, panelLeft + 20, panelTop + 126, 0xDDE7F0);

        if (failureSummary != null) {
            drawString(fontRendererObj, "Failure Summary", panelLeft + 20, panelTop + 148, 0xFF8A8A);
            fontRendererObj.drawSplitString(failureSummary, panelLeft + 20, panelTop + 162, panelWidth - 40, 0xFFD3D3);
        }

        int logTop = failureSummary == null ? panelTop + 152 : panelTop + 206;
        drawString(fontRendererObj, "Recent Output", panelLeft + 20, logTop, 0xF8D66D);

        int y = logTop + 16;
        for (String message : messages) {
            fontRendererObj.drawSplitString(message, panelLeft + 20, y, panelWidth - 40, 0xFFFFFF);
            y += 18;
            if (y > panelBottom - 22) {
                break;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Call this when closing the GUI to clear the active reference.
     */
    public static void clearActiveGui() {
        activeGui = null;
    }

    private void parseStructuredState(String message) {
        Matcher matcher = STAGE_PATTERN.matcher(message);
        if (matcher.find()) {
            currentStageIndex = Integer.parseInt(matcher.group(1));
            totalStages = Integer.parseInt(matcher.group(2));
            currentStage = matcher.group(3);
            statusLine = "Running export pipeline";
            return;
        }

        if (message.contains("Export failed at stage:")) {
            statusLine = "Export failed";
            failureSummary = message;
            return;
        }

        if (message.contains("Root cause:")) {
            failureSummary = failureSummary == null ? message : failureSummary + " | " + message;
            return;
        }

        if (message.contains("complete") || message.contains("written") || message.contains("exported")) {
            statusLine = message;
        } else {
            statusLine = message;
        }
    }
}
