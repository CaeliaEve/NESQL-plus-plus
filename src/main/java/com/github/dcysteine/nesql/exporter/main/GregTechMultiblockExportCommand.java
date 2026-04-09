package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Command to export GregTech multiblock voxel blueprints for 3D frontend viewing. */
final class GregTechMultiblockExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql-multiblocks";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql-multiblocks [repository suffix]  // exports gregtech multiblock voxel blueprints";
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

        GregTechMultiblockExporter exporter =
                args.length == 1 ? new GregTechMultiblockExporter(args[0]) : new GregTechMultiblockExporter();

        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("GT Multiblock Export / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Starting GregTech multiblock export...");
        gui.addMessage("Export target: multiblocks/gregtech-multiblocks.json.gz");

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
