package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Concrete offered-stack binding of the installed GT/IC2 crafting electric stage. It never
 * substitutes consumption count for offered count. Unknown callbacks fail before invocation; all
 * native charge/discharge operations use owned stacks.
 */
final class GtElectricBindings implements GtCraftingBinding {
  private static final String BASE = "gregtech.api.items.MetaBaseItem";
  private static final String META = "gregtech.api.items.MetaGeneratedItem";
  private static final String TOOL = "gregtech.api.items.MetaGeneratedTool";
  private static final String IC2 = "ic2.core.item.BaseElectricItem";
  private static final String ARMOR = "ic2.core.item.armor.ItemArmorElectric";
  private static final String RAW = "ic2.core.item.ElectricItemManager";
  private static final String GATEWAY = "ic2.core.item.GatewayElectricItemManager";
  private final ItemStack specimen;
  private final boolean output;
  private final boolean ic2Advanced;
  private final List<Object> anchors = new ArrayList<>();
  private final List<GtItemNormalization> normalizers = new ArrayList<>();
  private final JsonObject declaration;
  private final String signature;

  GtElectricBindings(ItemStack specimen, boolean output) {
    this(specimen, output, false);
  }
  /**
   * Share the callback audit, but keep Advanced's double/additive stage separate from GT discharge.
   */
  static GtElectricBindings ic2Advanced(ItemStack specimen, boolean output) {
    return new GtElectricBindings(specimen, output, true);
  }

  private GtElectricBindings(ItemStack specimen, boolean output, boolean ic2Advanced) {
    if (specimen == null || specimen.getItem() == null || (!ic2Advanced && specimen.stackSize <= 0))
      throw fault("Native electric stage needs a positive concrete offered stack");
    if (ic2Advanced) NativeStackAudit.audit(specimen);
    this.specimen = specimen.copy();
    this.output = output;
    this.ic2Advanced = ic2Advanced;
    declaration = read(anchors, normalizers);
    signature = CanonicalJson.digest(declaration);
  }

  JsonObject declaration() {
    return new com.google.gson.JsonParser().parse(declaration.toString()).getAsJsonObject();
  }

  boolean active() {
    return declaration.get("active").getAsBoolean();
  }

  public Item item() {
    return specimen.getItem();
  }

  public String key() {
    return "electric:"
        + ic2Advanced
        + ":"
        + output
        + ":"
        + CanonicalJson.digest(RecipeRow.stackEvidence(specimen));
  }

  public boolean mutable() {
    return !declaration.get("kind").getAsString().equals("nonElectric");
  }

  public void verify() {
    List<Object> current = new ArrayList<>();
    JsonObject actual = read(current, new ArrayList<>());
    if (anchors.size() != current.size() || !signature.equals(CanonicalJson.digest(actual)))
      throw changed();
    for (int index = 0; index < current.size(); index++)
      if (anchors.get(index) != current.get(index)) throw changed();
    for (GtItemNormalization normalizer : normalizers) normalizer.verify();
  }

  int transfer() {
    if (ic2Advanced) throw fault("Advanced electric stage cannot use GT integer transfer");
    if (output) throw fault("Output electric binding is not an input projection");
    verify();
    int amount = active() ? discharge(specimen.copy(), true) : 0;
    verify();
    return amount;
  }

  ItemStack apply(int signedTransfer) {
    if (ic2Advanced) throw fault("Advanced electric stage cannot use GT clear-and-charge");
    if (!output) throw fault("Input electric binding is not an output transformation");
    verify();
    ItemStack owned = specimen.copy();
    return ownedCall(
        () -> {
          if (active()) {
            discharge(owned, false);
            // The original result item may have become its native empty variant.
            GtElectricBindings empty = new GtElectricBindings(owned, true);
            if (signedTransfer > 0)
              invoke(
                  type("gregtech.api.util.GTModHandler"),
                  null,
                  "chargeElectricItem",
                  new Class<?>[] {
                    ItemStack.class, int.class, int.class, boolean.class, boolean.class
                  },
                  owned,
                  signedTransfer,
                  Integer.MAX_VALUE,
                  true,
                  false);
            empty.verify();
          }
          verify();
          return owned;
        });
  }

  double advancedCharge() {
    if (!ic2Advanced || output) throw fault("Invalid IC2 Advanced input electric stage");
    return ownedCall(
        () -> {
          Object manager = field(type("ic2.api.item.ElectricItem"), null, "manager");
          return ((Number)
                  invoke(
                      manager.getClass(),
                      manager,
                      "getCharge",
                      new Class<?>[] {ItemStack.class},
                      specimen.copy()))
              .doubleValue();
        });
  }

  ItemStack advancedResult(double total) {
    if (!ic2Advanced || !output) throw fault("Invalid IC2 Advanced output electric stage");
    ItemStack owned = specimen.copy();
    Item original = owned.getItem();
    return ownedCall(
        () -> {
          Object manager = field(type("ic2.api.item.ElectricItem"), null, "manager");
          invoke(
              manager.getClass(),
              manager,
              "charge",
              new Class<?>[] {
                ItemStack.class, double.class, int.class, boolean.class, boolean.class
              },
              owned,
              total,
              Integer.MAX_VALUE,
              true,
              false);
          if (type("net.minecraftforge.fluids.IFluidContainerItem").isInstance(original)
              && owned.getTagCompound() == null)
            owned.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
          return owned;
        });
  }

  <T> T ownedCall(Supplier<T> operation) {
    verify();
    Map<ThreadLocal<?>, Object> borrowed = new IdentityHashMap<>();
    for (Object anchor : anchors)
      if (anchor instanceof ThreadLocal<?>) {
        ThreadLocal<?> local = (ThreadLocal<?>) anchor;
        borrowed.put(local, local.get());
      }
    try {
      T result;
      try {
        result = operation.get();
      } catch (RuntimeException | Error failure) {
        try {
          verify();
        } catch (RuntimeException | Error verification) {
          if (verification != failure) failure.addSuppressed(verification);
        }
        throw failure;
      }
      verify();
      return result;
    } finally {
      // IC2 armor's native setter writes its item-owned ThreadLocal. Restore
      // the calling thread's value even when a downstream native call fails.
      for (Map.Entry<ThreadLocal<?>, Object> entry : borrowed.entrySet())
        restore(entry.getKey(), entry.getValue());
    }
  }

  private static int discharge(ItemStack owned, boolean simulate) {
    return (Integer)
        invoke(
            type("gregtech.api.util.GTModHandler"),
            null,
            "dischargeElectricItem",
            new Class<?>[] {
              ItemStack.class, int.class, int.class, boolean.class, boolean.class, boolean.class
            },
            owned,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            true,
            simulate,
            true);
  }

  private JsonObject read(List<Object> bindings, List<GtItemNormalization> normalization) {
    Item item = specimen.getItem();
    bindings.add(item);
    if (ic2Advanced) {
      Object gateway = field(type("ic2.api.item.ElectricItem"), null, "manager");
      bindings.add(gateway);
      if (gateway == null) throw notReady("Native IC2 electric gateway is unavailable");
      requireOwner(gateway, "getCharge", GATEWAY, ItemStack.class);
      requireOwner(
          gateway,
          "charge",
          GATEWAY,
          ItemStack.class,
          double.class,
          int.class,
          boolean.class,
          boolean.class);
      requireOwner(
          gateway,
          "discharge",
          GATEWAY,
          ItemStack.class,
          double.class,
          int.class,
          boolean.class,
          boolean.class,
          boolean.class);
    }
    if (!type("ic2.api.item.IElectricItem").isInstance(item)) {
      if (ic2Advanced) {
        if (type("ic2.api.item.ISpecialElectricItem").isInstance(item))
          throw fault("Unadapted IC2 non-electric special manager");
        Object backups = field(type("ic2.api.item.ElectricItem"), null, "backupManagers");
        bindings.add(backups);
        if (backups == null || backups.getClass() != ArrayList.class)
          throw fault("Unadapted native IC2 backup-manager storage");
        // No backup implementation is part of the pinned IC2 distribution.
        // Reject external handles callbacks before invocation, rather than claim zero.
        if (!((List<?>) backups).isEmpty()) {
          Object first = ((List<?>) backups).get(0);
          throw fault(
              "Unadapted IC2 Advanced backup electric manager: "
                  + (first == null ? "null" : first.getClass().getName()));
        }
      }
      return object("active", false, "kind", "nonElectric");
    }
    JsonObject stats = statistics(item, specimen, bindings);
    int tier =
        (Integer)
            invoke(
                item.getClass(),
                item,
                "getTier",
                new Class<?>[] {ItemStack.class},
                specimen.copy());
    if (!ic2Advanced && tier == Integer.MAX_VALUE)
      return object("active", false, "kind", "noElectricStats", "statistics", stats);
    Object manager = field(type("ic2.api.item.ElectricItem"), null, "manager");
    bindings.add(manager);
    if (manager == null) throw notReady("Native electric gateway is unavailable");
    String dispatch =
        owner(
            manager,
            "discharge",
            ItemStack.class,
            double.class,
            int.class,
            boolean.class,
            boolean.class,
            boolean.class);
    if (!dispatch.equals(GATEWAY) && !dispatch.equals(RAW))
      throw fault("Unadapted electric manager: " + dispatch);
    requireOwner(
        manager,
        "charge",
        dispatch,
        ItemStack.class,
        double.class,
        int.class,
        boolean.class,
        boolean.class);
    boolean special = type("ic2.api.item.ISpecialElectricItem").isInstance(item);
    Object resolved;
    if (dispatch.equals(GATEWAY) && special) {
      requireOwner(item, "getManager", BASE, ItemStack.class);
      resolved =
          invoke(
              item.getClass(),
              item,
              "getManager",
              new Class<?>[] {ItemStack.class},
              specimen.copy());
      if (resolved != item) throw fault("Pinned GT special manager is not its native item");
    } else {
      resolved =
          dispatch.equals(RAW)
              ? manager
              : field(type("ic2.api.item.ElectricItem"), null, "rawManager");
      if (resolved == null) throw notReady("Native raw electric manager is unavailable");
      requireOwner(
          resolved,
          "discharge",
          RAW,
          ItemStack.class,
          double.class,
          int.class,
          boolean.class,
          boolean.class,
          boolean.class);
      requireOwner(
          resolved,
          "charge",
          RAW,
          ItemStack.class,
          double.class,
          int.class,
          boolean.class,
          boolean.class);
      if (special)
        throw fault("Raw IC2 gateway for a GT special electric item requires a separate rule");
    }
    bindings.add(resolved);
    if (ic2Advanced) {
      requireOwner(resolved, "getCharge", special ? BASE : RAW, ItemStack.class);
      if (special) requireOwner(resolved, "getRealCharge", BASE, ItemStack.class);
    }
    JsonObject result =
        object(
            "active",
            true,
            "kind",
            special ? "gtSpecial" : "ic2Raw",
            "offeredCount",
            specimen.stackSize,
            "statistics",
            stats,
            "manager",
            dispatch,
            "resolved",
            resolved.getClass().getName());
    if (output) {
      if (special) result.add("transitions", gtTransitions(item, bindings, normalization));
      else result.add("transitions", ic2Transitions(item, bindings));
    }
    return result;
  }

  private static JsonObject statistics(Item item, ItemStack stack, List<Object> bindings) {
    String tierOwner = owner(item, "getTier", ItemStack.class);
    if (tierOwner.equals(BASE)) {
      requireOwner(item, "getMaxCharge", BASE, ItemStack.class);
      String statsOwner = owner(item, "getElectricStats", ItemStack.class);
      if (!statsOwner.equals(META) && !statsOwner.equals(TOOL))
        throw fault("Unadapted GT electric statistics: " + statsOwner);
      if (ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass()
          != Item.class) throw fault("GT electric metadata callback changed");
      Long[] stats;
      if (statsOwner.equals(META)) {
        Object table = field(item, "mElectricStats");
        bindings.add(table);
        if (table == null) throw notReady("GT electric-stat table is unavailable");
        if (table.getClass() != HashMap.class
            && table.getClass() != java.util.concurrent.ConcurrentHashMap.class)
          throw fault("Unadapted GT electric-stat storage callback");
        Object nativeStats = ((Map<?, ?>) table).get((short) stack.getItemDamage());
        bindings.add(nativeStats);
        if (nativeStats != null && !(nativeStats instanceof Long[]))
          throw fault("Malformed GT electric statistics");
        stats = (Long[]) nativeStats;
      } else
        stats =
            (Long[])
                invoke(
                    item.getClass(),
                    item,
                    "getElectricStats",
                    new Class<?>[] {ItemStack.class},
                    stack.copy());
      JsonArray values = array();
      if (stats != null) {
        if (stats.length != 4) throw fault("Malformed native GT electric-stat width");
        for (Long number : stats) {
          if (number == null) throw fault("Null native electric statistic");
          values.add(value(Long.toString(number)));
        }
      }
      return object("owner", statsOwner, "values", stats == null ? null : values);
    }
    if (!tierOwner.equals(IC2) && !tierOwner.equals(ARMOR))
      throw fault("Unadapted electric tier callback: " + tierOwner);
    requireOwner(item, "getMaxCharge", tierOwner, ItemStack.class);
    return object(
        "owner",
        tierOwner,
        "maxCharge",
        Double.toHexString(((Number) field(item, "maxCharge")).doubleValue()),
        "tier",
        ((Number) field(item, "tier")).intValue(),
        "transferLimit",
        Double.toHexString(((Number) field(item, "transferLimit")).doubleValue()));
  }

  private JsonArray gtTransitions(
      Item item, List<Object> bindings, List<GtItemNormalization> normalization) {
    requireOwner(
        item,
        "charge",
        BASE,
        ItemStack.class,
        double.class,
        int.class,
        boolean.class,
        boolean.class);
    requireOwner(
        item,
        "discharge",
        BASE,
        ItemStack.class,
        double.class,
        int.class,
        boolean.class,
        boolean.class,
        boolean.class);
    requireOwner(item, "getRealCharge", BASE, ItemStack.class);
    requireOwner(item, "setCharge", BASE, ItemStack.class, long.class);
    if (ItemCallbacks.method(item, "setDamage", "setDamage", ItemStack.class, int.class)
            .getDeclaringClass()
        != Item.class) throw fault("Unadapted GT electric damage setter");
    JsonArray result = array();
    for (String callback : Arrays.asList("getEmptyMetaData", "getChargedMetaData")) {
      String callbackOwner = owner(item, callback, ItemStack.class);
      if (!callbackOwner.equals(BASE) && !callbackOwner.equals(TOOL))
        throw fault("Unadapted GT electric metadata transition: " + callbackOwner);
      ItemStack target = specimen.copy();
      int meta =
          ((Number)
                  invoke(item.getClass(), item, callback, new Class<?>[] {ItemStack.class}, target))
              .intValue();
      target.setItemDamage(meta);
      JsonObject stats = statistics(item, target, bindings);
      GtItemNormalization bound = new GtItemNormalization(target);
      normalization.add(bound);
      result.add(
          object(
              "callback",
              callback,
              "owner",
              callbackOwner,
              "metadata",
              meta,
              "statistics",
              stats,
              "normalization",
              bound.declaration()));
    }
    return result;
  }

  private JsonArray ic2Transitions(Item item, List<Object> bindings) {
    JsonArray result = array();
    for (String callback : Arrays.asList("getEmptyItem", "getChargedItem")) {
      String callbackOwner = owner(item, callback, ItemStack.class);
      if (!callbackOwner.equals(IC2)
          && !callbackOwner.equals(ARMOR)
          && !(callback.equals("getEmptyItem") && callbackOwner.equals("ic2.core.item.ItemBattery"))
          && !(callback.equals("getChargedItem")
              && callbackOwner.equals("ic2.core.item.ItemBatteryDischarged")))
        throw fault("Unadapted IC2 item transition: " + callbackOwner);
      if (callbackOwner.equals("ic2.core.item.ItemBattery")
          || callbackOwner.equals("ic2.core.item.ItemBatteryDischarged")) {
        for (String name : Arrays.asList("chargedReBattery", "reBattery")) {
          Object entry = field(type("ic2.core.Ic2Items"), null, name);
          bindings.add(entry);
          if (!(entry instanceof ItemStack) || ((ItemStack) entry).getItem() == null)
            throw notReady("IC2 battery transition is unavailable: " + name);
          bindings.add(((ItemStack) entry).getItem());
        }
      }
      Item target =
          (Item)
              invoke(
                  item.getClass(),
                  item,
                  callback,
                  new Class<?>[] {ItemStack.class},
                  specimen.copy());
      bindings.add(target);
      if (target == null) throw fault("Native electric transition returned no item");
      ItemStack targetStack = specimen.copy();
      targetStack.func_150996_a(target);
      JsonObject stats =
          type("ic2.api.item.IElectricItem").isInstance(target)
              ? statistics(target, targetStack, bindings)
              : null;
      damageCallbacks(target, bindings);
      result.add(
          object(
              "callback",
              callback,
              "owner",
              callbackOwner,
              "targetClass",
              target.getClass().getName(),
              "statistics",
              stats,
              "maxDamage",
              targetStack.getMaxDamage()));
    }
    return result;
  }

  private static void damageCallbacks(Item item, List<Object> bindings) {
    if (ItemCallbacks.method(item, "getMaxDamage", "getMaxDamage", ItemStack.class)
                .getDeclaringClass()
            != Item.class
        || ItemCallbacks.method(item, "getMaxDamage", "func_77612_l").getDeclaringClass()
            != Item.class) throw fault("Unadapted native electric damage capacity");
    if (type("ic2.api.item.ICustomDamageItem").isInstance(item)) {
      requireOwner(item, "getMaxCustomDamage", ARMOR, ItemStack.class);
      requireOwner(item, "setCustomDamage", ARMOR, ItemStack.class, int.class);
      if (!ItemCallbacks.method(item, "setDamage", "setDamage", ItemStack.class, int.class)
          .getDeclaringClass()
          .getName()
          .equals(ARMOR)) throw fault("Unadapted native electric armor damage callback");
      Object local = field(item, "allowDamaging");
      if (local == null || local.getClass() != ThreadLocal.class)
        throw fault("Unadapted native electric armor temporary flag");
      bindings.add(local);
    } else if (ItemCallbacks.method(item, "setDamage", "setDamage", ItemStack.class, int.class)
            .getDeclaringClass()
        != Item.class) throw fault("Unadapted native electric damage setter");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ThreadLocal local, Object previous) {
    if (previous == null) local.remove();
    else local.set(previous);
  }

  private static String owner(Object target, String name, Class<?>... args) {
    try {
      Method method = target.getClass().getMethod(name, args);
      return method.getDeclaringClass().getName();
    } catch (NoSuchMethodException failure) {
      throw fault("Missing electric callback: " + target.getClass().getName() + "." + name);
    }
  }

  private static void requireOwner(Object target, String name, String expected, Class<?>... args) {
    if (!owner(target, name, args).equals(expected))
      throw fault("Unadapted electric callback: " + target.getClass().getName() + "." + name);
  }

  private static Jobs.Fault notReady(String message) {
    return new Jobs.Fault("handler_not_ready", message);
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "Native electric callbacks/managers/configuration changed");
  }
}
