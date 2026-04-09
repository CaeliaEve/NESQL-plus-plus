package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Command to export block face texture metadata for 3D multiblock rendering. */
final class BlockFaceExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql-blockfaces";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql-blockfaces [repository suffix]  // exports per-face texture + UV metadata";
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

        BlockFaceExporter exporter = args.length == 1 ? new BlockFaceExporter(args[0]) : new BlockFaceExporter();

        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("Block Face Export / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Starting block face export...");
        gui.addMessage("Export target: multiblocks/block-face-textures.json.gz");
        gui.addMessage("Also exports textures to image/blockface/ and includes per-face UV.");

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
