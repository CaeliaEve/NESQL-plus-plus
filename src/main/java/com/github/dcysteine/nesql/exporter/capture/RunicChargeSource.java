package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import thaumcraft.api.IRunicArmor;

/** Source-bound charge observations. Never invokes an arbitrary equipment callback. */
final class RunicChargeSource implements AutoCloseable {
  private static final String BOUND="WayofTime.alchemicalWizardry.common.items.armour.BoundArmour";
  private static final String MOD_ITEMS="WayofTime.alchemicalWizardry.ModItems";
  private static final String UPGRADE="WayofTime.alchemicalWizardry.api.items.interfaces.ArmourUpgrade";
  private static final Set<String> ZERO=new HashSet<>(Arrays.asList(
      "thaumcraft.common.items.armor.ItemFortressArmor","thaumcraft.common.items.armor.ItemThaumiumArmor",
      "thaumcraft.common.items.armor.ItemGoggles","thaumcraft.common.items.armor.ItemBootsTraveller",
      "thaumcraft.common.items.armor.ItemVoidArmor","thaumcraft.common.items.armor.ItemVoidRobeArmor",
      "thaumcraft.common.items.armor.ItemRobeArmor","thaumcraft.common.items.armor.ItemHoverHarness",
      "thaumcraft.common.items.armor.ItemCultistBoots","thaumcraft.common.items.armor.ItemCultistLeaderArmor",
      "thaumcraft.common.items.armor.ItemCultistPlateArmor","thaumcraft.common.items.armor.ItemCultistRobeArmor",
      "thaumcraft.common.items.baubles.ItemAmuletVis","thaumcraft.common.items.baubles.ItemBaubleBlanks",
      "thaumcraft.common.items.baubles.ItemGirdleHover",
      "vazkii.botania.common.item.equipment.bauble.ItemBauble",
      "vazkii.botania.common.item.equipment.armor.manasteel.ItemManasteelArmor",
      "WayofTime.alchemicalWizardry.common.items.thaumcraft.ItemSanguineArmour",
      "witchinggadgets.common.items.armor.ItemPrimordialArmor",
      "emt.item.ItemOneRing","emt.item.ItemIC2Baubles","emt.item.armor.wings.ItemFeatherWing",
      "emt.item.armor.goggles.ItemElectricGoggles","emt.item.armor.chestplate.ItemInfusedQuantumChestplate",
      "emt.item.armor.boots.ItemElectricBootsTraveller",
      "fox.spiteful.forbidden.items.baubles.ItemRingNutrition",
      "taintedmagic.common.items.equipment.ItemVoidwalkerBoots",
      "flaxbeard.thaumicexploration.item.ItemTXArmorSpecial","flaxbeard.thaumicexploration.item.ItemStabilizerBelt",
      "flaxbeard.thaumicexploration.item.ItemBauble",
      "thaumic.tinkerer.common.item.kami.armor.ItemIchorclothArmor","thaumic.tinkerer.common.item.ItemRevealingHelm",
      "com.kentington.thaumichorizons.common.items.ItemAmuletMirror",
      "tuhljin.automagy.items.ItemBaubleEnderDisjunction",
      "tb.common.item.ItemThauminiteArmor","tb.common.item.ItemBloodyArmor",
      "com.gtnewhorizon.randomboubles.items.rings.ItemTerraRing","com.gtnewhorizon.randomboubles.items.rings.ItemPerditioRing",
      "com.gtnewhorizon.randomboubles.items.rings.ItemOrdoRing","com.gtnewhorizon.randomboubles.items.rings.ItemIgnisRing",
      "com.gtnewhorizon.randomboubles.items.rings.ItemAquaRing","com.gtnewhorizon.randomboubles.items.rings.ItemAerRing"));
  private final Map<Item,Provider> providers=new IdentityHashMap<>();
  private final Map<Integer,Item> numericItems=new HashMap<>();
  private final Map<Item,Integer> armorPositions=new IdentityHashMap<>();
  private boolean closed;

  private final class Provider implements AutoCloseable {
    final Item item;final String owner;final NativeDefinitionGraph graph;
    Provider(Item item){
      this.item=item;owner=methodOwner(item,"getRunicCharge",ItemStack.class).getName();
      if(!ZERO.contains(owner)&&!Arrays.asList("thaumcraft.api.ItemRunic","thaumicboots.api.ItemBoots",
          "thaumcraft.common.items.baubles.ItemRingRunic","thaumcraft.common.items.baubles.ItemAmuletRunic",
          "thaumcraft.common.items.baubles.ItemGirdleRunic","taintedmagic.common.items.equipment.ItemVoidwalkerSash",
          "com.gtnewhorizon.randomboubles.items.rings.ItemCombinationRings",BOUND).contains(owner))
        throw fault("Runic charge provider needs an adapter: "+owner);
      if(owner.equals(BOUND)){
        for(String method:new String[]{"getInternalInventory","getMaxBloodShardLevel"})
          if(methodOwner(item,method,ItemStack.class)!=type(BOUND))throw fault("Bound armor overrides its inventory/charge rule");
        if(!Objects.equals(field(type(BOUND),null,"invSize"),9))throw fault("Bound armor's native fixed inventory no longer matches its slot limit");
      }
      graph=new NativeDefinitionGraph(this::roots,Collections.emptyMap());
    }
    private Object[] roots(){
      if(owner.equals("thaumcraft.api.ItemRunic"))return new Object[]{item,field(type(owner),item,"charge")};
      if(owner.equals("thaumicboots.api.ItemBoots"))return new Object[]{item,field(type(owner),item,"runicCharge")};
      if(owner.equals(BOUND))return new Object[]{item,field(type(BOUND),null,"invSize"),
          field(type(MOD_ITEMS),null,"weakBloodShard"),field(type(MOD_ITEMS),null,"demonBloodShard"),armorType(item)};
      return new Object[]{item};
    }
    int base(ItemStack stack,int depth,int[] visits){
      graph.verifyRoots();
      if(ZERO.contains(owner))return 0;
      if(owner.equals("thaumcraft.api.ItemRunic"))return (Integer)field(type(owner),item,"charge");
      if(owner.equals("thaumicboots.api.ItemBoots"))return (Integer)field(type(owner),item,"runicCharge");
      if(owner.equals("taintedmagic.common.items.equipment.ItemVoidwalkerSash"))return 20;
      if(owner.equals(BOUND))return bound(stack,depth,visits);
      if(ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
        throw fault("Runic metadata provider overrides damage access: "+item.getClass().getName());
      int meta=net.minecraft.init.Items.feather.getDamage(stack);
      if(owner.equals("thaumcraft.common.items.baubles.ItemRingRunic"))return meta==0?1:meta==1?5:4;
      if(owner.equals("thaumcraft.common.items.baubles.ItemAmuletRunic"))return meta==0?8:7;
      if(owner.equals("thaumcraft.common.items.baubles.ItemGirdleRunic"))return meta==0?10:9;
      return meta==6?2:meta==7?4:meta==8?6:0;
    }
    public void close(){graph.close();}
  }

  int finalCharge(ItemStack stack){
    alive();NativeStackAudit.audit(stack);
    if(!(stack.getItem() instanceof IRunicArmor))return 0;
    return base(stack,0,new int[1])+hardening(stack);
  }
  private int base(ItemStack stack,int depth,int[] visits){
    Jobs.checkpoint();if(depth>16||++visits[0]>65536)throw fault("Runic nested charge exceeds budget");
    Provider provider=providers.get(stack.getItem());
    if(provider==null){if(providers.size()>=65536)throw fault("Runic provider budget exceeded");provider=new Provider(stack.getItem());providers.put(stack.getItem(),provider);}
    return provider.base(stack,depth,visits);
  }
  static int hardening(ItemStack stack){
    NativeStackAudit.audit(stack);return stack.hasTagCompound()?stack.getTagCompound().getByte("RS.HARDEN"):0;
  }
  private int bound(ItemStack stack,int depth,int[] visits){
    if(!stack.hasTagCompound())return 0;
    NBTTagList list=stack.getTagCompound().getTagList("Inventory",10);
    if(list.tagCount()>4096)throw fault("Bound armor inventory tag exceeds budget");
    ItemStack[] inventory=new ItemStack[9];
    for(int at=0;at<list.tagCount();at++){
      NBTTagCompound stored=list.getCompoundTagAt(at);int slot=stored.getByte("Slot");
      if(slot<0||slot>=9)continue;
      int id=stored.getShort("id");Item item=Item.getItemById(id);
      if(!numericItems.containsKey(id)){if(numericItems.size()>=65536)throw fault("Nested runic registry budget exceeded");numericItems.put(id,item);}
      else if(numericItems.get(id)!=item)throw changed();
      if(item!=null)NativeStackAudit.identityOwner(item);
      // The pinned ItemStack loader only reads id/Count/Damage/tag. The tag is
      // already owned; it is never allowed to alias a borrowed recipe inventory.
      inventory[slot]=ItemStack.loadItemStackFromNBT((NBTTagCompound)stored.copy());
      if(inventory[slot]!=null)NativeStackAudit.audit(inventory[slot]);
    }
    Item weak=(Item)field(type(MOD_ITEMS),null,"weakBloodShard"),demon=(Item)field(type(MOD_ITEMS),null,"demonBloodShard");
    int maximum=0;for(ItemStack inner:inventory)if(inner!=null){
      if(inner.getItem()==weak)maximum=Math.max(maximum,1);
      if(inner.getItem()==demon)maximum=Math.max(maximum,2);
    }
    int count=0,charge=0;
    for(ItemStack inner:inventory){
      if(count>=maximum)break;
      if(inner==null||!type(UPGRADE).isInstance(inner.getItem()))continue;
      if(inner.getItem() instanceof ItemArmor&&armorType(inner.getItem())!=armorType(stack.getItem()))continue;
      if(!inner.hasTagCompound())continue;
      int next=hardening(inner);if(inner.getItem() instanceof IRunicArmor)next+=base(inner,depth+1,visits);
      if(next>0){charge+=next;if(upgrade(inner.getItem()))count++;}
    }
    return charge;
  }
  private static boolean upgrade(Item item){
    String owner=methodOwner(item,"isUpgrade").getName();
    String prefix="WayofTime.alchemicalWizardry.common.items.";
    if(Arrays.asList(prefix+"BloodShard",prefix+"EnergyBattery",prefix+"sigil.SigilDivination",prefix+"sigil.SigilSeer").contains(owner))return false;
    if(Arrays.asList(prefix+"ItemBloodLetterPack",prefix+"thaumcraft.ItemSanguineArmour",prefix+"sigil.SigilAir",
        prefix+"sigil.SigilOfHaste",prefix+"sigil.SigilOfMagnetism",prefix+"sigil.SigilOfGrowth",prefix+"sigil.SigilBloodLight",
        prefix+"sigil.SigilOfTheAssassin",prefix+"sigil.SigilLava",prefix+"sigil.SigilOfTheFastMiner",prefix+"sigil.SigilHarvest",
        prefix+"sigil.SigilOfTheBridge",prefix+"sigil.SigilOfSupression",prefix+"sigil.SigilPackRat",prefix+"sigil.SigilOfWind",
        prefix+"sigil.SigilVoid",prefix+"sigil.SigilWater",
        "com.arc.bloodarsenal.common.items.sigil.SigilSwimming","com.arc.bloodarsenal.common.items.sigil.SigilDivinity").contains(owner))return true;
    throw fault("Bound armor upgrade callback needs an adapter: "+owner);
  }
  private static Class<?> methodOwner(Item item,String method,Class<?>...arguments){
    try{return item.getClass().getMethod(method,arguments).getDeclaringClass();}
    catch(NoSuchMethodException absent){throw fault("Missing native runic callback: "+method);}
  }
  private int armorType(Item item){
    int value=((ItemArmor)item).armorType;
    Integer prior=armorPositions.putIfAbsent(item,value);if(prior!=null&&prior!=value)throw changed();
    if(armorPositions.size()>65536)throw fault("Runic armor position budget exceeded");return value;
  }
  void verify(){alive();for(Provider provider:providers.values())provider.graph.verifyRoots();}
  void finishVerification(){verify();for(Provider provider:providers.values())provider.graph.verify();
    for(Map.Entry<Integer,Item> entry:numericItems.entrySet())if(Item.getItemById(entry.getKey())!=entry.getValue())throw changed();
    for(Map.Entry<Item,Integer> entry:armorPositions.entrySet())if(((ItemArmor)entry.getKey()).armorType!=entry.getValue())throw changed();}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Runic charge source is closed");}
  public void close(){closed=true;for(Provider provider:providers.values())provider.close();providers.clear();numericItems.clear();armorPositions.clear();}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Runic provider or nested item identity changed");}
}
