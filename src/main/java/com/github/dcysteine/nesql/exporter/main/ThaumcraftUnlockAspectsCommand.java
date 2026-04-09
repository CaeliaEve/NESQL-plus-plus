package com.github.dcysteine.nesql.exporter.main;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.util.EnumChatFormatting;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketSyncAspects;
import thaumcraft.common.lib.network.playerdata.PacketSyncScannedItems;
import thaumcraft.common.lib.research.PlayerKnowledge;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ScanManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unlocks Thaumcraft item scanning and aspect discovery for a player. */
final class ThaumcraftUnlockAspectsCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "nesql-tc-unlock-aspects";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/nesql-tc-unlock-aspects [player]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            Logger.chatMessage(EnumChatFormatting.RED + "Usage: " + getCommandUsage(sender));
            return;
        }

        EntityPlayerMP target;
        try {
            if (args.length == 1) {
                target = getPlayer(sender, args[0]);
            } else {
                target = getCommandSenderAsPlayer(sender);
            }
        } catch (Exception e) {
            Logger.chatMessage(EnumChatFormatting.RED + "Target player not found.");
            return;
        }

        UnlockResult result = unlockThaumcraftKnowledge(target);

        Logger.chatMessage(EnumChatFormatting.GREEN + "Thaumcraft item aspects unlocked for " + target.getCommandSenderName());
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Discovered aspects: " + result.discoveredAspects);
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Scanned item entries: " + result.scannedObjects);
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    private UnlockResult unlockThaumcraftKnowledge(EntityPlayerMP player) {
        String playerName = player.getCommandSenderName();
        UnlockResult result = new UnlockResult();

        PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();
        if (knowledge == null) {
            return result;
        }

        for (Aspect aspect : collectAllAspects()) {
            if (aspect != null && knowledge.addDiscoveredAspect(playerName, aspect)) {
                result.discoveredAspects++;
            }
        }

        Set<String> scanKeys = collectItemScanKeys();
        for (String scanKey : scanKeys) {
            if (ResearchManager.completeScannedObjectUnsaved(playerName, scanKey)) {
                result.scannedObjects++;
            }
        }

        ResearchManager.scheduleSave(player);
        SimpleNetworkWrapper packetBus = PacketHandler.INSTANCE;
        packetBus.sendTo(new PacketSyncAspects(player), player);
        packetBus.sendTo(new PacketSyncScannedItems(player), player);
        return result;
    }

    private List<Aspect> collectAllAspects() {
        List<Aspect> aspects = new ArrayList<>();
        try {
            Field aspectsField = Aspect.class.getDeclaredField("aspects");
            aspectsField.setAccessible(true);
            Object value = aspectsField.get(null);
            if (value instanceof Map) {
                for (Object aspect : ((Map<?, ?>) value).values()) {
                    if (aspect instanceof Aspect) {
                        aspects.add((Aspect) aspect);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return aspects;
    }

    private Set<String> collectItemScanKeys() {
        Set<String> scanKeys = new LinkedHashSet<>();
        addScanKeysFromTagMap(scanKeys, ThaumcraftApi.objectTags);
        addScanKeysFromGroupedTagMap(scanKeys, ThaumcraftApi.groupedObjectTags);
        return scanKeys;
    }

    private void addScanKeysFromTagMap(Set<String> scanKeys, Map<List, ?> tagMap) {
        if (tagMap == null) {
            return;
        }
        for (List key : tagMap.keySet()) {
            String scanKey = toScanKey(key);
            if (scanKey != null) {
                scanKeys.add(scanKey);
            }
        }
    }

    private void addScanKeysFromGroupedTagMap(Set<String> scanKeys, Map<List, int[]> tagMap) {
        if (tagMap == null) {
            return;
        }
        for (List key : tagMap.keySet()) {
            String scanKey = toScanKey(key);
            if (scanKey != null) {
                scanKeys.add(scanKey);
            }
        }
    }

    private String toScanKey(List key) {
        if (key == null || key.size() < 2) {
            return null;
        }
        Object itemObj = key.get(0);
        Object metaObj = key.get(1);
        if (!(itemObj instanceof Item) || !(metaObj instanceof Number)) {
            return null;
        }
        int hash = ScanManager.generateItemHash((Item) itemObj, ((Number) metaObj).intValue());
        return "@" + hash;
    }

    private static final class UnlockResult {
        int discoveredAspects;
        int scannedObjects;
    }
}
