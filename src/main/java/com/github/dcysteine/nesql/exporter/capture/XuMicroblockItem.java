package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;

/** Read-only native getMicroID, permitting only its equivalent lazy memoization. */
final class XuMicroblockItem implements AutoCloseable {
  private static final String RECIPE = XuMicroblockSource.RECIPE,
      PROXY = "com.rwtema.extrautils.ExtraUtilsProxy";
  private Item expected;
  private Item initialRecipeCache;
  private final boolean checked;
  private Item proxyCache;
  private boolean closed;

  XuMicroblockItem() {
    initialRecipeCache = (Item) field(type(RECIPE), null, "microID");
    checked = (Boolean) field(type(PROXY), null, "checked2");
    proxyCache = (Item) field(type(PROXY), null, "MicroBlockId");
    expected = effective();
    if (expected == null)
      throw new Jobs.Fault("handler_not_ready", "Native XU micro item is unavailable");
    if (Item.itemRegistry.getNameForObject(expected) == null)
      throw fault("Unregistered native XU micro item");
  }

  private Item effective() {
    Item cached = (Item) field(type(RECIPE), null, "microID");
    if (cached != null) return cached;
    if ((Boolean) field(type(PROXY), null, "checked2"))
      return (Item) field(type(PROXY), null, "MicroBlockId");
    // Constructor pins ForgeMultipart's loaded version; native FMPBase performs
    // this exact registry lookup, then writes both caches. We do neither write.
    MagicApi.version("XuMicroblockItem","ForgeMultipart");
    Object item = Item.itemRegistry.getObject("ForgeMicroblock:microblock");
    if (item != null && !(item instanceof Item))
      throw fault("Invalid native XU micro registry entry");
    return (Item) item;
  }

  Item item() {
    check();
    return expected;
  }

  void verify() {
    check();
    Item cached = (Item) field(type(RECIPE), null, "microID");
    if (initialRecipeCache != null) {
      if (cached != initialRecipeCache) throw changed();
      return;
    }
    if (cached != null) {
      if (cached != expected) throw changed();
      return;
    }
    boolean current = (Boolean) field(type(PROXY), null, "checked2");
    Item value = (Item) field(type(PROXY), null, "MicroBlockId");
    if (checked) {
      if (!current || value != proxyCache) throw changed();
    } else if (current) {
      if (value != expected) throw changed();
      return;
    } else if (value != proxyCache) throw changed();
    if (effective() != expected) throw changed();
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "XU micro item binding is closed");
  }

  public void close() {
    closed = true;
    expected = null;
    initialRecipeCache = null;
    proxyCache = null;
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault("recipe_changed", "Native XU micro item or provider changed");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
