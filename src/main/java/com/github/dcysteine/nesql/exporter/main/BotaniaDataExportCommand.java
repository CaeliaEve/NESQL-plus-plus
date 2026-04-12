package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

/** Debug command to re-export Botania-family NEI data without running full nesql-data. */
final class BotaniaDataExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql-data-botania";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql-data-botania [filename suffix]";
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

        DataExporter exporter = args.length == 1
                ? DataExporter.botaniaDebug(args[0])
                : DataExporter.botaniaDebug();

        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("Data Export / Botania debug / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Starting Botania-family debug data export...");
        gui.addMessage(EnumChatFormatting.YELLOW + "Note: /nesql-data remains the full export command");

        Minecraft.getMinecraft().displayGuiScreen(gui);

        new Thread(() -> {
            try {
                exporter.exportReportException();
            } finally {
                try {
                    Thread.sleep(3000);
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
