package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeCellQueries.call;
import net.minecraft.item.*;
import java.util.*;

/** Native Scala delegates, including ink descriptor identity and invalid metadata fallback. */
final class NativeReturnedOcTest {
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("OpenComputers", "1.11.20-GTNH");
      Item parent = NativeCoreFixesTest.item("li.cil.oc.common.init.Items$$anon$1", "returned_oc_items");
      Object buffer = type("scala.collection.mutable.ArrayBuffer").newInstance();
      scope.replace(type("li.cil.oc.common.item.Delegator"), parent, "subItems", buffer);
      Object api = field(type("li.cil.oc.common.init.Items$"), null, "MODULE$");
      scope.replace(type("li.cil.oc.api.API"), null, "items", api);
      Object descriptors = type("scala.collection.mutable.HashMap").newInstance();
      Object names = type("scala.collection.mutable.HashMap").newInstance();
      scope.replace(api.getClass(), api, "descriptors", descriptors);
      scope.replace(api.getClass(), api, "names", names);
      int meta = 0;
      for (String name : Arrays.asList("Microchip", "InkCartridgeEmpty", "InkCartridge")) {
        Class<?> owner = type("li.cil.oc.common.item." + name);
        Object delegate = scope.allocate(owner);
        scope.replace(owner, delegate, "parent", parent);
        scope.replace(owner, delegate, "itemId", meta++);
        call(buffer, "$plus$eq", new Class<?>[]{Object.class}, delegate);
        String id = name.equals("Microchip") ? "microchip" : name.equals("InkCartridge") ? "inkCartridge" : "inkCartridgeEmpty";
        Object descriptor = type("li.cil.oc.common.init.Items$$anon$6")
            .getConstructor(type("li.cil.oc.common.item.traits.Delegate"), String.class).newInstance(delegate, id);
        call(names, "put", new Class<?>[]{Object.class, Object.class}, delegate, id);
        call(descriptors, "put", new Class<?>[]{Object.class, Object.class}, id, descriptor);
      }
      for (int damage : new int[]{0, 1, 2, 3, -1, 32767}) {
        ItemStack input = new ItemStack(parent, 1, damage);
        ItemStack expected = parent.hasContainerItem(input) ? parent.getContainerItem(input.copy()) : null;
        ItemStack actual = ItemCallbacks.container(input, false);
        if (!ItemStack.areItemStacksEqual(expected, actual))
          throw new AssertionError("OC container differed at metadata " + damage);
      }
      call(descriptors, "remove", new Class<?>[]{Object.class}, "inkCartridge");
      ItemStack ink = new ItemStack(parent, 1, 2);
      if (!ItemStack.areItemStacksEqual(parent.getContainerItem(ink.copy()), ItemCallbacks.container(ink, false)))
        throw new AssertionError("OC null descriptor equality branch changed");
      Object inkDelegate = call(buffer, "apply", new Class<?>[]{int.class}, 2);
      call(names, "put", new Class<?>[]{Object.class, Object.class}, inkDelegate, "microchip");
      if (ItemCallbacks.container(ink, false) != null)
        throw new AssertionError("OC ink descriptor identity gate was bypassed");
    }
    System.out.println("OC native default delegates, ink return identity, metadata bounds and changed registry passed");
  }
}
