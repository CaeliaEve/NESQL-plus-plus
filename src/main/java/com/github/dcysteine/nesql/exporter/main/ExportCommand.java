package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Minecraft command adapter for the registry-backed NESQL++ export command. */
final class ExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return ExportCommandParser.USAGE;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        ExportCommandDispatcher.dispatch(sender, args);
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
