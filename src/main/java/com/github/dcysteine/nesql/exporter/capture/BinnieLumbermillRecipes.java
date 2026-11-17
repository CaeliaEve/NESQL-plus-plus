package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Binnie native map lookup and conditional machine inventory changes, without NEI pages. */
final class BinnieLumbermillRecipes implements RegistryRecipes {
  private static final String HANDLER = "binnie.extratrees.nei.NEIHandlerLumbermill";
  private LumbermillSource source;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  BinnieLumbermillRecipes(TemplateRecipeHandler handler) {
    this(handler, new LumbermillSource());
  }

  BinnieLumbermillRecipes(
      TemplateRecipeHandler handler,
      Map<?, ?> registry,
      ItemStack bark,
      ItemStack dust,
      FluidStack water) {
    this(handler, new LumbermillSource(registry, bark, dust, water));
  }

  private BinnieLumbermillRecipes(TemplateRecipeHandler handler, LumbermillSource source) {
    this.source = source;
    try {
      if (!supports(handler))
        throw new Jobs.Fault("handler_unsupported", "Unknown lumbermill handler");
      source.verify();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    verify();
    return source.rows.size();
  }

  public void verify() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Lumbermill reader closed");
    source.verifyRoots();
  }

  public void finishVerification() {
    verify();
    source.verify();
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (source != null) source.close();
    source = null;
  }

  public boolean capture(int index, RecipeRow row) {
    verify();
    LumbermillSource.Row entry = source.rows.get(index);
    if (entry.shadowed)
      return row.exclude(
          object(
              "native",
              HANDLER,
              "reason",
              "earlier_map_entry_matches_same_item_and_literal_metadata",
              "nativeIndex",
              index));
    ItemStack input = entry.input.copy();
    input.stackSize = 1;
    input.setTagCompound(null);
    row.itemInput(0,
        Collections.singletonList(
            new RecipeRow.Ingredient(
                input, 1, false, object("kind", "wildcard", "meta", false, "nbt", true))),
        false);
    FluidStack gate = source.water.copy();
    gate.amount = 5;
    row.fluidInput(1, gate, false, object("kind", "lumbermillTank"));
    for (com.google.gson.JsonElement raw : row.inputs)
      raw.getAsJsonObject()
          .getAsJsonArray("choices")
          .get(0)
          .getAsJsonObject()
          .add("consume", object("kind", "allocated"));
    row.itemOutput(0,
        entry.output.copy(),
        10000,
        Amounts.potential("binnie:lumbermillOutputState", "nativeLumbermillSlotAccepts", null, 6));
    row.itemOutput(1,
        source.bark.copy(),
        10000,
        Amounts.potential("binnie:lumbermillOutputState", "nativeLumbermillSlotAccepts", null, 1));
    row.itemOutput(2,
        source.dust.copy(),
        10000,
        Amounts.potential("binnie:lumbermillOutputState", "nativeLumbermillSlotAccepts", null, 1));
    row.record.add(
        "process",
        object(
            "kind",
            "binnieLumbermill",
            "version",
            "2.5.24",
            "workTicks",
            30,
            "startTicks",
            1,
            "queuedTicks",
            30,
            "rfPerWorkTick",
            30,
            "fluidGate",
            5,
            "fluidDrainLimit",
            10,
            "nativeIndex",
            index,
            "nativeCount",
            source.rows.size(),
            "firstOreName",
            entry.firstOre,
            "inputPolicy",
            "normalFirstOreLogWoodRuntimeItemAndLiteralMetadata",
            "tankPolicy",
            "normalWaterAdmissionRuntimeVolumeOnlyPartialDrain",
            "outputPolicy",
            "emptyPrototypeMatchingMergeKeepsTagsBestEffortThenDebit",
            "workPolicy",
            "cachedStateFloatProgressPauseResetAndReselectAtFinish"));
    row.note(
        "binnie:lumbermill",
        "binnie.extratrees.machines.lumbermill.LumbermillComponentLogic",
        row.facts.locale().equals("zh_CN")
            ? "按原生 map 遍历顺序选择同物品和精确元数据的第一项，忽略输入数量和 NBT，包括字面值 32767。正常插入要求首个矿辞名称包含 logWood；已保存库存的运行时加工不再执行该插入限制。储罐正常接收水，但运行时只检查能否取出 5 mB，每个成功工作 tick 最多排出 10 mB，允许部分排出。每步需 30 RF，原生浮点进度为 100/30，首次启动增加 0.01；无中断时首次 31 次更新、连续 30 次，非固定墙钟时长。缺少液体或能量会暂停，不能工作会重置；缓存的进行中标志与保存进度分别存在，完成时重新查询当前木材。三个物品输出槽按 1 木板/2 树皮/3 锯末独立尝试放入，空槽复制原型，同物品元数据合并保留已有 NBT；不兼容或超容量则跳过，仍扣一件木材，不返还容器。木板槽在工作前另有容量检查；实际产物仍取决于完成时槽位状态，图标是空输出槽的原型。"
            : "The first native map entry matching item and literal metadata wins, ignoring input count and NBT, including literal 32767. Normal insertion needs the first ore name to contain logWood; saved runtime inventory does not repeat that admission check. Tank insertion accepts water, but runtime checks only whether 5 mB can be drained; each successful work tick drains up to 10 mB, including a partial drain. Work costs 30 RF per tick with native float progress 100/30 and a 0.01 start increment; uninterrupted fresh/queued cycles take 31/30 updates, not fixed wall time. Missing fluid or power pauses progress; inability to work resets it. Cached working state is separate from saved progress, and completion reselects the current wood. Output slots 1 planks, 2 bark and 3 sawdust independently copy into empty slots or merge matching item/meta while keeping existing NBT. Incompatible or over-capacity placement is skipped but one wood is still debited, without containers. The plank slot also has a pre-work capacity check; actual output depends on completion-time inventory, and the displayed items are empty-slot prototypes.");
    return true;
  }
}
