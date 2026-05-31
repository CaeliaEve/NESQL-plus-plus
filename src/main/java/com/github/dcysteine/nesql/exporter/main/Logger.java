package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.sql.Plugin;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;

public final class Logger {
    public static final org.apache.logging.log4j.Logger MOD = LogManager.getLogger(Main.MOD_NAME);

    private Logger() {}

    public static org.apache.logging.log4j.Logger getLogger(Plugin plugin) {
        return LogManager.getLogger(String.format("%s/%s", Main.MOD_NAME, plugin.getName()));
    }

    public static void chatMessage(String message) {
        boolean echoToChat = shouldEchoToChat(message);
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (echoToChat && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(message));
            }
        } catch (Throwable e) {
            MOD.warn("Failed to send chat message: " + e.getMessage());
        }

        try {
            ExportProgressGui gui = ExportProgressGui.getActiveGui();
            if (gui != null) {
                gui.addMessage(message);
            }
        } catch (Throwable e) {
            MOD.warn("Failed to update GUI: " + e.getMessage());
        }
    }

    private static boolean shouldEchoToChat(String message) {
        if (message == null) {
            return false;
        }
        String clean = message.replaceAll("\u00A7.", "").toLowerCase();
        return clean.contains("opened nesql++ export selection")
                || clean.contains("starting nesql")
                || clean.contains("begin export")
                || clean.contains("export complete")
                || clean.contains("pipeline runtime")
                || clean.contains("export failed")
                || clean.contains("initializing item index")
                || clean.contains("scanning nei item index")
                || clean.contains("discovered")
                || clean.contains("building export context")
                || clean.contains("root cause")
                || clean.contains("debug report")
                || clean.contains("cannot create repository")
                || clean.contains("failed to create repository")
                || clean.contains("something went wrong");
    }

    /** Returns whether we should log a message, given the configured logging interval. */
    public static boolean intermittentLog(int count) {
        int loggingFrequency = ConfigOptions.LOGGING_FREQUENCY.get();
        if (loggingFrequency <= 0) {
            return false;
        }

        return count % loggingFrequency == 0;
    }

    /**
     * Logs a message at the configured logging interval.
     *
     * @param logger the logger to use
     * @param formatString a format string containing exactly one {@code "{}"}, which will be
     *                     replaced with {@code count}
     * @param count a count of the current progress (# of things processed)
     * @return whether the message was logged
     */
    public static boolean intermittentLog(
            org.apache.logging.log4j.Logger logger, String formatString, int count) {
        if (intermittentLog(count)) {
            logger.info(formatString, count);
            return true;
        }
        return false;
    }
}
