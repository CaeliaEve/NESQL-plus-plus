package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.task.ExportScreen;
import com.github.dcysteine.nesql.exporter.task.Exports;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

final class ExportCommand extends CommandBase {
    private final Exports exports;
    ExportCommand(Exports exports) { this.exports = exports; }
    @Override public String getCommandName() { return "nesql"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/nesql"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 0) throw new WrongUsageException("/nesql");
        Minecraft.getMinecraft().displayGuiScreen(new ExportScreen(exports));
    }
}
