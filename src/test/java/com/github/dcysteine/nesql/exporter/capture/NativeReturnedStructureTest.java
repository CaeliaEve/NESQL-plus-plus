package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.Probe;
import com.gtnewhorizon.structurelib.StructureLib;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.structure.*;
import java.lang.reflect.Constructor;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

/** Actual pinned providers in an owned preview; no client or live world is used. */
final class NativeReturnedStructureTest {
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(StructureLib.class, null, "itemConstructableTrigger", Items.stick);
      scope.replace(StructureLib.class, null, "proxy", scope.allocate(type("com.gtnewhorizon.structurelib.CommonProxy")));
      scope.replace(ItemList.class, null, "loadFinished", true);
      scope.replace(ItemList.class, null, "items", new ArrayList<ItemStack>());
      // A hidden controller can still be supplied by its verified native source.
      IStructureDefinition<TileEntityFurnace> definition = IStructureDefinition.<TileEntityFurnace>builder()
          .addShape("main", new String[][]{{"c~c"}})
          .addElement('c', StructureUtility.ofBlock(Blocks.furnace, 0)).build();
      Class<?> providerClass = type("net.glease.structurecompat.CompatRailcraft$SimpleMultiblockInfoContainer");
      Constructor<?> constructor = providerClass.getDeclaredConstructor(IStructureDefinition.class,
          int.class, int.class, int.class, String[].class);
      constructor.setAccessible(true);
      IMultiblockInfoContainer<?> provider = (IMultiblockInfoContainer<?>)constructor.newInstance(
          definition, 1, 0, 0, new String[]{"owned supply boundary"});
      preview("minecraft", TileEntityFurnace.class.getCanonicalName(), provider,
          new ItemStack(Blocks.furnace), 3);
      NativeCoreFixesTest.version("AWWayofTime", "1.7.52");
      scope.replace(type("WayofTime.alchemicalWizardry.AlchemicalWizardry"), null, "lifeEssenceFluid",
          net.minecraftforge.fluids.FluidRegistry.WATER);
      Block altar = (Block)type("WayofTime.alchemicalWizardry.common.block.BlockAltar").newInstance();
      Block.blockRegistry.addObject(3998, "fixture:returned_altar", altar);
      ItemBlock altarItem = new ItemBlock(altar);
      net.minecraft.item.Item.itemRegistry.addObject(31998, "fixture:returned_altar", altarItem);
      IStructureDefinition<Object> tierOne = IStructureDefinition.<Object>builder()
          .addShape("tier1", new String[][]{{"a"}})
          .addElement('a', StructureUtility.ofBlock(altar, 0)).build();
      Class<?> altarProvider = type("WayofTime.alchemicalWizardry.compat.structurelib.AltarStructure$AltarMultiblockInfoContainer");
      constructor = altarProvider.getDeclaredConstructor(IStructureDefinition.class);
      constructor.setAccessible(true);
      provider = (IMultiblockInfoContainer<?>)constructor.newInstance(tierOne);
      preview("AWWayofTime", "WayofTime.alchemicalWizardry.common.tileEntity.TEAltar",
          provider, new ItemStack(altarItem), 1);
      rock(scope);
    }
    System.out.println("Returned structures: ordinary compat preview, controller-only tier-one altar and disabled native rock-crusher display passed");
  }
  private static void rock(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("Railcraft", "9.16.33");
    NativeCoreFixesTest.version("structurecompat", "0.7.4");
    NativeBotaniaSpecialTest.active(scope);
    Class<?> proxy = type("mods.railcraft.common.blocks.machine.IMachineProxy");
    Block block = (Block)type("mods.railcraft.common.blocks.machine.BlockMachine")
        .getConstructor(int.class, proxy, boolean.class, int[].class)
        .newInstance(0, type("mods.railcraft.common.blocks.machine.alpha.MachineProxyAlpha").newInstance(), true, new int[16]);
    cpw.mods.fml.common.registry.GameRegistry.registerBlock(block, ItemBlock.class, "returned_rock");
    scope.replace(type("mods.railcraft.common.blocks.RailcraftBlocks"), null, "blockMachineAlpha", block);
    Object machine = field(type("mods.railcraft.common.blocks.machine.alpha.EnumMachineAlpha"), null, "ROCK_CRUSHER");
    if((Boolean)invoke(machine.getClass(),machine,"isAvaliable",new Class<?>[0]))
      throw new AssertionError("Fixture needs the native disabled rock-crusher environment");
    int meta=((Enum<?>)machine).ordinal();
    @SuppressWarnings("unchecked")
    IStructureElement<Object> notBlock=(IStructureElement<Object>)invoke(type("net.glease.structurecompat.StructureUtilityExt"),
        null,"notBlock",new Class<?>[]{Block.class,int.class},block,meta);
    IStructureDefinition<Object> definition=IStructureDefinition.<Object>builder().addShape("main",new String[][]{
        {"    "," xxx "," xxx ","    "},
        {" xx ","xcccx","xcccx"," xx "},
        {" xx ","xcccx","xcccx"," xx "},
        {"    "," xxx "," xxx ","    "}})
        .addElement('c',StructureUtility.ofBlock(block,meta)).addElement('x',notBlock).build();
    Constructor<?> c=type("net.glease.structurecompat.CompatRailcraft$SimpleMultiblockInfoContainer")
        .getDeclaredConstructor(IStructureDefinition.class,int.class,int.class,int.class,String[].class);
    c.setAccessible(true);
    IMultiblockInfoContainer<?> provider=(IMultiblockInfoContainer<?>)c.newInstance(definition,2,2,1,new String[]{"Rock Crusher"});
    preview("Railcraft","mods.railcraft.common.blocks.machine.alpha.TileRockCrusher",provider,new ItemStack(block,1,meta),12);
  }
  private static void preview(String owner, String tile, IMultiblockInfoContainer<?> provider,
      ItemStack controller, int blocks) {
    IMultiblockInfoContainer<?> old = IMultiblockInfoContainer.MULTIBLOCK_MAP.put(tile, provider);
    try {
      Structures.Machine source = Structures.Machine.compat(owner, tile, provider, controller);
      try (Preview preview = new Preview(source, new Probe(1, Collections.emptyMap()), null, true)) {
        int steps=0;while(!preview.build())if(++steps>64)throw new AssertionError("Native preview stalled");
        if(preview.world.blocks.size()!=blocks)throw new AssertionError("Missing native structure cells: "+preview.world.blocks.size());
      }
    } finally {
      if(old==null)IMultiblockInfoContainer.MULTIBLOCK_MAP.remove(tile);
      else IMultiblockInfoContainer.MULTIBLOCK_MAP.put(tile,old);
    }
  }
}
