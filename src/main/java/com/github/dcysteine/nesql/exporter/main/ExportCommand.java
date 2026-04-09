package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Command to export recipes and other data to a file. */
final class ExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql [filename suffix]";
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

        Exporter exporter;
        if (args.length == 1) {
            exporter = new Exporter(args[0]);
        } else {
            exporter = new Exporter();
        }

        // Show progress GUI before starting export
        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("Full Export / v1.04 / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Starting export...");

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

    @Override
    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return this.getCommandName().compareTo(((ICommand) other).getCommandName());
        }

        return 0;
    }
}
