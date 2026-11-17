package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import com.google.common.cache.*;
import forestry.api.apiculture.IAlleleBeeSpecies;
import java.lang.reflect.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** A real callback loop provides both the decisions and persistent output-cache
 * state. Catches repairing/reparsing on cache hits, skipped catalyst callbacks,
 * low-mana fall-through, and premature output-cache reset. */
final class NativeMagicBeesPoolSessionTest {
  @SuppressWarnings({"unchecked", "rawtypes"})
  static void run(IAlleleBeeSpecies input,IAlleleBeeSpecies output,ItemStack offered,
      Object bee,Class<?> recipeType) throws Exception {
    Class<?> sessionType;
    try { sessionType=Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolSession"); }
    catch(ClassNotFoundException missing){throw new AssertionError("Missing persistent native pool session",missing);}
    Constructor<?> constructor=sessionType.getDeclaredConstructor(JsonObject.class,JsonArray.class,JsonObject.class);
    Method select=sessionType.getDeclaredMethod("select",ItemStack.class,boolean.class,boolean.class,int.class,LongSupplier.class);
    Method outputs=sessionType.getDeclaredMethod("outputs"),take=sessionType.getDeclaredMethod("takeOutput",int.class);
    Method cacheState=sessionType.getDeclaredMethod("cacheState");
    for(AccessibleObject member:new AccessibleObject[]{constructor,select,outputs,take,cacheState})member.setAccessible(true);
    Constructor<?> loaderConstructor=type("forestry.apiculture.genetics.BeeGenome$1").getDeclaredConstructor();
    loaderConstructor.setAccessible(true);CacheLoader loader=(CacheLoader)loaderConstructor.newInstance();
    Clock clock=new Clock();
    LoadingCache cache=CacheBuilder.newBuilder().maximumSize(128).expireAfterAccess(1,TimeUnit.MINUTES).ticker(clock).build(loader);
    JsonArray observations=array();
    try(MagicBeesPoolSource source=new MagicBeesPoolSource();NativeRegistryScope scope=new NativeRegistryScope()){
      Class<?> base=type("vazkii.botania.api.recipe.RecipeManaInfusion");
      ItemStack prototype=offered.copy();prototype.stackSize=1;
      scope.replace(recipeType,bee,"outputCache",prototype);
      scope.replace(base,bee,"output",prototype);scope.replace(base,bee,"mana",10);
      scope.replace(base,bee,"isAlchemy",true);scope.replace(base,bee,"isConjuration",false);
      ItemStack fixed=new ItemStack(Items.diamond,3);
      Object ordinary=base.getConstructor(ItemStack.class,Object.class,int.class).newInstance(fixed,offered.copy(),2);
      Class<?> miniType=type("vazkii.botania.api.recipe.RecipeMiniFlower");
      scope.replace(type("vazkii.botania.api.BotaniaAPI"),null,"internalHandler",
          type("vazkii.botania.common.core.handler.InternalMethodHandler").newInstance());
      Object mini=scope.allocate(miniType);
      ItemStack miniInput=new ItemStack(Items.apple);
      miniInput.setTagInfo("type",new NBTTagInt(7));
      scope.replace(base,mini,"input",miniInput);scope.replace(base,mini,"output",fixed);
      scope.replace(base,mini,"mana",2);scope.replace(base,mini,"isAlchemy",false);
      scope.replace(base,mini,"isConjuration",false);
      Class<?> flower=type("vazkii.botania.common.item.block.ItemBlockSpecialFlower");
      String miniKey=(String)invoke(flower,null,"getType",new Class<?>[]{ItemStack.class},miniInput);
      JsonArray entries=array(object("kind","bee","form","beeDroneGE",
          "inputSpecies",input.getUID(),"outputSpecies",output.getUID(),"alchemy",true,"conjuration",false,"mana",10,
          "displayInput",RecipeRow.stackEvidence(prototype),
          "prototype",RecipeRow.stackEvidence(prototype),"cachedOutput",RecipeRow.stackEvidence(prototype)),
          object("kind","item","predicates",array(object("registry",Item.itemRegistry.getNameForObject(offered.getItem()),
              "metadata",offered.getItemDamage())),"output",RecipeRow.stackEvidence(fixed),
              "alchemy",false,"conjuration",false,"mana",2),
          object("kind","mini","key",miniKey,"output",RecipeRow.stackEvidence(fixed),
              "alchemy",false,"conjuration",false,"mana",2,
              "coercions",array(object("input",RecipeRow.stackEvidence(miniInput),"value",miniKey))));
      recipeBinding(source,bee,recipeType,entries.get(0).getAsJsonObject());
      JsonObject initial;
      try(MagicBeesPoolCache capture=new MagicBeesPoolCache(cache,source)){initial=capture.state();}
      Class<?> transport;
      try{transport=Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolProgram");}
      catch(ClassNotFoundException missing){throw new AssertionError("Missing bounded native bee pool program transport",missing);}
      Method rules=transport.getDeclaredMethod("rules",JsonObject.class,JsonArray.class,JsonObject.class,JsonObject.class);
      rules.setAccessible(true);
      JsonObject runtime=new JsonParser().parse(new String(java.nio.file.Files.readAllBytes(
          java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-cache-runtime.json")),
          java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
      JsonObject programRules=(JsonObject)rules.invoke(null,source.snapshot(),entries,initial,runtime);
      Method chunker=com.github.dcysteine.nesql.exporter.source.Programs.class.getDeclaredMethod(
          "beePool",JsonObject.class,java.util.function.Consumer.class);
      JsonArray chunks=array();
      String program=(String)chunker.invoke(null,programRules,(java.util.function.Consumer<JsonObject>)chunks::add);
      java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-program.json"),
          CanonicalJson.bytes(object("program",program,"rules",programRules,"chunks",chunks)));
      Object session=constructor.newInstance(source.snapshot(),entries,initial);
      scope.replace(type("forestry.apiculture.genetics.BeeGenome"),null,"beeGenomeCache",cache);
      for(int round=0;round<9;round++){
        ItemStack sample=offered.copy();sample.getTagCompound().setInteger("invocation",round%2);
        if(round<2||round>=6)sample.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).getCompoundTagAt(1)
            .setString("UID0","fixture.missing.session.repair");
        if(round==6){
          NBTTagCompound mate=new NBTTagCompound();NBTTagList genes=new NBTTagList();
          NBTTagCompound invalid=new NBTTagCompound();invalid.setByte("Slot",(byte)6);
          invalid.setString("UID0","fixture.invalid.humidity");invalid.setString("UID1","fixture.invalid.humidity");
          genes.appendTag(invalid);mate.setTag("Chromosomes",genes);sample.getTagCompound().setTag("Mate",mate);
        }
        if(round==7)sample=miniInput.copy();
        if(round==8){sample=new ItemStack(Items.diamond);sample.setTagInfo("type",new NBTTagString(miniKey));}
        boolean alchemy=round!=0;int mana=round==1?5:100;
        if(round==4)sample.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10)
            .getCompoundTagAt(0).setString("UID0",output.getUID());
        clock.now=round>=5?60000010000L+round*100:1000+round*100;
        clock.reads=array();
        ItemStack nativeInput=sample.copy();JsonObject result=object("state","notMatched");int visited=0;
        Object[] nativeEntries={bee,ordinary,mini};
        for(int index=0;index<nativeEntries.length;index++){
          Object entry=nativeEntries[index];visited++;
          try{
            if(!(Boolean)invoke(entry.getClass(),entry,"matches",new Class<?>[]{ItemStack.class},nativeInput))continue;
            if((Boolean)field(entry,"isAlchemy")&&!alchemy||(Boolean)field(entry,"isConjuration"))continue;
            int cost=(Integer)field(entry,"mana");
            result=mana<cost?object("state","insufficientMana","index",index):
                object("state","selected","index",index,"output",RecipeRow.stackEvidence(
                    ((ItemStack)invoke(entry.getClass(),entry,"getOutput",new Class<?>[0])).copy()));
          }catch(RuntimeException failure){result=object("state","nativeError","index",index);}
          break;
        }
        JsonObject expected=object("result",result,"offeredAfter",RecipeRow.stackEvidence(nativeInput),"visited",visited);
        JsonArray ticks=clock.reads;int[] at={0};
        LongSupplier replay=()->{
          if(at[0]>=ticks.size())throw new AssertionError("Session over-read native clock");
          return Long.parseLong(ticks.get(at[0]++).getAsString());
        };
        JsonObject actual=(JsonObject)select.invoke(session,sample,alchemy,false,mana,replay);
        JsonArray expectedOutputs=array(RecipeRow.stackEvidence((ItemStack)field(bee,"outputCache")),null,null);
        JsonObject expectedCache;
        try(MagicBeesPoolCache capture=new MagicBeesPoolCache(cache,source)){expectedCache=capture.state();}
        if(!expected.equals(actual)||at[0]!=ticks.size()||!expectedOutputs.equals(outputs.invoke(session))
            ||!expectedCache.equals(cacheState.invoke(session)))
          throw new AssertionError("Persistent pool session differs at invocation "+round+"\n"+expected+"\n"+actual);
        observations.add(object("input",RecipeRow.stackEvidence(sample),"alchemy",alchemy,"conjuration",false,
            "mana",mana,"ticks",ticks,"selection",expected,"outputs",expectedOutputs,"cache",expectedCache));
      }
      ItemStack first=(ItemStack)invoke(recipeType,bee,"getOutput",new Class<?>[0]);
      if(!RecipeRow.stackEvidence(first).equals(take.invoke(session,0))
          ||!RecipeRow.stackEvidence((ItemStack)invoke(recipeType,bee,"getOutput",new Class<?>[0])).equals(take.invoke(session,0)))
        throw new AssertionError("Persistent recipe getOutput did not reset to its constructor prototype");
      try{
        select.invoke(session,offered,true,false,100,(LongSupplier)()->{throw new IllegalStateException("ticker unavailable");});
        throw new AssertionError("Session accepted an unavailable ticker");
      }catch(InvocationTargetException expected){
        if(!(expected.getCause() instanceof IllegalStateException))throw expected;
      }
      for(Method operation:new Method[]{outputs,cacheState}){
        try{operation.invoke(session);throw new AssertionError("Failed pool session remained reusable");}
        catch(InvocationTargetException expected){
          if(!(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault))throw expected;
        }
      }
      source.finishVerification();
      java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-session.json"),
          CanonicalJson.bytes(object("scope","owned-serial-selector-cache-and-output-cache-not-production-admission",
              "source",source.snapshot(),"entries",entries,"cache",initial,"observations",observations)));
    }
    System.out.println("MagicBees pool session: nine native ordered invocations preserve cache repair/hits/expiry,"
        +" catalyst/low-mana effects, source-bound non-string MiniFlower keys and persistent output-cache resets");
  }
  private static void recipeBinding(MagicBeesPoolSource source,Object bee,Class<?> recipeType,JsonObject expected)throws Exception{
    Class<?> binding;
    try{binding=Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolRecipe");}
    catch(ClassNotFoundException missing){throw new AssertionError("Missing read-only native pool callback field binding",missing);}
    Constructor<?> create=binding.getDeclaredConstructor(Object.class,MagicBeesPoolSource.class);
    Method entry=binding.getDeclaredMethod("entry"),verify=binding.getDeclaredMethod("verify");
    for(AccessibleObject member:new AccessibleObject[]{create,entry,verify})member.setAccessible(true);
    Object cached=field(bee,"outputCache"),bound=create.newInstance(bee,source);
    if(!entry.invoke(bound).equals(expected)||field(bee,"outputCache")!=cached)
      throw new AssertionError("Binding called a live callback or changed its output cache");
    verify.invoke(bound);
    try(NativeRegistryScope changed=new NativeRegistryScope()){
      changed.replace(recipeType,bee,"outputCache",((ItemStack)cached).copy());
      try{verify.invoke(bound);throw new AssertionError("Equal-content output-cache replacement bypassed identity drift");}
      catch(InvocationTargetException error){
        if(!(error.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault))throw error;
      }
    }
    verify.invoke(bound);
  }
  private static final class Clock extends com.google.common.base.Ticker {
    long now;JsonArray reads=array();
    @Override public long read(){reads.add(value(Long.toString(now)));return now;}
  }
}
