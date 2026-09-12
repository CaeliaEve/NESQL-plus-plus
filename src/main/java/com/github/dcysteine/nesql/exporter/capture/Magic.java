package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.research.PlayerKnowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Public Thaumcraft facts and observed client knowledge. No research completion or scanning actions. */
final class Magic {
    private final List<Aspect> aspects;
    private final List<ResearchItem> research;
    private final Studies studies;
    private final String player = Minecraft.getMinecraft().thePlayer.getCommandSenderName();
    private final PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();

    Magic() {
        if (!Loader.isModLoaded("Thaumcraft")) throw new Jobs.Fault("magic_missing", "The target requires Thaumcraft");
        aspects = new ArrayList<>(new TreeMap<>(Aspect.aspects).values());
        if (aspects.isEmpty() || aspects.size() > 4096) throw new Jobs.Fault("aspect_registry", "Aspect registry is unavailable or exceeds its budget");
        studies = Studies.capture();
        research = studies.all();
    }

    int aspectCount() { return aspects.size(); }
    int researchCount() { return research.size(); }

    void aspect(int index, Facts facts) {
        Aspect aspect = aspects.get(index);
        JsonArray components = new JsonArray();
        if (!aspect.isPrimal()) {
            Aspect[] parts = aspect.getComponents();
            if (parts == null || parts.length != 2) throw new Jobs.Fault("aspect_components", "Compound aspect has no component pair");
            for (Aspect part : parts) components.add(value(Aspects.id(part)));
        }
        AspectList known = knowledge.aspectsDiscovered.get(player);
        JsonObject record = object("id", Aspects.id(aspect), "source", Aspects.source(aspect),
                "name", facts.text(aspect.getName()), "description", facts.text(aspect.getLocalizedDescription()),
                "color", uint(Integer.toUnsignedLong(aspect.getColor() | 0xff000000)), "components", components,
                "discovered", known == null ? null : known.aspects.containsKey(aspect), "icon", null);
        facts.row("aspects", record);
        facts.picture(new Facts.Picture(record, "icon", "thaumcraft:aspect/" + aspect.getTag(), () -> UtilsFX.drawTag(0, 0, aspect)));
    }

    void research(int index, Facts facts) {
        ResearchItem study = studies.get(research.get(index).key);
        JsonArray flags = new JsonArray();
        if (study.isAutoUnlock()) flags.add(value("auto"));
        if (study.isConcealed()) flags.add(value("concealed"));
        if (study.isHidden()) flags.add(value("hidden"));
        if (study.isLost()) flags.add(value("lost"));
        if (study.isRound()) flags.add(value("round"));
        if (study.isSecondary()) flags.add(value("secondary"));
        if (study.isSpecial()) flags.add(value("special"));
        if (study.isStub()) flags.add(value("stub"));
        if (study.isVirtual()) flags.add(value("virtual"));
        JsonArray items = new JsonArray(), entities = new JsonArray(), aspectTriggers = new JsonArray();
        if (study.getItemTriggers() != null) for (ItemStack item : study.getItemTriggers()) items.add(value(facts.item(item)));
        if (study.getEntityTriggers() != null) for (String entity : study.getEntityTriggers()) entities.add(value(entity));
        if (study.getAspectTriggers() != null) for (Aspect aspect : study.getAspectTriggers()) aspectTriggers.add(value(Aspects.id(aspect)));
        JsonObject record = object("id", researchId(study.key), "source", origin(study.key), "name", facts.text(study.getName()),
                "text", facts.text(study.getText()), "category", study.category, "categoryName", facts.text(ResearchCategories.getCategoryName(study.category)),
                "position", array(study.displayColumn, study.displayRow), "complexity", study.getComplexity(), "warp", ThaumcraftApi.getWarp(study.key),
                "flags", flags, "completed", completed(study.key), "parents", links(study.parents), "hiddenParents", links(study.parentsHidden), "siblings", links(study.siblings),
                "aspects", Aspects.amounts(study.tags), "itemTriggers", items, "entityTriggers", entities, "aspectTriggers", aspectTriggers,
                "icon", study.icon_item == null ? null : facts.item(study.icon_item), "texture", null);
        if (study.icon_item != null && study.icon_resource != null) throw new Jobs.Fault("research_icon", "Research has two icon sources");
        facts.row("research", record);
        if (study.icon_resource != null) facts.picture(new Facts.Picture(record, "texture", study.icon_resource.toString(), () -> {
            Minecraft.getMinecraft().getTextureManager().bindTexture(study.icon_resource);
            UtilsFX.drawTexturedQuadFull(0, 0, 0);
        }));
    }

    private JsonArray links(String[] keys) {
        JsonArray rows = new JsonArray();
        if (keys == null) return rows;
        for (String key : keys) {
            ResearchItem study = studies.get(key);
            rows.add(object("key", key, "id", study == null ? null : researchId(key), "completed", completed(key)));
        }
        return rows;
    }

    private Boolean completed(String key) {
        List<String> completed = knowledge.researchCompleted.get(player);
        return completed == null ? null : completed.contains(key);
    }

    private static String researchId(String key) { return Identity.origin("research", origin(key)); }
    static JsonObject study(String key) {
        ResearchItem study = Studies.find(key);
        String player = Minecraft.getMinecraft().thePlayer.getCommandSenderName();
        List<String> completed = Thaumcraft.proxy.getPlayerKnowledge().researchCompleted.get(player);
        return object("key", key, "id", study == null ? null : researchId(key), "completed", completed == null ? null : completed.contains(key));
    }
    private static JsonObject origin(String key) { return object("owner", "Thaumcraft", "handler", ResearchItem.class.getName(), "key", key); }
}
