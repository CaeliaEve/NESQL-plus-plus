package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

/** Command to export data (items, recipes) without rendering images. */
final class DataExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql-data";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql-data [filename suffix]";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            Logger.chatMessage("Too many parameters! Usage: " + getCommandUsage(sender));
            return;
        }

        DataExporter exporter;
        if (args.length == 1) {
            exporter = new DataExporter(args[0]);
        } else {
            exporter = new DataExporter();
        }

        // Show progress GUI before starting export
        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("Data Export / v1.04 indexed / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Starting v1.04 data export...");
        gui.addMessage(EnumChatFormatting.YELLOW + "Note: Images will NOT be rendered");
        gui.addMessage(EnumChatFormatting.AQUA + "Output: items + recipes + canonical snapshot");
        gui.addMessage(EnumChatFormatting.AQUA + "Mode: safe split export; existing files are preserved");

        // Open GUI on client side
        Minecraft.getMinecraft().displayGuiScreen(gui);

        // Start export in background thread
        new Thread(() -> {
            try {
                exporter.exportReportException();
            } finally {
                // Close GUI when export is complete
                try {
                    Thread.sleep(3000); // Wait 3 seconds before closing so user can see completion
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ExportProgressGui.clearActiveGui();
                Minecraft.getMinecraft().displayGuiScreen(null);
            }
        }).start();
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender unused) {
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender unused, String[] args) {
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] strings, int i) {
        return false;
    }

    public int compareTo(ICommand other) {
        if (other == null) {
            return 1;
        }
        return this.getCommandName().compareTo(other.getCommandName());
    }

    @Override
    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return compareTo((ICommand) other);
        }
        return 0;
    }
}
