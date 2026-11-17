package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/** Native BlockMicroMaterial facts without executing its polymorphic getters. */
final class FmpMaterialSnapshot implements AutoCloseable {
  private static final String BASE = "codechicken.microblock.BlockMicroMaterial";
  private Object material;
  private Block block;
  private final int metadata;
  private NativeHarvest harvest;
  private ItemStack stack;
  private String signature;

  FmpMaterialSnapshot(Object material) {
    this(material, true);
  }

  FmpMaterialSnapshot(Object material, boolean requireCutter) {
    if (material == null) throw fault("Null native FMP material");
    requireOwner(material, "getItem", BASE);
    requireOwner(material, "block", BASE);
    requireOwner(material, "meta", BASE);
    String cutter = requireCutter ? owner(material, "getCutterStrength") : null;
    if (requireCutter
        && !cutter.equals(BASE)
        && !cutter.equals("mrtjp.projectred.illumination.LightMicroMaterial"))
      throw fault("Unadapted FMP cutter callback: " + cutter);
    if (requireCutter && cutter.equals("mrtjp.projectred.illumination.LightMicroMaterial"))
      MagicApi.version("FmpMaterialSnapshot","ProjRed|Illumination");
    this.material = material;
    Object nativeBlock = field(material, "block");
    if (!(nativeBlock instanceof Block)) throw fault("Invalid FMP native material block");
    block = (Block) nativeBlock;
    metadata = (Integer) field(material, "meta");
    // The native getItem body constructs exactly this owned stack. The
    // native cutter body passes Java's signed metadata remainder, not a clamp.
    stack = new ItemStack(block, 1, metadata);
    NativeStackAudit.audit(stack);
    if (requireCutter) harvest = new NativeHarvest(block, metadata % 16);
    signature = CanonicalJson.digest(facts());
  }

  ItemStack stack() {
    verify();
    return stack.copy();
  }

  int cutter() {
    verify();
    if (harvest == null) throw fault("Item-only FMP material snapshot has no cutter observation");
    return harvest.level();
  }

  private JsonObject facts() {
    JsonObject facts =
        object(
            "owner",
            material.getClass().getName(),
            "rawMetadata",
            metadata,
            "stack",
            RecipeRow.stackEvidence(stack));
    if (harvest != null) facts.addProperty("cutter", harvest.level());
    return facts;
  }

  void verify() {
    if (material == null) throw new Jobs.Fault("capture_closed", "FMP material snapshot is closed");
    Jobs.checkpoint();
    if (field(material, "block") != block || ((Integer) field(material, "meta")) != metadata)
      throw changed();
    ItemStack current = new ItemStack(block, 1, metadata);
    NativeStackAudit.audit(current);
    if (current.getItem() != stack.getItem()
        || !CanonicalJson.digest(RecipeRow.stackEvidence(current))
            .equals(CanonicalJson.digest(RecipeRow.stackEvidence(stack)))
        || !signature.equals(CanonicalJson.digest(facts()))) throw changed();
    if (harvest != null) harvest.verify();
  }

  public void close() {
    if (material == null) return;
    material = null;
    block = null;
    if (harvest != null) harvest.close();
    harvest = null;
    stack = null;
    signature = null;
  }

  private static String owner(Object value, String name) {
    try {
      Method method = value.getClass().getMethod(name);
      return method.getDeclaringClass().getName();
    } catch (NoSuchMethodException error) {
      throw fault("Missing FMP native getter: " + name);
    }
  }

  private static void requireOwner(Object value, String method, String expected) {
    String owner = owner(value, method);
    if (!owner.equals(expected))
      throw fault("Unadapted FMP material callback: " + method + " / " + owner);
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "FMP native material fields, block item or cutter source changed");
  }
}
