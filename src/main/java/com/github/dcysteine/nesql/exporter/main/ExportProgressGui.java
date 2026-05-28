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
    private static final int PANEL_MARGIN = 18;
    private static final int PANEL_MAX_WIDTH = 468;
    private static final int LOG_LINE_HEIGHT = 18;

    // Static reference to the currently active GUI instance for Logger access
    private static ExportProgressGui activeGui = null;

    // Thread-safe list to prevent ConcurrentModificationException
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final long openedAtMs = System.currentTimeMillis();
    private String title = "NESQL++";
    private String subtitle = "Export session";
    private String statusLine = "Initializing";
    private String currentStage = "Waiting";
    private String latestEvent = "Waiting for export pipeline";
    private String failureSummary = null;
    private int currentStageIndex = 0;
    private int totalStages = 0;
    private int totalMessageCount = 0;
    private long lastMessageAtMs = openedAtMs;
    private int maxMessages = 9;

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
        if (!shouldShowInRecentOutput(cleanMessage)) {
            parseStructuredState(cleanMessage);
            return;
        }
        cleanMessage = simplifyMessage(cleanMessage);
        latestEvent = cleanMessage;
        lastMessageAtMs = System.currentTimeMillis();
        totalMessageCount++;
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

        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - (PANEL_MARGIN * 2));
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = PANEL_MARGIN;
        int panelBottom = height - PANEL_MARGIN;
        int panelHeight = panelBottom - panelTop;

        drawRect(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelBottom + 1, 0xE60A1118);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xD1121923);
        drawRect(panelLeft + 1, panelTop + 2, panelLeft + panelWidth - 1, panelTop + 58, 0x241B6376);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, accentColor());
        drawRect(panelLeft, panelTop, panelLeft + 2, panelBottom, 0xAA29465A);
        drawRect(panelLeft + panelWidth - 2, panelTop, panelLeft + panelWidth, panelBottom, 0xAA29465A);
        drawRect(panelLeft, panelBottom - 2, panelLeft + panelWidth, panelBottom, 0xAA15212D);

        int contentLeft = panelLeft + 18;
        int contentRight = panelLeft + panelWidth - 18;

        drawCenteredString(fontRendererObj, title, width / 2, panelTop + 10, 0xF8FCFF);
        drawCenteredString(fontRendererObj, subtitle, width / 2, panelTop + 24, 0x8FD3E7);
        drawCenteredString(fontRendererObj, "Raw Export Pipeline", width / 2, panelTop + 36, 0x6FB4C9);

        int chipTop = panelTop + 54;
        drawMetricChip(contentLeft, chipTop, 92, "Elapsed", formatElapsed(System.currentTimeMillis() - openedAtMs), 0xD6F6FF);
        drawMetricChip(contentLeft + 100, chipTop, 88, "Events", Integer.toString(totalMessageCount), 0xEDE7C5);
        drawMetricChip(contentLeft + 196, chipTop, 112, "Last update", formatElapsed(System.currentTimeMillis() - lastMessageAtMs), 0xD6FFE1);

        int barTop = chipTop + 28;
        int barWidth = contentRight - contentLeft;
        drawRect(contentLeft, barTop, contentRight, barTop + 12, 0xFF16202A);
        drawRect(contentLeft + 1, barTop + 1, contentRight - 1, barTop + 11, 0xFF0D141C);
        int fillWidth = totalStages > 0
                ? Math.max(4, ((barWidth - 2) * Math.min(currentStageIndex, totalStages)) / totalStages)
                : Math.max(4, (barWidth - 2) / 10);
        drawRect(contentLeft + 1, barTop + 1, contentLeft + 1 + fillWidth, barTop + 11, accentColor());

        drawCenteredString(
                fontRendererObj,
                totalStages > 0
                        ? "Stage " + Math.min(currentStageIndex, totalStages) + " / " + totalStages
                        : "Preparing export pipeline",
                width / 2,
                barTop + 16,
                0xEAF7FF);

        int cardsTop = barTop + 34;
        int gap = 12;
        int cardWidth = (barWidth - gap) / 2;
        int leftCardHeight = failureSummary == null ? 88 : 120;
        int rightCardHeight = leftCardHeight;

        drawCard(contentLeft, cardsTop, cardWidth, leftCardHeight, "Pipeline");
        drawCard(contentLeft + cardWidth + gap, cardsTop, cardWidth, rightCardHeight, "Telemetry");

        drawLabelValue(contentLeft + 12, cardsTop + 20, "Current stage", currentStage, 0xFFFFFF, cardWidth - 24);
        drawLabelValue(contentLeft + 12, cardsTop + 48, "Status", statusLine, statusColor(), cardWidth - 24);
        if (failureSummary != null) {
            drawLabelValue(contentLeft + 12, cardsTop + 76, "Failure", failureSummary, 0xFFB8B8, cardWidth - 24);
        }

        drawLabelValue(contentLeft + cardWidth + gap + 12, cardsTop + 20, "Latest event", latestEvent, 0xD6E7F2, cardWidth - 24);
        drawLabelValue(
                contentLeft + cardWidth + gap + 12,
                cardsTop + 58,
                "Progress",
                totalStages > 0
                        ? (Math.min(currentStageIndex, totalStages) + " / " + totalStages + " stages")
                        : "Collecting stage plan",
                0xEDE7C5,
                cardWidth - 24);

        int logTop = cardsTop + leftCardHeight + 14;
        int logHeight = panelBottom - logTop - 12;
        drawCard(contentLeft, logTop, barWidth, logHeight, "Curated output");

        int y = logTop + 20;
        int logIndex = 0;
        for (String message : messages) {
            int rowTop = y - 2;
            if (logIndex % 2 == 0) {
                drawRect(contentLeft + 8, rowTop, contentRight - 8, rowTop + LOG_LINE_HEIGHT, 0x221B2B38);
            }
            fontRendererObj.drawSplitString(message, contentLeft + 12, y, barWidth - 24, 0xF5FBFF);
            y += LOG_LINE_HEIGHT;
            logIndex++;
            if (y > logTop + logHeight - 16) {
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
            currentStage = humanizeStage(matcher.group(3));
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

    private int accentColor() {
        return failureSummary == null ? 0xFF49D38A : 0xFFF87171;
    }

    private int statusColor() {
        return failureSummary == null ? 0xFFE8F3FB : 0xFFFFB4B4;
    }

    private void drawMetricChip(int x, int y, int width, String label, String value, int valueColor) {
        drawRect(x, y, x + width, y + 20, 0xA9141D27);
        drawRect(x, y, x + width, y + 1, 0x66354A5B);
        drawString(fontRendererObj, label, x + 6, y + 4, 0x86B8CA);
        drawString(fontRendererObj, value, x + 6, y + 12, valueColor);
    }

    private void drawCard(int x, int y, int width, int height, String label) {
        drawRect(x, y, x + width, y + height, 0x8A101820);
        drawRect(x, y, x + width, y + 1, 0xFF253746);
        drawString(fontRendererObj, label, x + 10, y + 6, 0xF7D774);
    }

    private void drawLabelValue(int x, int y, String label, String value, int valueColor, int wrapWidth) {
        drawString(fontRendererObj, label, x, y, 0x8EB3C7);
        fontRendererObj.drawSplitString(value == null ? "-" : value, x, y + 12, wrapWidth, valueColor);
    }

    private String formatElapsed(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private boolean shouldShowInRecentOutput(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase();
        if (lower.startsWith("  ") || lower.contains("active plugins:")) {
            return false;
        }
        if (lower.contains("repository:") || lower.contains("profile:")) {
            return false;
        }
        if (lower.contains("closing database")) {
            return false;
        }
        return lower.contains("[nesql]")
                || lower.contains("starting")
                || lower.contains("complete")
                || lower.contains("failed")
                || lower.contains("debug report")
                || lower.contains("root cause")
                || lower.contains("selection");
    }

    private String simplifyMessage(String message) {
        if (message == null) {
            return "";
        }
        if (message.contains("[NESQL] Stage ")) {
            Matcher matcher = STAGE_PATTERN.matcher(message);
            if (matcher.find()) {
                return "Stage " + matcher.group(1) + "/" + matcher.group(2) + "  " + humanizeStage(matcher.group(3));
            }
        }
        if (message.contains("[NESQL] Stage complete: ")) {
            return message.replace("[NESQL] Stage complete: ", "Done  ");
        }
        return message;
    }

    private String humanizeStage(String stage) {
        if (stage == null) {
            return "-";
        }
        String[] parts = stage.replace('_', ' ').toLowerCase().split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
