package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonObject;
import forestry.api.apiculture.*;
import forestry.api.genetics.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Native-law evidence only: this does not admit the still unsupported production callback. */
final class NativeMagicBeesPoolLawTest {
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeBotaniaSpecialTest.active(scope);
      NativeCoreFixesTest.version("MagicBees", "2.9.4-GTNH");
      NativeCoreFixesTest.version("Forestry", "4.10.17");
      try (GendustryNativeSource source = new GendustryNativeSource()) {
        scope.replace(type("magicbees.bees.BeeManager"), null, "beeRoot", source.root);
        IAlleleBeeSpecies input = (IAlleleBeeSpecies) source.root.getTemplate("fixture.gendustry.parentA")[0];
        IAlleleBeeSpecies output = (IAlleleBeeSpecies) source.root.getTemplate("fixture.gendustry.result")[0];
        IIndividual specimen = ForestryMembers.create(source.root, source.root.getTemplate(input.getUID()));
        scope.replace(type("forestry.apiculture.genetics.BeeDefinition"),
            field(type("forestry.apiculture.genetics.BeeDefinition"), null, "FOREST"), "genome", specimen.getGenome());
        ItemStack offered = source.root.getMemberStack(specimen, EnumBeeType.DRONE.ordinal());
        offered.stackSize = 19; offered.setItemDamage(7);
        offered.getTagCompound().setString("unrelated", "retain-native-tags");
        ItemStack ownedRead = offered.copy();
        ownedRead.getTagCompound().getCompoundTag("Genome").setString("fixture-cache-key", "owned-native-read");
        com.google.common.cache.LoadingCache<?, ?> cache = (com.google.common.cache.LoadingCache<?, ?>)
            field(type("forestry.apiculture.genetics.BeeGenome"), null, "beeGenomeCache");
        Map<Object, Object> cacheBefore = cacheSnapshot(cache);
        String readBefore = CanonicalJson.digest(TypedNbt.encode(ownedRead.getTagCompound()));
        forestry.api.apiculture.IBee read = (forestry.api.apiculture.IBee) ForestryMembers.read(source.root, ownedRead);
        if (read.getGenome().getPrimary() != input
            || !cacheBefore.equals(cacheSnapshot(cache))
            || !readBefore.equals(CanonicalJson.digest(TypedNbt.encode(ownedRead.getTagCompound()))))
          throw new AssertionError("Owned bee read modified native genome cache or borrowed NBT");
        ItemStack stateful = offered.copy();
        stateful.getTagCompound().setInteger("Health", 17);
        stateful.getTagCompound().setInteger("MaxH", 31);
        stateful.getTagCompound().setInteger("GEN", 5);
        stateful.getTagCompound().setBoolean("NA", false);
        stateful.getTagCompound().setBoolean("IsAnalyzed", true);
        stateful.getTagCompound().setTag("Mate", stateful.getTagCompound().getCompoundTag("Genome").copy());
        sameNativeRead(source, stateful, cache);
        ItemStack duplicate = offered.copy();
        NBTTagList duplicates = duplicate.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes", 10);
        NBTTagCompound last = (NBTTagCompound) duplicates.getCompoundTagAt(0).copy();
        last.setString("UID0", output.getUID()); last.setString("UID1", output.getUID());
        duplicates.appendTag(last);
        sameNativeRead(source, duplicate, cache);
        ItemStack repaired = offered.copy();
        repaired.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes", 10)
            .getCompoundTagAt(0).setString("UID0", "fixture.unknown.missing.allele");
        scope.replace(type("forestry.core.config.Config"), null, "clearInvalidChromosomes", true);
        sameNativeRead(source, repaired, cache);
        ItemStack fallback = offered.copy(); fallback.setTagCompound(null);
        sameNativeRead(source, fallback, cache);
        Class<?> recipeType = type("magicbees.main.utils.compat.botania.SpeciesRecipeManaInfusion");
        Object recipe = scope.allocate(recipeType);
        // Constructor-owned fields, using real native callback implementations on owned NBT.
        scope.replace(recipeType, recipe, "inputSpecies", input);
        scope.replace(recipeType, recipe, "outputSpecies", output);
        scope.replace(recipeType, recipe, "beeType", EnumBeeType.DRONE);
        ItemStack prototype = offered.copy(); prototype.stackSize = 1;
        scope.replace(type("vazkii.botania.api.recipe.RecipeManaInfusion"), recipe, "input", prototype);
        scope.replace(type("vazkii.botania.api.recipe.RecipeManaInfusion"), recipe, "output", prototype);
        scope.replace(recipeType, recipe, "outputCache", prototype);
        String before = CanonicalJson.digest(TypedNbt.encode(offered.getTagCompound()));
        if (!(Boolean) invoke(recipeType, recipe, "matches", new Class<?>[]{ItemStack.class}, offered))
          throw new AssertionError("Native mana infusion lost its drone/species match");
        ItemStack result = (ItemStack) invoke(recipeType, recipe, "getOutput", new Class<?>[]{});
        if (result == offered || result.stackSize != 1 || result.getItem() != offered.getItem()
            || result.getItemDamage() != 7
            || !result.getTagCompound().getString("unrelated").equals("retain-native-tags")
            || !before.equals(CanonicalJson.digest(TypedNbt.encode(offered.getTagCompound()))))
          throw new AssertionError("Native species conversion did not copy metadata/tags and consume one unit");
        NBTTagList original = offered.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes", 10);
        NBTTagList converted = result.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes", 10);
        if (!converted.getCompoundTagAt(0).getString("UID0").equals(output.getUID())
            || !converted.getCompoundTagAt(0).getString("UID1").equals(output.getUID()))
          throw new AssertionError("Native result lost both replaced species strings");
        if (invoke(recipeType, recipe, "getOutput", new Class<?>[]{}) != prototype)
          throw new AssertionError("Native result cache did not reset to its constructor prototype");

        ItemStack reordered = offered.copy();
        NBTTagList reorderedGenes = new NBTTagList();
        reorderedGenes.appendTag(original.getCompoundTagAt(1).copy());
        reorderedGenes.appendTag(original.getCompoundTagAt(0).copy());
        for (int i = 2; i < original.tagCount(); i++) reorderedGenes.appendTag(original.getCompoundTagAt(i).copy());
        reordered.getTagCompound().getCompoundTag("Genome").setTag("Chromosomes", reorderedGenes);
        if (!(Boolean) invoke(recipeType, recipe, "matches", new Class<?>[]{ItemStack.class}, reordered))
          throw new AssertionError("Native matching incorrectly depended on chromosome list order");
        ItemStack reorderedResult = (ItemStack) invoke(recipeType, recipe, "getOutput", new Class<?>[]{});
        NBTTagList rewritten = reorderedResult.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes", 10);
        if (rewritten.getCompoundTagAt(0).getByte("Slot") == 0
            || !rewritten.getCompoundTagAt(0).getString("UID0").equals(output.getUID())
            || !rewritten.getCompoundTagAt(1).getString("UID0").equals(input.getUID()))
          throw new AssertionError("Native conversion edits physical list entry zero, not the species Slot");
        portableLaw(source,input,output,offered,recipe,recipeType);
        historicalCacheLaw(scope,source,input,output,offered,recipe,recipeType);
        transitionLaw(source,input,output,offered,recipe,recipeType);
        selectionLaw(source,input,output,offered,recipe,recipeType);
        cacheSnapshotLaw(source,input,offered);
        NativeMagicBeesPoolCacheTest.run(offered);
        NativeMagicBeesPoolSessionTest.run(input,output,offered,recipe,recipeType);
        NativeMagicBeesPoolAdmissionTest.run(recipe,offered);
        nativeHashLaw();
        sourceBindings(scope,source,input);
        JsonObject evidence = object("scope", "isolated-native-law-not-production-adaptation",
            "nativeClass", recipeType.getName(), "input", RecipeRow.stackEvidence(offered),
            "output", RecipeRow.stackEvidence(result), "reorderedInput", RecipeRow.stackEvidence(reordered),
            "reorderedOutput", RecipeRow.stackEvidence(reorderedResult));
        java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),
            "magicbees-pool-native-law.json"), CanonicalJson.bytes(evidence));
      }
    }
    System.out.println("Owned bee reads: native scalar/mate/default/duplicate/repair semantics, unchanged borrowed NBT and global genome cache passed");
    System.out.println("MagicBees native law: metadata/tag copy, count one, cache reset and physical entry-zero rewrite passed");
  }

  /** Portable cache lookup needs the game's hash, not JSON/content identity. */
  private static void nativeHashLaw()throws Exception {
    List<NBTBase> tags=new ArrayList<>();
    Collections.addAll(tags,new NBTTagByte((byte)-128),new NBTTagShort((short)-12345),
        new NBTTagInt(Integer.MIN_VALUE),new NBTTagLong(Long.MIN_VALUE+12345),
        new NBTTagFloat(0f),new NBTTagFloat(-0f),new NBTTagFloat(Float.intBitsToFloat(0x7fa12345)),
        new NBTTagFloat(Float.POSITIVE_INFINITY),new NBTTagDouble(0d),new NBTTagDouble(-0d),
        new NBTTagDouble(Double.longBitsToDouble(0xfff0123456789abcL)),new NBTTagDouble(Double.NEGATIVE_INFINITY),
        new NBTTagString("\u4e2d\u6587\ud83d\ude80"),new NBTTagByteArray(new byte[]{-128,-1,0,127}),
        new NBTTagIntArray(new int[]{Integer.MIN_VALUE,-1,0,Integer.MAX_VALUE}),new NBTTagList());
    NBTTagList empty=new NBTTagList();
    java.lang.reflect.Field listType=NBTTagList.class.getDeclaredField("tagType");listType.setAccessible(true);
    listType.setByte(empty,(byte)10);tags.add(empty);
    NBTTagList list=new NBTTagList();list.appendTag(new NBTTagString("alpha"));list.appendTag(new NBTTagString("\ud83d\ude80"));tags.add(list);
    NBTTagCompound a=new NBTTagCompound();a.setFloat("value",0f);a.setTag("nested",list.copy());
    a.setLong("\ud83d\ude80",Long.MIN_VALUE);tags.add(a);
    NBTTagCompound b=(NBTTagCompound)a.copy();b.setFloat("value",-0f);tags.add(b);
    com.google.gson.JsonArray cases=array();int index=0;
    for(NBTBase tag:tags)cases.add(object("case",index++,"tag",TypedNbt.encode(tag),"nativeHash",tag.hashCode()));
    // A generated broad probe exercises integer overflow and all nested tags;
    // expectations still come from the installed native NBT implementation.
    Random random=new Random(0x4e425448415348L);
    for(int i=0;i<64;i++){
      NBTTagCompound compound=new NBTTagCompound();
      compound.setInteger("int",random.nextInt());compound.setLong("long",random.nextLong());
      compound.setFloat("float",Float.intBitsToFloat(random.nextInt()));
      compound.setDouble("double",Double.longBitsToDouble(random.nextLong()));
      compound.setString("text","\u7269\u54c1\ud83d\ude80"+random.nextInt());
      NBTTagList cells=new NBTTagList();cells.appendTag(a.copy());cells.appendTag(b.copy());compound.setTag("list",cells);
      cases.add(object("case",index++,"tag",TypedNbt.encode(compound),"nativeHash",compound.hashCode()));
    }
    java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"native-nbt-hash-law.json"),CanonicalJson.bytes(cases));
    System.out.println("Native NBT hash oracle: "+cases.size()+" typed/nested values, signed zero, canonical NaN, UTF-16 and overflow recorded");
  }

  /** Snapshot lookup must preserve stored hashes/expiry and leave access metadata untouched. */
  @SuppressWarnings({"unchecked","rawtypes"}) private static void cacheSnapshotLaw(
      GendustryNativeSource source,IAlleleBeeSpecies input,ItemStack offered)throws Exception {
    Class<?> snapshotType;
    try{snapshotType=Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolCache");}
    catch(ClassNotFoundException absent){throw new AssertionError("Missing read-only native cache lookup snapshot",absent);}
    java.lang.reflect.Constructor<?> constructor=snapshotType.getDeclaredConstructor(Object.class,MagicBeesPoolSource.class);
    java.lang.reflect.Method lookup=snapshotType.getDeclaredMethod("lookup",NBTTagCompound.class,long.class),
        verify=snapshotType.getDeclaredMethod("finishVerification"),close=snapshotType.getDeclaredMethod("close");
    java.lang.reflect.Method portable;
    try{portable=snapshotType.getDeclaredMethod("snapshot");portable.setAccessible(true);}
    catch(NoSuchMethodException absent){throw new AssertionError("Native cache lacks an owned portable lookup snapshot",absent);}
    constructor.setAccessible(true);lookup.setAccessible(true);verify.setAccessible(true);close.setAccessible(true);
    Class<?> loaderType=type("forestry.apiculture.genetics.BeeGenome$1");
    java.lang.reflect.Constructor<?> loaderConstructor=loaderType.getDeclaredConstructor();loaderConstructor.setAccessible(true);
    com.google.common.cache.CacheLoader loader=(com.google.common.cache.CacheLoader)loaderConstructor.newInstance();
    class Clock extends com.google.common.base.Ticker {long now=1000;@Override public long read(){return now;}}
    Clock clock=new Clock();
    com.google.common.cache.LoadingCache cache=com.google.common.cache.CacheBuilder.newBuilder()
        .maximumSize(128).expireAfterAccess(1,java.util.concurrent.TimeUnit.MINUTES).ticker(clock).build(loader);
    NBTTagCompound key=(NBTTagCompound)offered.getTagCompound().getCompoundTag("Genome").copy();
    Object genome=loader.load(key.copy());cache.put(key,genome);
    Object nativeMap=field(cache,"localCache");
    java.util.concurrent.atomic.AtomicReferenceArray<?> table=null;Object entry=null,entrySegment=null;int entrySlot=-1;
    for(Object segment:(Object[])field(nativeMap,"segments")){
      java.util.concurrent.atomic.AtomicReferenceArray<?> candidate=(java.util.concurrent.atomic.AtomicReferenceArray<?>)field(segment,"table");
      for(int i=0;i<candidate.length();i++)if(candidate.get(i)!=null){table=candidate;entry=candidate.get(i);entrySlot=i;entrySegment=segment;}
    }
    if(entry==null)throw new AssertionError("Missing private native cache fixture entry");
    Object initialAccess=field(entry,"accessTime"),initialValue=field(entry,"valueReference");
    java.util.concurrent.atomic.AtomicInteger readCount=(java.util.concurrent.atomic.AtomicInteger)field(entrySegment,"readCount");
    int initialReads=readCount.get(),initialRecency=((Queue<?>)field(entrySegment,"recencyQueue")).size();
    com.google.gson.JsonArray portableCases=array();
    try(MagicBeesPoolSource bindings=new MagicBeesPoolSource()){
      Object snapshot=constructor.newInstance(cache,bindings);
      try{
        JsonObject value=(JsonObject)portable.invoke(snapshot);
        String digest=CanonicalJson.digest(value);value.remove("segments");
        if(!digest.equals(CanonicalJson.digest((JsonObject)portable.invoke(snapshot))))
          throw new AssertionError("Portable cache snapshot returned borrowed mutable data");
        for(long at:new long[]{1000,1000+60000000000L-1,1000+60000000000L}){
          JsonObject result=(JsonObject)lookup.invoke(snapshot,key.copy(),at);
          if(!result.get("kind").getAsString().equals(at<1000+60000000000L?"hit":"miss"))
            throw new AssertionError("Cache snapshot expiry boundary differs");
          portableCases.add(object("case","expiry-"+at,"cache",portable.invoke(snapshot),"query",TypedNbt.encode(key),
              "now",Long.toString(at),"result",result));
        }
        if(!initialAccess.equals(field(entry,"accessTime"))||table.get(entrySlot)!=entry
            ||field(entry,"key")!=key||field(entry,"valueReference")!=initialValue
            ||readCount.get()!=initialReads||((Queue<?>)field(entrySegment,"recencyQueue")).size()!=initialRecency)
          throw new AssertionError("Cache snapshot lookup changed live access, counters, recency or entry ownership");
        verify.invoke(snapshot);
        // Native loading lookup skips its fast path when the routed segment's
        // count is zero. Table contents alone are not a complete source binding.
        try(NativeRegistryScope replacement=new NativeRegistryScope()){
          replacement.replace(entrySegment.getClass(),entrySegment,"count",0);
          expectSourceDrift(verify,snapshot);
        }
        verify.invoke(snapshot);
        try(NativeRegistryScope replacement=new NativeRegistryScope()){
          replacement.replace(nativeMap.getClass(),nativeMap,"segmentShift",0);
          expectSourceDrift(verify,snapshot);
        }
        verify.invoke(snapshot);
        // Equal-content replacement is source drift even if serialized cells agree.
        try(NativeRegistryScope replacement=new NativeRegistryScope()){
          replacement.replace(type("forestry.core.genetics.Genome"),genome,"chromosomes",specimenChromosomes(source,input));
          expectSourceDrift(verify,snapshot);
        }
        verify.invoke(snapshot);
        // Native lookup agrees at both sides of the boundary. These oracle
        // reads occur only after the no-mutation comparison above.
        clock.now=1000+60000000000L-1;
        if(cache.getIfPresent(key.copy())!=genome)throw new AssertionError("Native cache expired before its access boundary");
        clock.now+=60000000000L;
        if(cache.getIfPresent(key.copy())!=null)throw new AssertionError("Native cache retained an exactly expired entry");
      }finally{close.invoke(snapshot);}
      // A key mutated after insertion can still appear in entrySet(), but its
      // stored hash makes the new equal-content query unreachable to get().
      // Reinstall a distinct native entry after the oracle's expiry cleanup.
      clock.now=1000;cache.put(key,genome);key.setString("changed-after-insert","native-mutable-key");
      snapshot=constructor.newInstance(cache,bindings);
      try{
        JsonObject result=(JsonObject)lookup.invoke(snapshot,key.copy(),clock.now);
        if(!result.get("kind").getAsString().equals("miss")||cache.getIfPresent(key.copy())!=null)
          throw new AssertionError("Cache snapshot rehashed a historical mutable key into a false hit");
        portableCases.add(object("case","mutated-key","cache",portable.invoke(snapshot),"query",TypedNbt.encode(key),
            "now",Long.toString(clock.now),"result",result));
      }finally{close.invoke(snapshot);}
      cache.invalidateAll();
      try(NativeRegistryScope aliases=new NativeRegistryScope()){
        IAlleleBeeSpecies foreign=aliases.allocate(input.getClass());
        aliases.replace(type("forestry.core.genetics.alleles.Allele"),foreign,"uid",input.getUID());
        aliases.replace(type("forestry.core.genetics.alleles.Allele"),foreign,"isDominant",true);
        IChromosome[] cells=specimenChromosomes(source,foreign);
        Object historical=aliases.allocate(type("forestry.apiculture.genetics.BeeGenome"));
        aliases.replace(type("forestry.core.genetics.Genome"),historical,"chromosomes",cells);
        cache.put(key,historical);snapshot=constructor.newInstance(cache,bindings);
        try{
          JsonObject result=(JsonObject)lookup.invoke(snapshot,key.copy(),clock.now);
          JsonObject allele=result.getAsJsonObject("chromosome").getAsJsonObject("primary");
          if(!allele.get("uid").getAsString().equals(input.getUID())||allele.get("sourceIdentity").getAsBoolean())
            throw new AssertionError("Cache source binding merged a foreign equal-UID allele");
          portableCases.add(object("case","foreign-equal-uid","cache",portable.invoke(snapshot),"query",TypedNbt.encode(key),
              "now",Long.toString(clock.now),"result",result));
          verify.invoke(snapshot);bindings.finishVerification();
        }finally{close.invoke(snapshot);}
      }
      cache.invalidateAll();
      List<NBTTagCompound> queries=new ArrayList<>();
      for(String text:new String[]{"Aa","BB","\u4e2d\u6587\ud83d\ude80"}){
        NBTTagCompound compound=new NBTTagCompound();compound.setString("collision",text);
        cache.put(compound,genome);queries.add((NBTTagCompound)compound.copy());
      }
      NBTTagCompound zero=new NBTTagCompound();zero.setFloat("zero",0f);cache.put(zero,genome);queries.add(zero);
      NBTTagCompound negative=(NBTTagCompound)zero.copy();negative.setFloat("zero",-0f);queries.add(negative);
      NBTTagCompound absent=new NBTTagCompound();absent.setString("collision","absent");queries.add(absent);
      snapshot=constructor.newInstance(cache,bindings);
      try{
        for(int i=0;i<queries.size();i++){
          NBTTagCompound query=queries.get(i);JsonObject result=(JsonObject)lookup.invoke(snapshot,query,clock.now);
          portableCases.add(object("case","collision-and-zero-"+i,"cache",portable.invoke(snapshot),"query",TypedNbt.encode(query),
              "now",Long.toString(clock.now),"result",result));
          if(!result.get("kind").getAsString().equals(cache.getIfPresent(query.copy())==null?"miss":"hit"))
            throw new AssertionError("Native cache collision/signed-zero routing differs");
        }
      }finally{close.invoke(snapshot);}
      cache.invalidateAll();clock.now=Long.MAX_VALUE-1000;cache.put(key,genome);
      snapshot=constructor.newInstance(cache,bindings);
      try{
        for(long elapsed:new long[]{59999999999L,60000000000L}){
          long at=clock.now+elapsed;JsonObject result=(JsonObject)lookup.invoke(snapshot,key.copy(),at);
          if(!result.get("kind").getAsString().equals(elapsed<60000000000L?"hit":"miss"))
            throw new AssertionError("Cache snapshot widened native wrapping nano arithmetic");
          portableCases.add(object("case","nano-wrap-"+elapsed,"cache",portable.invoke(snapshot),"query",TypedNbt.encode(key),
              "now",Long.toString(at),"result",result));
        }
      }finally{close.invoke(snapshot);}
    }
    java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-cache-lookup.json"),
        CanonicalJson.bytes(object("scope","single-instant-owned-cache-lookup-not-transition-or-production-admission","cases",portableCases)));
    System.out.println("MagicBees cache snapshot: stored-hash routing/collision, wrapping expiry boundary, unchanged access time and identity/count/routing drift passed");
  }

  /** A cold reparse must not replace a historical cache hit or merge equal UID objects. */
  @SuppressWarnings({"unchecked","rawtypes"}) private static void historicalCacheLaw(NativeRegistryScope scope,
      GendustryNativeSource source,IAlleleBeeSpecies input,IAlleleBeeSpecies output,
      ItemStack offered,Object recipe,Class<?> recipeType)throws Exception {
    java.lang.reflect.Method evaluate;
    try {evaluate=MagicBeesPoolLaw.class.getDeclaredMethod("evaluate",String.class,String.class,
        String.class,ItemStack.class,com.google.gson.JsonArray.class);evaluate.setAccessible(true);}
    catch(NoSuchMethodException absent){throw new AssertionError("Pool evaluator cannot replay explicit historical cache reads",absent);}
    Class<?> genomeType=type("forestry.apiculture.genetics.BeeGenome");
    Class<?> chromosomeType=type("forestry.core.genetics.Chromosome");
    IAlleleBeeSpecies foreign=(IAlleleBeeSpecies)scope.allocate(input.getClass());
    scope.replace(type("forestry.core.genetics.alleles.Allele"),foreign,"uid",input.getUID());
    scope.replace(type("forestry.core.genetics.alleles.Allele"),foreign,"isDominant",true);
    com.google.gson.JsonArray observations=array();
    try(MagicBeesPoolSource bindings=new MagicBeesPoolSource()){
      MagicBeesPoolLaw law=new MagicBeesPoolLaw(bindings.snapshot());
      for(int state=0;state<8;state++){
        IChromosome[] cells=specimenChromosomes(source,state==1?output:state==2?foreign:input);
        if(state==3)cells[0]=null;
        if(state==4)cells[0]=(IChromosome)chromosomeType.getConstructor(IAllele.class,IAllele.class)
            .newInstance(source.defaults[EnumBeeChromosome.SPEED.ordinal()],source.defaults[EnumBeeChromosome.SPEED.ordinal()]);
        if(state==5)cells[0]=(IChromosome)chromosomeType.getConstructor(IAllele.class,IAllele.class).newInstance(null,input);
        if(state==6)cells[0]=(IChromosome)chromosomeType.getConstructor(IAllele.class,IAllele.class).newInstance(input,null);
        Object cached=scope.allocate(genomeType);
        scope.replace(type("forestry.core.genetics.Genome"),cached,"chromosomes",cells);
        JsonObject hit=cacheRead(cells,input,output);
        com.google.gson.JsonArray reads=array(hit);
        ItemStack sample=offered.copy();
        if(state==7){
          // A hit bypasses both registry parsing and repair of the offered tag.
          sample.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10)
              .getCompoundTagAt(1).setString("UID0","fixture.missing.cached.input");
        }
        JsonObject actual=(JsonObject)evaluate.invoke(law,"beeDroneGE",input.getUID(),output.getUID(),sample,reads);
        com.google.common.cache.LoadingCache cache=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(128)
            .build(new com.google.common.cache.CacheLoader<NBTTagCompound,Object>(){
              @Override public Object load(NBTTagCompound tag){throw new AssertionError("Expected native historical cache hit");}
            });
        ItemStack nativeInput=sample.copy();cache.put(nativeInput.getTagCompound().getCompoundTag("Genome"),cached);
        Map<Object,Object> before=cacheSnapshot(cache);boolean accepted=false;ItemStack result=null;Throwable error=null;
        try(NativeRegistryScope isolated=new NativeRegistryScope()){
          isolated.replace(genomeType,null,"beeGenomeCache",cache);
          try {accepted=(Boolean)invoke(recipeType,recipe,"matches",new Class<?>[]{ItemStack.class},nativeInput);
            if(accepted)result=(ItemStack)invoke(recipeType,recipe,"getOutput",new Class<?>[0]);}
          catch(RuntimeException failure){error=failure;}
        }
        String expected=state==0||state==7?"matched":state>=3?"nativeError":"notMatched";
        String nativeState=error!=null?"nativeError":accepted?"matched":"notMatched";
        if(!actual.get("state").getAsString().equals(expected)||!nativeState.equals(expected)
            ||accepted&&!CanonicalJson.digest(actual.get("output")).equals(CanonicalJson.digest(RecipeRow.stackEvidence(result)))
            ||!before.equals(cacheSnapshot(cache)))
          throw new AssertionError("Explicit historical cache state differs: "+state+" / "+actual+" / "+nativeState,error);
        observations.add(object("case",state,"input",RecipeRow.stackEvidence(sample),"reads",reads,"result",actual));
      }
      // Native Bee reads Mate before consulting the current genome's species.
      // A cached malformed Mate is assigned without inspecting its chromosomes.
      ItemStack mate=offered.copy();mate.getTagCompound().setTag("Mate",new NBTTagCompound());
      JsonObject actual=(JsonObject)evaluate.invoke(law,"beeDroneGE",input.getUID(),output.getUID(),mate,
          array(cacheRead(specimenChromosomes(source,input),input,output),object("kind","hit","chromosome",null)));
      if(!actual.get("state").getAsString().equals("matched"))
        throw new AssertionError("Cached Mate was incorrectly reparsed or dereferenced");
      Object primary=scope.allocate(genomeType),emptyMate=scope.allocate(genomeType);
      scope.replace(type("forestry.core.genetics.Genome"),primary,"chromosomes",specimenChromosomes(source,input));
      scope.replace(type("forestry.core.genetics.Genome"),emptyMate,"chromosomes",null);
      com.google.common.cache.LoadingCache mateCache=com.google.common.cache.CacheBuilder.newBuilder()
          .build(new com.google.common.cache.CacheLoader<NBTTagCompound,Object>(){
            @Override public Object load(NBTTagCompound tag){throw new AssertionError("Unexpected Mate cache miss");}
          });
      ItemStack nativeMate=mate.copy();
      mateCache.put(nativeMate.getTagCompound().getCompoundTag("Genome"),primary);
      mateCache.put(nativeMate.getTagCompound().getCompoundTag("Mate"),emptyMate);
      try(NativeRegistryScope isolated=new NativeRegistryScope()){
        isolated.replace(genomeType,null,"beeGenomeCache",mateCache);
        if(!(Boolean)invoke(recipeType,recipe,"matches",new Class<?>[]{ItemStack.class},nativeMate))
          throw new AssertionError("Native cached malformed Mate blocked matching");
        ItemStack result=(ItemStack)invoke(recipeType,recipe,"getOutput",new Class<?>[0]);
        if(!CanonicalJson.digest(actual.get("output")).equals(CanonicalJson.digest(RecipeRow.stackEvidence(result))))
          throw new AssertionError("Cached malformed Mate output differs");
      }
      observations.add(object("case","cached-malformed-mate","input",RecipeRow.stackEvidence(mate),
          "reads",array(cacheRead(specimenChromosomes(source,input),input,output),object("kind","hit","chromosome",null)),"result",actual));
      // An uncached invalid Mate still throws before a different species can
      // reject this recipe. A premature species short-circuit would be wrong.
      ItemStack badMate=offered.copy();NBTTagCompound invalidGenome=new NBTTagCompound(),invalidGene=new NBTTagCompound();
      invalidGene.setByte("Slot",(byte)EnumBeeChromosome.HUMIDITY.ordinal());
      invalidGene.setString("UID0","fixture.missing.mate");invalidGene.setString("UID1","fixture.missing.mate");
      NBTTagList invalidGenes=new NBTTagList();invalidGenes.appendTag(invalidGene);
      invalidGenome.setTag("Chromosomes",invalidGenes);badMate.getTagCompound().setTag("Mate",invalidGenome);
      com.google.gson.JsonArray invalidMateReads=array(cacheRead(specimenChromosomes(source,output),input,output),object("kind","miss"));
      JsonObject invalidMate=(JsonObject)evaluate.invoke(law,"beeDroneGE",input.getUID(),output.getUID(),badMate,invalidMateReads);
      if(!invalidMate.get("state").getAsString().equals("nativeError"))
        throw new AssertionError("Historical species mismatch skipped native Mate failure");
      observations.add(object("case","mismatch-with-invalid-cold-mate","input",RecipeRow.stackEvidence(badMate),"reads",invalidMateReads,"result",invalidMate));
      bindings.finishVerification();
      java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-cache-law.json"),
          CanonicalJson.bytes(object("scope","explicit-read-trace-not-live-cache-snapshot-or-production-admission",
              "form","beeDroneGE","inputSpecies",input.getUID(),"outputSpecies",output.getUID(),
              "source",bindings.snapshot(),"observations",observations)));
    }
    System.out.println("MagicBees explicit cache traces: historical species/object identity, null/wrong alleles, repair bypass and unused cached Mate passed");
  }

  private static IChromosome[] specimenChromosomes(GendustryNativeSource source,IAlleleBeeSpecies species){
    IAllele[] genes=source.defaults.clone();genes[0]=species;
    return source.root.templateAsGenome(genes).getChromosomes();
  }

  /** A rejected callback can repair the offered stack before later pool entries run. */
  @SuppressWarnings({"unchecked","rawtypes"}) private static void transitionLaw(GendustryNativeSource source,
      IAlleleBeeSpecies input,IAlleleBeeSpecies output,ItemStack offered,Object recipe,Class<?> recipeType)throws Exception {
    java.lang.reflect.Method transition;
    try{transition=MagicBeesPoolLaw.class.getDeclaredMethod("transition",String.class,String.class,String.class,ItemStack.class,com.google.gson.JsonArray.class);}
    catch(NoSuchMethodException missing){throw new AssertionError("Pool callback loses offered-stack repair after mismatch or failure",missing);}
    transition.setAccessible(true);
    java.lang.reflect.Constructor<?> loaderConstructor=type("forestry.apiculture.genetics.BeeGenome$1").getDeclaredConstructor();
    loaderConstructor.setAccessible(true);
    com.google.common.cache.CacheLoader loader=(com.google.common.cache.CacheLoader)loaderConstructor.newInstance();
    com.google.gson.JsonArray observations=array();
    try(MagicBeesPoolSource bindings=new MagicBeesPoolSource()){
      MagicBeesPoolLaw law=new MagicBeesPoolLaw(bindings.snapshot());
      for(String name:new String[]{"matched-copy","repaired-mismatch","partial-repair-before-mate-error","hit-skips-repair"}){
        ItemStack sample=offered.copy();
        if(!name.equals("matched-copy")){
          NBTTagList genes=sample.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10);
          genes.getCompoundTagAt(1).setString("UID0","fixture.missing.repaired");
          if(name.equals("repaired-mismatch")){
            genes.getCompoundTagAt(0).setString("UID0",output.getUID());
            genes.getCompoundTagAt(0).setString("UID1",output.getUID());
          }
        }
        if(name.equals("partial-repair-before-mate-error")){
          NBTTagCompound mate=new NBTTagCompound(),gene=new NBTTagCompound();
          gene.setByte("Slot",(byte)EnumBeeChromosome.HUMIDITY.ordinal());gene.setString("UID0","fixture.missing.mate");gene.setString("UID1","fixture.missing.mate");
          NBTTagList genes=new NBTTagList();genes.appendTag(gene);mate.setTag("Chromosomes",genes);
          sample.getTagCompound().setTag("Mate",mate);
        }
        ItemStack nativeInput=sample.copy();com.google.gson.JsonArray reads=array();
        com.google.common.cache.LoadingCache cache=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(128).build(loader);
        boolean warm=name.equals("hit-skips-repair");
        for(String key:new String[]{"Genome","Mate"})if(nativeInput.getTagCompound().hasKey(key)){
          NBTTagCompound tag=nativeInput.getTagCompound().getCompoundTag(key);
          if(warm){IGenome loaded=(IGenome)loader.load(tag.copy());cache.put(tag,loaded);reads.add(cacheRead(loaded.getChromosomes(),input,output));}
          else reads.add(object("kind","miss"));
        }
        String before=CanonicalJson.digest(RecipeRow.stackEvidence(sample));
        JsonObject actual=(JsonObject)transition.invoke(law,"beeDroneGE",input.getUID(),output.getUID(),sample,reads);
        boolean accepted=false;ItemStack result=null;Throwable error=null;
        try(NativeRegistryScope isolated=new NativeRegistryScope()){
          isolated.replace(type("forestry.apiculture.genetics.BeeGenome"),null,"beeGenomeCache",cache);
          try{accepted=(Boolean)invoke(recipeType,recipe,"matches",new Class<?>[]{ItemStack.class},nativeInput);
            if(accepted)result=(ItemStack)invoke(recipeType,recipe,"getOutput",new Class<?>[0]);}
          catch(RuntimeException failure){error=failure;}
        }
        JsonObject expectedResult=error!=null?object("state","nativeError"):accepted?
            object("state","matched","output",RecipeRow.stackEvidence(result)):object("state","notMatched");
        JsonObject expected=object("result",expectedResult,"offeredAfter",RecipeRow.stackEvidence(nativeInput));
        if(!CanonicalJson.digest(actual).equals(CanonicalJson.digest(expected))
            ||!before.equals(CanonicalJson.digest(RecipeRow.stackEvidence(sample))))
          throw new AssertionError("Offered-stack transition differs from native callback: "+name+" / "+actual+" / "+expected,error);
        if(name.equals("repaired-mismatch")&&(!expectedResult.get("state").getAsString().equals("notMatched")
            ||before.equals(CanonicalJson.digest(expected.get("offeredAfter")))))
          throw new AssertionError("Mismatch fixture did not exercise native repair");
        if(name.equals("partial-repair-before-mate-error")&&(!expectedResult.get("state").getAsString().equals("nativeError")
            ||before.equals(CanonicalJson.digest(expected.get("offeredAfter")))))
          throw new AssertionError("Error fixture did not preserve the earlier native repair");
        observations.add(object("case",name,"input",RecipeRow.stackEvidence(sample),"reads",reads,"transition",actual));
      }
      bindings.finishVerification();
      java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-transition-law.json"),
          CanonicalJson.bytes(object("scope","explicit-read-stack-transition-not-cache-transition-or-production-admission",
              "form","beeDroneGE","inputSpecies",input.getUID(),"outputSpecies",output.getUID(),
              "source",bindings.snapshot(),"observations",observations)));
    }
    System.out.println("MagicBees offered-stack transitions: mismatch repair, error side effects, output copy and hit repair bypass passed");
  }

  /** Native ordered callbacks are evaluated before catalyst and mana checks. */
  @SuppressWarnings({"unchecked","rawtypes"}) private static void selectionLaw(GendustryNativeSource source,
      IAlleleBeeSpecies input,IAlleleBeeSpecies output,ItemStack offered,Object recipe,Class<?> recipeType)throws Exception {
    Class<?> selection;
    try{selection=Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolSelection");}
    catch(ClassNotFoundException missing){throw new AssertionError("Missing ordered pool selector with callback repair effects",missing);}
    java.lang.reflect.Method select=selection.getDeclaredMethod("select",JsonObject.class,com.google.gson.JsonArray.class,
        ItemStack.class,boolean.class,boolean.class,int.class,com.google.gson.JsonArray.class);
    select.setAccessible(true);
    Class<?> base=type("vazkii.botania.api.recipe.RecipeManaInfusion");
    java.lang.reflect.Constructor<?> loaderConstructor=type("forestry.apiculture.genetics.BeeGenome$1").getDeclaredConstructor();
    loaderConstructor.setAccessible(true);
    com.google.common.cache.CacheLoader loader=(com.google.common.cache.CacheLoader)loaderConstructor.newInstance();
    com.google.gson.JsonArray observations=array();
    try(MagicBeesPoolSource bindings=new MagicBeesPoolSource();NativeRegistryScope options=new NativeRegistryScope()){
      options.replace(type("vazkii.botania.api.BotaniaAPI"),null,"internalHandler",
          type("vazkii.botania.common.core.handler.InternalMethodHandler").newInstance());
      for(String name:new String[]{"ordinary-preempts","ineligible-bee-repairs","low-mana-preempts","mini-preempts","no-eligible-entry"}){
        ItemStack sample=offered.copy();
        sample.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).getCompoundTagAt(1)
            .setString("UID0","fixture.missing.priority.repair");
        sample.getTagCompound().setString("type","daybloom");
        ItemStack fixed=new ItemStack(net.minecraft.init.Items.diamond,3);
        Object ordinary=base.getConstructor(ItemStack.class,Object.class,int.class).newInstance(fixed,sample.copy(),2);
        Object mini=options.allocate(type("vazkii.botania.api.recipe.RecipeMiniFlower"));
        options.replace(base,mini,"input","daybloom");options.replace(base,mini,"output",fixed);
        options.replace(base,mini,"mana",1);options.replace(base,mini,"isAlchemy",true);
        options.replace(base,recipe,"mana",10);
        options.replace(base,recipe,"isAlchemy",name.equals("ineligible-bee-repairs")||name.equals("no-eligible-entry"));
        options.replace(base,ordinary,"isAlchemy",name.equals("no-eligible-entry"));
        List<Object> entries=name.equals("ordinary-preempts")?Arrays.asList(ordinary,recipe):
            name.equals("mini-preempts")?Arrays.asList(mini,recipe):Arrays.asList(recipe,ordinary);
        boolean alchemy=name.equals("mini-preempts");int mana=name.equals("low-mana-preempts")?5:100;
        com.google.gson.JsonArray program=array(),traces=array();
        for(Object entry:entries){
          JsonObject row=object("alchemy",field(entry,"isAlchemy"),"conjuration",false,"mana",field(entry,"mana"));
          if(entry==recipe){row.addProperty("kind","bee");row.addProperty("form","beeDroneGE");
            row.addProperty("inputSpecies",input.getUID());row.addProperty("outputSpecies",output.getUID());}
          else if(entry==mini){row.addProperty("kind","mini");row.addProperty("key","daybloom");row.add("output",RecipeRow.stackEvidence(fixed));}
          else{row.addProperty("kind","item");row.add("predicates",array(object("registry",net.minecraft.item.Item.itemRegistry.getNameForObject(sample.getItem()),"metadata",sample.getItemDamage())));
            row.add("output",RecipeRow.stackEvidence(fixed));}
          program.add(row);
        }
        if(!name.equals("ordinary-preempts")&&!name.equals("mini-preempts"))
          traces.add(object("index",0,"reads",array(object("kind","miss"))));
        String before=CanonicalJson.digest(RecipeRow.stackEvidence(sample));
        JsonObject actual=(JsonObject)select.invoke(null,bindings.snapshot(),program,sample,alchemy,false,mana,traces);
        ItemStack nativeInput=sample.copy();JsonObject result=object("state","notMatched");int visited=0;
        com.google.common.cache.LoadingCache cache=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(128).build(loader);
        try(NativeRegistryScope isolated=new NativeRegistryScope()){
          isolated.replace(type("forestry.apiculture.genetics.BeeGenome"),null,"beeGenomeCache",cache);
          for(int index=0;index<entries.size();index++){
            Object entry=entries.get(index);visited++;
            try{
              if(!(Boolean)invoke(entry.getClass(),entry,"matches",new Class<?>[]{ItemStack.class},nativeInput))continue;
              if((Boolean)field(entry,"isAlchemy")&&!alchemy||(Boolean)field(entry,"isConjuration"))continue;
              int cost=(Integer)field(entry,"mana");
              result=mana<cost?object("state","insufficientMana","index",index):
                  object("state","selected","index",index,"output",RecipeRow.stackEvidence((ItemStack)invoke(entry.getClass(),entry,"getOutput",new Class<?>[0])));
            }catch(RuntimeException failure){result=object("state","nativeError","index",index);}
            break;
          }
        }
        JsonObject expected=object("result",result,"offeredAfter",RecipeRow.stackEvidence(nativeInput),"visited",visited);
        if(!CanonicalJson.digest(actual).equals(CanonicalJson.digest(expected))
            ||!before.equals(CanonicalJson.digest(RecipeRow.stackEvidence(sample))))
          throw new AssertionError("Ordered pool selection differs from native callbacks: "+name+" / "+actual+" / "+expected);
        observations.add(object("case",name,"entries",program,"input",RecipeRow.stackEvidence(sample),
            "alchemy",alchemy,"conjuration",false,"mana",mana,"traces",traces,"selection",actual));
      }
      bindings.finishVerification();
      java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-selection-law.json"),
          CanonicalJson.bytes(object("scope","explicit-read-ordered-selection-not-cache-transition-or-production-admission",
              "source",bindings.snapshot(),"observations",observations)));
    }
    System.out.println("MagicBees ordered selection: matches before catalyst, ordinary/MiniFlower priority and low-mana preemption passed");
  }

  private static JsonObject cacheRead(IChromosome[] cells,IAlleleBeeSpecies input,IAlleleBeeSpecies output){
    IChromosome first=cells[0];
    return object("kind","hit","chromosome",first==null?null:object(
        "primary",cachedAllele(first.getPrimaryAllele(),input,output),
        "secondary",cachedAllele(first.getSecondaryAllele(),input,output)));
  }
  private static JsonObject cachedAllele(IAllele allele,IAlleleBeeSpecies input,IAlleleBeeSpecies output){
    return allele==null?null:object("uid",allele.getUID(),"dominant",allele.isDominant(),
        "beeSpecies",allele instanceof IAlleleBeeSpecies,"sourceIdentity",allele==input||allele==output);
  }

  /** A missing snapshot evaluator must fail before any production admission. */
  @SuppressWarnings("unchecked") private static void portableLaw(GendustryNativeSource source,
      IAlleleBeeSpecies input,IAlleleBeeSpecies output,ItemStack offered,Object recipe,Class<?> recipeType)throws Exception {
    Class<?> law;
    try{law=Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolLaw");}
    catch(ClassNotFoundException absent){throw new AssertionError("Missing source-snapshot pool evaluator",absent);}
    java.lang.reflect.Constructor<?> constructor=law.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
    java.lang.reflect.Method evaluate=law.getDeclaredMethod("evaluate",String.class,String.class,String.class,ItemStack.class,com.google.gson.JsonArray.class);
    evaluate.setAccessible(true);
    try(MagicBeesPoolSource bindings=new MagicBeesPoolSource()){
      Object evaluator=constructor.newInstance(bindings.snapshot());
      List<ItemStack> cases=new ArrayList<>();cases.add(offered.copy());
      ItemStack alias=offered.copy();
      Map<String,IAllele> aliases=(Map<String,IAllele>)field(AlleleManager.alleleRegistry,"deprecatedAlleleMap");
      aliases.put("fixture.old.pool",input);
      try(MagicBeesPoolSource withAlias=new MagicBeesPoolSource()){
        Object aliased=constructor.newInstance(withAlias.snapshot());
        NBTTagCompound gene=alias.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).getCompoundTagAt(0);
        gene.setString("UID0","fixture.old.pool");gene.setString("UID1","fixture.old.pool");
        compareLaw(evaluate,aliased,source,input,output,alias,recipe,recipeType,false);
      }finally{aliases.remove("fixture.old.pool");}
      ItemStack duplicate=offered.copy();NBTTagList genes=duplicate.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10);
      NBTTagCompound last=(NBTTagCompound)genes.getCompoundTagAt(0).copy();last.setString("UID0",output.getUID());last.setString("UID1",output.getUID());genes.appendTag(last);cases.add(duplicate);
      ItemStack repaired=offered.copy();repaired.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).getCompoundTagAt(1).setString("UID0","fixture.missing");cases.add(repaired);
      ItemStack mate=offered.copy();mate.getTagCompound().setTag("Mate",repaired.getTagCompound().getCompoundTag("Genome").copy());cases.add(mate);
      ItemStack invalidMate=offered.copy();invalidMate.getTagCompound().setTag("Mate",new NBTTagCompound());cases.add(invalidMate);
      ItemStack defaultBee=offered.copy();defaultBee.setTagCompound(null);cases.add(defaultBee);
      ItemStack missing=offered.copy();missing.getTagCompound().getCompoundTag("Genome").removeTag("Chromosomes");cases.add(missing);
      ItemStack badSlot=offered.copy();badSlot.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).getCompoundTagAt(0).setInteger("Slot",256);cases.add(badSlot);
      ItemStack reordered=offered.copy();NBTTagList original=reordered.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10),order=new NBTTagList();
      order.appendTag(original.getCompoundTagAt(1).copy());order.appendTag(original.getCompoundTagAt(0).copy());for(int i=2;i<original.tagCount();i++)order.appendTag(original.getCompoundTagAt(i).copy());
      reordered.getTagCompound().getCompoundTag("Genome").setTag("Chromosomes",order);cases.add(reordered);
      ItemStack ignored=offered.copy();NBTTagCompound ignoredEntry=new NBTTagCompound();ignoredEntry.setByte("Slot",(byte)-1);
      ignored.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).appendTag(ignoredEntry);cases.add(ignored);
      ItemStack wrongForm=offered.copy();wrongForm.func_150996_a(net.minecraft.init.Items.paper);cases.add(wrongForm);
      com.google.gson.JsonArray observations=new com.google.gson.JsonArray();
      for(ItemStack sample:cases)observations.add(compareLaw(evaluate,evaluator,source,input,output,sample,recipe,recipeType,false));
      // Cache-hit parsing skips native repair writes to the offered NBT. Use a
      // private cache for this probe so no live/global cache owner is touched.
      observations.add(compareLaw(evaluate,evaluator,source,input,output,repaired,recipe,recipeType,true));
      bindings.finishVerification();
      java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),"magicbees-pool-snapshot-law.json"),
          CanonicalJson.bytes(object("scope","isolated-snapshot-law-not-production-adaptation",
              "form","beeDroneGE","inputSpecies",input.getUID(),"outputSpecies",output.getUID(),
              "cacheScope","explicit-isolated-native-read-trace-without-live-lookup-or-expiry-binding",
              "source",bindings.snapshot(),"observations",observations)));
    }
    try(NativeRegistryScope options=new NativeRegistryScope()){
      options.replace(type("forestry.core.genetics.alleles.Allele"),input,"isDominant",false);
      try(MagicBeesPoolSource recessive=new MagicBeesPoolSource()){
        ItemStack heterozygous=offered.copy();heterozygous.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).getCompoundTagAt(0).setString("UID1",output.getUID());
        compareLaw(evaluate,constructor.newInstance(recessive.snapshot()),source,input,output,heterozygous,recipe,recipeType,false);
        recessive.finishVerification();
      }
      options.replace(type("forestry.core.config.Config"),null,"clearInvalidChromosomes",false);
      try(MagicBeesPoolSource strict=new MagicBeesPoolSource()){
        ItemStack invalid=offered.copy();invalid.getTagCompound().getCompoundTag("Genome").getTagList("Chromosomes",10).getCompoundTagAt(1).setString("UID1","fixture.missing");
        compareLaw(evaluate,constructor.newInstance(strict.snapshot()),source,input,output,invalid,recipe,recipeType,false);
        strict.finishVerification();
      }
    }
  }

  @SuppressWarnings({"unchecked","rawtypes"}) private static JsonObject compareLaw(java.lang.reflect.Method evaluate,Object evaluator,
      GendustryNativeSource source,IAlleleBeeSpecies input,IAlleleBeeSpecies output,ItemStack sample,Object recipe,Class<?> recipeType,boolean warm)throws Exception {
    String before=CanonicalJson.digest(RecipeRow.stackEvidence(sample));
    java.lang.reflect.Constructor<?> loaderConstructor=type("forestry.apiculture.genetics.BeeGenome$1").getDeclaredConstructor();
    loaderConstructor.setAccessible(true);
    com.google.common.cache.CacheLoader loader=(com.google.common.cache.CacheLoader)loaderConstructor.newInstance();
    com.google.common.cache.LoadingCache cache=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(128).build(loader);
    ItemStack nativeInput=sample.copy();boolean accepted=false;ItemStack result=null;Throwable error=null;
    com.google.gson.JsonArray reads=array();
    if(nativeInput.getTagCompound()!=null){
      for(String key:new String[]{"Genome","Mate"})if(nativeInput.getTagCompound().hasKey(key)){
        NBTTagCompound tag=nativeInput.getTagCompound().getCompoundTag(key);
        if(warm){
          IGenome loaded=(IGenome)loader.load(tag.copy());cache.put(tag,loaded);
          reads.add(cacheRead(loaded.getChromosomes(),input,output));
        }else reads.add(object("kind","miss"));
      }
    }
    JsonObject actual=(JsonObject)evaluate.invoke(evaluator,"beeDroneGE",input.getUID(),output.getUID(),sample,reads);
    try(NativeRegistryScope isolated=new NativeRegistryScope()){
      isolated.replace(type("forestry.apiculture.genetics.BeeGenome"),null,"beeGenomeCache",cache);
      try{accepted=(Boolean)invoke(recipeType,recipe,"matches",new Class<?>[]{ItemStack.class},nativeInput);if(accepted)result=(ItemStack)invoke(recipeType,recipe,"getOutput",new Class<?>[0]);}
      catch(RuntimeException failure){error=failure;}
    }
    String state=error!=null?"nativeError":accepted?"matched":"notMatched";
    if(!actual.get("state").getAsString().equals(state)
        ||accepted&&!CanonicalJson.digest(actual.get("output")).equals(CanonicalJson.digest(RecipeRow.stackEvidence(result)))
        ||!before.equals(CanonicalJson.digest(RecipeRow.stackEvidence(sample))))
      throw new AssertionError("Snapshot pool law differs from native callback: "+state+" / "+actual,error);
    return object("input",RecipeRow.stackEvidence(sample),"reads",reads,"result",actual);
  }

  /** Registry aliases, dominance and repair settings are part of matching, not labels. */
  @SuppressWarnings("unchecked") private static void sourceBindings(NativeRegistryScope scope,
      GendustryNativeSource source,IAlleleBeeSpecies input)throws Exception {
    Class<?> sourceType;
    try {sourceType=Class.forName("com.github.dcysteine.nesql.exporter.capture.MagicBeesPoolSource");}
    catch(ClassNotFoundException absent){throw new AssertionError("MagicBees pool lacks a bound native matching source",absent);}
    java.lang.reflect.Constructor<?> constructor=sourceType.getDeclaredConstructor();constructor.setAccessible(true);
    java.lang.reflect.Method snapshot=sourceType.getDeclaredMethod("snapshot"),verify=sourceType.getDeclaredMethod("finishVerification"),close=sourceType.getDeclaredMethod("close");
    snapshot.setAccessible(true);verify.setAccessible(true);close.setAccessible(true);
    Map<String,IAllele> aliases=(Map<String,IAllele>)field(AlleleManager.alleleRegistry,"deprecatedAlleleMap");
    aliases.put("fixture.old.species",input);
    Object bindings=constructor.newInstance();
    try {
      JsonObject context=(JsonObject)snapshot.invoke(bindings);
      if(!context.getAsJsonObject("aliases").get("fixture.old.species").getAsString().equals(input.getUID())
          ||context.getAsJsonArray("defaults").size()!=EnumBeeChromosome.values().length
          ||context.getAsJsonArray("forest").size()!=EnumBeeChromosome.values().length
          ||context.getAsJsonArray("alleles").size()==0)
        throw new AssertionError("Native pool source lost aliases, karyotype defaults or FOREST state");
      String before=CanonicalJson.digest(context);
      context.remove("aliases");
      if(!before.equals(CanonicalJson.digest((JsonObject)snapshot.invoke(bindings))))
        throw new AssertionError("Matching source returned a borrowed mutable snapshot");
      verify.invoke(bindings);
      aliases.put("fixture.old.species",source.defaults[EnumBeeChromosome.LIFESPAN.ordinal()]);
      expectSourceDrift(verify,bindings);
      aliases.put("fixture.old.species",input);verify.invoke(bindings);
      scope.replace(type("forestry.core.config.Config"),null,"clearInvalidChromosomes",false);
      expectSourceDrift(verify,bindings);
    }finally{close.invoke(bindings);aliases.remove("fixture.old.species");}
    bindings=constructor.newInstance();
    try {
      // SpeciesRoot.getTemplate returns a copy too; drift must modify storage.
      IAllele[] template=source.root.getGenomeTemplates().get(input.getUID());IAllele previous=template[1];
      try {template[1]=source.defaults[EnumBeeChromosome.LIFESPAN.ordinal()];expectSourceDrift(verify,bindings);}
      finally {template[1]=previous;}
      verify.invoke(bindings);
    }finally{close.invoke(bindings);}
    bindings=constructor.newInstance();
    try {
      Object forest=field(type("forestry.apiculture.genetics.BeeDefinition"),null,"FOREST");
      // Replacing native storage with equal contents is still a source change.
      try(NativeRegistryScope replacement=new NativeRegistryScope()){
        replacement.replace(type("forestry.apiculture.genetics.BeeDefinition"),forest,"template",source.defaults.clone());
        expectSourceDrift(verify,bindings);
      }
      verify.invoke(bindings);
    }finally{close.invoke(bindings);}
    // Templates can retain unregistered objects. Their UID/dominance/slot facts
    // must still exist, without falsely adding them to native UID lookup.
    Map<String,IAllele> registered=(Map<String,IAllele>)field(AlleleManager.alleleRegistry,"alleleMap");
    IAllele speed=source.defaults[EnumBeeChromosome.SPEED.ordinal()];
    IAllele removed=registered.remove(speed.getUID());
    try {
      bindings=constructor.newInstance();
      try {
        JsonObject context=(JsonObject)snapshot.invoke(bindings);
        if(!context.has("definitions")||!context.getAsJsonObject("definitions").has(speed.getUID()))
          throw new AssertionError("Unregistered template allele lost its source-bound descriptor");
        for(com.google.gson.JsonElement row:context.getAsJsonArray("alleles"))
          if(row.getAsJsonObject().get("uid").getAsString().equals(speed.getUID()))
            throw new AssertionError("Template descriptor invented a native UID registration");
        verify.invoke(bindings);
      }finally{close.invoke(bindings);}
    }finally{if(removed!=null)registered.put(speed.getUID(),removed);}
  }

  private static void expectSourceDrift(java.lang.reflect.Method verify,Object source)throws Exception {
    try {verify.invoke(source);throw new AssertionError("Changed pool source was accepted");}
    catch(java.lang.reflect.InvocationTargetException failure){
      Throwable cause=failure.getCause();
      if(!(cause instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault)
          ||!((com.github.dcysteine.nesql.exporter.task.Jobs.Fault)cause).code.equals("environment_changed"))throw failure;
    }
  }

  private static void sameNativeRead(GendustryNativeSource source, ItemStack offered,
      com.google.common.cache.LoadingCache<?, ?> cache) {
    String before = CanonicalJson.digest(TypedNbt.encode(offered.getTagCompound()));
    Map<Object, Object> cacheBefore = cacheSnapshot(cache);
    IIndividual actual = ForestryMembers.read(source.root, offered);
    if (!cacheBefore.equals(cacheSnapshot(cache))
        || !before.equals(CanonicalJson.digest(TypedNbt.encode(offered.getTagCompound()))))
      throw new AssertionError("Owned bee state read changed global cache or borrowed NBT");
    IIndividual expected = source.root.getMember(offered.copy());
    NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
    actual.writeToNBT(a); expected.writeToNBT(b);
    if (!CanonicalJson.digest(TypedNbt.encode(a)).equals(CanonicalJson.digest(TypedNbt.encode(b))))
      throw new AssertionError("Owned bee scalar/mate/default/duplicate/repair read differs from installed native behavior");
  }

  private static Map<Object, Object> cacheSnapshot(com.google.common.cache.LoadingCache<?, ?> cache) {
    // Native repair can mutate an NBT key already held by the cache. Observe
    // cache ownership by identity rather than rehashing those mutable keys.
    Map<Object, Object> result = new IdentityHashMap<>();
    for (Map.Entry<?, ?> entry : cache.asMap().entrySet()) result.put(entry.getKey(), entry.getValue());
    return result;
  }
}
