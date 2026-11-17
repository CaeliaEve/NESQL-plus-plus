package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.client.gui.GuiResearchRecipe;
import thaumcraft.common.CommonProxy;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.research.PlayerKnowledge;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Reader-owned ARI 1.1.6 inputs; never initializes or modifies player knowledge. */
final class AspectRecipeIndexContext {
    private static final String CONFIG = "com.gtnewhorizons.aspectrecipeindex.util.ARIConfig";
    private static final String HOOKS = "com.gtnewhorizons.aspectrecipeindex.client.ThaumcraftHooks";
    private final Minecraft client;
    private final EntityPlayer player;
    private final String username;
    private final CommonProxy proxy;
    private final PlayerKnowledge knowledge;
    private final Map<String, AspectList> aspectRoot;
    private final AspectList discovered;
    private final Map<?, ?> scansRoot;
    private final Object cacheRoot, tagsRoot, groupedTagsRoot;
    private final boolean scanned;
    private final String knowledgeDigest;
    final boolean showUndiscovered;

    static AspectRecipeIndexContext open(boolean scanned) {
        version("AspectRecipeIndexReferences", "aspectrecipeindex");
        version("AspectRecipeIndexReferences", "Thaumcraft");
        return new AspectRecipeIndexContext(scanned);
    }

    private AspectRecipeIndexContext(boolean scanned) {
        this.scanned = scanned;
        client = Minecraft.getMinecraft();
        player = client == null ? null : client.thePlayer;
        if (player == null || Thaumcraft.proxy == null || Thaumcraft.proxy.playerKnowledge == null)
            throw notReady("Native aspect knowledge owner is unavailable");
        username = player.getCommandSenderName();
        if (username == null || username.trim().isEmpty() || username.length() > 1024)
            throw notReady("Native aspect knowledge owner is invalid");
        proxy = Thaumcraft.proxy;
        knowledge = proxy.playerKnowledge;
        aspectRoot = knowledge.aspectsDiscovered;
        discovered = aspectRoot.get(username);
        scansRoot = knowledge.objectsScanned;
        showUndiscovered = showUndiscovered();
        // The native getter can add primal aspects. Require its read-only fixed point.
        if (!scanned && !showUndiscovered && !primalsReady(discovered))
            throw notReady("Native discovered aspects are not initialized");
        knowledgeDigest = scanned ? null : CanonicalJson.digest(Aspects.knowledge(discovered));
        cacheRoot = scanned ? GuiResearchRecipe.cache : null;
        tagsRoot = scanned ? ThaumcraftApi.objectTags : null;
        groupedTagsRoot = scanned ? ThaumcraftApi.groupedObjectTags : null;
        if (scanned && (!loaded() || cacheRoot == null || tagsRoot == null || groupedTagsRoot == null))
            throw notReady("Native scanned-item cache is not ready");
        verify();
    }

    static Object[] registrationAnchors(boolean scanned) {
        if (scanned) return new Object[] { Aspect.aspects, type(CONFIG), type(HOOKS),
                GuiResearchRecipe.cache, ThaumcraftApi.objectTags, ThaumcraftApi.groupedObjectTags };
        return new Object[] { Aspect.aspects, type(CONFIG) };
    }

    boolean discovered(Aspect aspect) {
        return discovered != null && discovered.aspects.containsKey(aspect);
    }

    List<String> scans() {
        verify();
        return knowledge.objectsScanned.get(username);
    }

    void verify() {
        Jobs.checkpoint();
        if (Minecraft.getMinecraft() != client || client.thePlayer != player
                || !username.equals(player.getCommandSenderName()) || Thaumcraft.proxy != proxy
                || proxy.playerKnowledge != knowledge || knowledge.aspectsDiscovered != aspectRoot
                || aspectRoot.get(username) != discovered || knowledge.objectsScanned != scansRoot
                || showUndiscovered() != showUndiscovered)
            throw changed();
        if (scanned && (!loaded() || GuiResearchRecipe.cache != cacheRoot
                || ThaumcraftApi.objectTags != tagsRoot || ThaumcraftApi.groupedObjectTags != groupedTagsRoot))
            throw changed();
        if (!scanned && !knowledgeDigest.equals(CanonicalJson.digest(Aspects.knowledge(discovered))))
            throw changed();
    }

    private static boolean primalsReady(AspectList aspects) {
        if (aspects == null) return false;
        for (Aspect primal : new Aspect[] { Aspect.AIR, Aspect.FIRE, Aspect.WATER,
                Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY })
            if (!aspects.aspects.containsKey(primal)) return false;
        return true;
    }

    private static boolean showUndiscovered() {
        return Boolean.TRUE.equals(field(type(CONFIG), null, "showUndiscoveredAspectRecipes"));
    }

    private static boolean loaded() {
        return Boolean.TRUE.equals(invoke(type(HOOKS), null, "isDataLoaded", new Class<?>[0]));
    }

    private static Jobs.Fault notReady(String message) { return new Jobs.Fault("handler_not_ready", message); }
    private static Jobs.Fault changed() {
        return new Jobs.Fault("environment_changed", "AspectRecipeIndex player, knowledge, configuration or cache changed");
    }
}
