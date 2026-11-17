package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.genetics.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable observation of the pinned strong/access-expiring native cache.
 * Never invokes get/getIfPresent/entrySet/cleanup or changes access metadata.
 * A lookup describes one supplied instant, not later eviction or recipe-order transitions. */
final class MagicBeesPoolCache implements AutoCloseable {
  private final MagicBeesPoolSource source;
  private final int chromosomeCount;
  private Object cache;
  private Snapshot initial;
  private JsonObject binding;
  private List<Object> runtimeConfiguration;
  private static final long EXPIRE=60000000000L;
  private static final String GUAVA17="8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc";

  MagicBeesPoolCache(Object cache,MagicBeesPoolSource source){
    this(cache,source,false);
  }
  private MagicBeesPoolCache(Object cache,MagicBeesPoolSource source,boolean bindRuntime){
    this.cache=cache;this.source=source;
    chromosomeCount=source.snapshot().getAsJsonArray("chromosomes").size();
    if(bindRuntime){
      Object map=field(cache,"localCache"),ticker=field(map,"ticker");
      if(ticker!=com.google.common.base.Ticker.systemTicker())
        throw unsupported("Native bee cache requires the reviewed system nanoTime ticker");
      verifyRuntime();
      runtimeConfiguration=configuration();
      long before=((com.google.common.base.Ticker)ticker).read();
      initial=read();
      long after=((com.google.common.base.Ticker)ticker).read();
      if(after-before<0||!sameConfiguration())throw changed();
      binding=object("runtime","guava17StrongAccess","artifactSha256",GUAVA17,
          "clock","javaSystemNanoTime","beforeNanos",Long.toString(before),"afterNanos",Long.toString(after),
          "scope","serialOwnedTransitionsFromBoundSnapshotWithExplicitTickerReads");
    }else initial=read();
  }

  static MagicBeesPoolCache bound(Object cache,MagicBeesPoolSource source){
    return new MagicBeesPoolCache(cache,source,true);
  }
  JsonObject binding(){
    alive();if(binding==null)throw unsupported("Unbound native cache observation has no live clock provenance");
    return new JsonParser().parse(binding.toString()).getAsJsonObject();
  }
  private static void verifyRuntime(){
    Class<?> anchor=com.google.common.cache.CacheBuilder.class;
    java.security.CodeSource origin=anchor.getProtectionDomain().getCodeSource();
    if(origin==null)throw unsupported("Native Guava cache artifact is unavailable");
    try{
      if(!"file".equals(origin.getLocation().getProtocol())
          ||!Sources.hash(java.nio.file.Paths.get(origin.getLocation().toURI())).equals(GUAVA17))
        throw unsupported("Native cache requires the reviewed installed Guava 17 artifact");
    }catch(java.io.IOException|java.net.URISyntaxException failure){
      Jobs.Fault fault=unsupported("Cannot bind native Guava cache artifact");fault.initCause(failure);throw fault;
    }
    for(String name:new String[]{"com.google.common.cache.LocalCache","com.google.common.cache.LocalCache$Segment",
        "com.google.common.cache.LocalCache$EntryFactory","com.google.common.cache.LocalCache$EntryFactory$2",
        "com.google.common.cache.LocalCache$StrongAccessEntry","com.google.common.cache.LocalCache$StrongValueReference",
        "com.google.common.cache.LocalCache$LoadingValueReference","com.google.common.cache.LocalCache$AccessQueue",
        "com.google.common.cache.LocalCache$LocalLoadingCache","com.google.common.cache.CacheBuilder$OneWeigher",
        "com.google.common.cache.CacheBuilder$NullListener","com.google.common.base.Equivalence",
        "com.google.common.base.Equivalence$Equals","com.google.common.base.Ticker",
        "com.google.common.base.Ticker$1","com.google.common.base.Platform"}){
      Class<?> owner=type(name);
      if(owner.getClassLoader()!=anchor.getClassLoader()||!origin.equals(owner.getProtectionDomain().getCodeSource()))
        throw unsupported("Native cache dependency is not from the reviewed Guava 17 artifact: "+name);
    }
  }

  JsonObject lookup(NBTTagCompound query,long now){
    alive();NativeStackAudit.nbt(query);
    if(query==null)throw unsupported("Null native cache lookup key");
    int hash=rehash(query.hashCode());
    // Native getEntry compares the stored insertion hash before key equality.
    // Rehashing mutable historical keys would invent cache hits.
    int segment=(hash>>>initial.shift)&initial.mask;
    Segment route=initial.segments.get(segment);
    if(route.count==0)return object("kind","miss");
    for(Entry entry:route.buckets.get(hash&(route.length-1))){
      if(entry.hash==hash&&query.equals(entry.key)){
        if(now-entry.access>=EXPIRE)return object("kind","miss");
        return object("kind","hit","chromosome",new JsonParser().parse(entry.chromosome.toString()));
      }
    }
    return object("kind","miss");
  }

  JsonObject snapshot(){
    alive();return new JsonParser().parse(initial.definition.toString()).getAsJsonObject();
  }

  /** Transition observation includes stale recency references and root-key aliases.
   * It still does not bind a live clock or admit a production callback. */
  JsonObject state(){
    alive();return new JsonParser().parse(initial.state.toString()).getAsJsonObject();
  }

  void finishVerification(){
    alive();
    try{
      // Production executes serial transitions against the owned initial state.
      // Live accesses and eviction are not edits to that state or recipe definitions.
      if(binding!=null){if(!sameConfiguration())throw changed();return;}
      Snapshot actual=read();
      if(!initial.signature.equals(actual.signature)||initial.pins.size()!=actual.pins.size())throw changed();
      for(int i=0;i<initial.pins.size();i++)if(initial.pins.get(i)!=actual.pins.get(i))throw changed();
    }catch(Jobs.Fault cause){
      if(cause.code.equals("environment_changed"))throw cause;
      Jobs.Fault drift=changed();drift.initCause(cause);throw drift;
    }
  }

  private List<Object> configuration(){
    Object map=field(cache,"localCache"),segments=field(map,"segments");
    if(!(segments instanceof Object[]))throw changed();
    List<Object> values=new ArrayList<>();
    Collections.addAll(values,cache,map,segments);
    for(String member:new String[]{"defaultLoader","ticker","keyStrength","valueStrength",
        "keyEquivalence","valueEquivalence","weigher","removalListener"})
      values.add(field(map,member));
    for(String member:new String[]{"expireAfterAccessNanos","expireAfterWriteNanos","refreshNanos",
        "maxWeight","segmentShift","segmentMask","concurrencyLevel"})
      values.add(field(map,member));
    for(Object segment:(Object[])segments){values.add(segment);values.add(field(segment,"maxSegmentWeight"));}
    return values;
  }
  private boolean sameConfiguration(){
    List<Object> actual=configuration();
    if(actual.size()!=runtimeConfiguration.size())return false;
    for(int i=0;i<actual.size();i++){
      Object left=runtimeConfiguration.get(i),right=actual.get(i);
      if(left instanceof Number? !left.equals(right):left!=right)return false;
    }
    return true;
  }

  private Snapshot read(){
    if(cache==null)throw new Jobs.Fault("capture_closed","Native pool cache snapshot is closed");
    require(cache,"com.google.common.cache.LocalCache$LocalLoadingCache");
    Object map=field(cache,"localCache");require(map,"com.google.common.cache.LocalCache");
    Object strong=field(type("com.google.common.cache.LocalCache$Strength"),null,"STRONG");
    if(field(map,"keyStrength")!=strong||field(map,"valueStrength")!=strong
        ||field(map,"keyEquivalence")!=com.google.common.base.Equivalence.equals()
        ||((Long)field(map,"expireAfterAccessNanos"))!=EXPIRE
        ||((Long)field(map,"expireAfterWriteNanos"))!=0L||((Long)field(map,"refreshNanos"))!=0L
        ||((Long)field(map,"maxWeight"))!=128L
        ||field(map,"weigher")!=field(type("com.google.common.cache.CacheBuilder$OneWeigher"),null,"INSTANCE")
        ||field(map,"removalListener")!=field(type("com.google.common.cache.CacheBuilder$NullListener"),null,"INSTANCE"))
      throw unsupported("Unadapted native bee cache configuration");
    require(field(map,"defaultLoader"),"forestry.apiculture.genetics.BeeGenome$1");
    Object[] segments=(Object[])field(map,"segments");
    if(segments.length==0||segments.length>128)throw unsupported("Invalid native bee cache segment count");
    List<ReentrantLock> locked=new ArrayList<>();
    try{
      for(Object segment:segments){
        require(segment,"com.google.common.cache.LocalCache$Segment");
        ReentrantLock lock=(ReentrantLock)segment;
        if(!lock.tryLock())throw changed();locked.add(lock);
      }
      Snapshot result=new Snapshot();
      Collections.addAll(result.pins,cache,map,segments,field(map,"defaultLoader"),field(map,"ticker"));
      result.shift=(Integer)field(map,"segmentShift");result.mask=(Integer)field(map,"segmentMask");
      if(Integer.bitCount(segments.length)!=1||result.mask!=segments.length-1
          ||result.shift!=32-Integer.numberOfTrailingZeros(segments.length))
        throw unsupported("Unadapted native cache segment routing");
      JsonArray rows=array();int scanned=0;long bytes=0;
      for(int segmentAt=0;segmentAt<segments.length;segmentAt++){
        Object segment=segments[segmentAt];int segmentEntries=0;
        pin(result,segment);Object rawTable=field(segment,"table");
        if(rawTable==null||rawTable.getClass()!=AtomicReferenceArray.class)throw unsupported("Unadapted native cache table");
        AtomicReferenceArray<?> table=(AtomicReferenceArray<?>)rawTable;pin(result,table);
        if(table.length()==0||table.length()>65536||Integer.bitCount(table.length())!=1)
          throw unsupported("Native cache table exceeds snapshot budget or is not power-of-two");
        int count=(Integer)field(segment,"count");
        if(count<0||count>4096)throw unsupported("Invalid native cache segment count");
        Segment route=new Segment(count,table.length());result.segments.add(route);
        JsonArray buckets=array();
        for(int slot=0;slot<table.length();slot++){
          List<Entry> bucket=new ArrayList<>();route.buckets.add(bucket);JsonArray bucketRows=array();
          Object entry=table.get(slot);pin(result,entry);
          for(;entry!=null;entry=field(entry,"next")){
            if(++scanned>4096)throw unsupported("Native cache entry budget exceeded");
            require(entry,"com.google.common.cache.LocalCache$StrongAccessEntry");pin(result,entry);
            Object key=field(entry,"key"),reference=field(entry,"valueReference");
            require(key,"net.minecraft.nbt.NBTTagCompound");
            require(reference,"com.google.common.cache.LocalCache$StrongValueReference");
            Object genome=field(reference,"referent");require(genome,"forestry.apiculture.genetics.BeeGenome");
            pin(result,key);pin(result,reference);pin(result,genome);
            NativeStackAudit.nbt((NBTTagCompound)key);
            Object chromosomes=field(genome,"chromosomes");pin(result,chromosomes);
            JsonElement cell=JsonNull.INSTANCE;
            if(chromosomes!=null){
              if(!(chromosomes instanceof IChromosome[])||((IChromosome[])chromosomes).length!=chromosomeCount)
                throw unsupported("Unadapted native cached genome array");
              IChromosome first=((IChromosome[])chromosomes)[0];pin(result,first);
              if(first!=null){
                require(first,"forestry.core.genetics.Chromosome");
                Object a=field(first,"primary"),b=field(first,"secondary");pin(result,a);pin(result,b);
                if(a!=null&&!(a instanceof IAllele)||b!=null&&!(b instanceof IAllele))throw unsupported("Invalid cached native allele");
                cell=object("primary",source.cachedAllele((IAllele)a),"secondary",source.cachedAllele((IAllele)b));
              }
            }
            int hash=(Integer)field(entry,"hash");long access=(Long)field(entry,"accessTime");
            if(((hash>>>result.shift)&result.mask)!=segmentAt||(hash&(table.length()-1))!=slot)
              throw unsupported("Invalid native cache entry routing");
            segmentEntries++;
            NBTTagCompound owned=(NBTTagCompound)((NBTTagCompound)key).copy();
            JsonObject row=object("hash",hash,"key",TypedNbt.encode(owned),"accessNanos",Long.toString(access),"chromosome",cell);
            bytes+=CanonicalJson.bytes(row).length;
            if(bytes>16*1024*1024)throw unsupported("Native cache snapshot exceeds byte budget");
            bucketRows.add(row);bucket.add(new Entry(hash,access,owned,cell));
          }
          buckets.add(bucketRows);
        }
        if(segmentEntries!=count)throw changed();
        rows.add(object("count",count,"buckets",buckets));
      }
      result.definition=object("shift",result.shift,"mask",result.mask,"segments",rows);
      if(CanonicalJson.bytes(result.definition).length>16*1024*1024)
        throw unsupported("Native cache snapshot exceeds total byte budget");
      result.state=readState(result,segments);
      result.signature=CanonicalJson.digest(object("lookup",result.definition,"state",result.state));return result;
    }finally{for(int i=locked.size()-1;i>=0;i--)locked.get(i).unlock();}
  }

  private JsonObject readState(Snapshot result,Object[] segments){
    StateScan scan=new StateScan(result);JsonArray rows=array();
    for(Object segment:segments){
      AtomicReferenceArray<?> table=(AtomicReferenceArray<?>)field(segment,"table");
      JsonArray buckets=array();
      for(int i=0;i<table.length();i++){
        JsonArray bucket=array();
        for(Object entry=table.get(i);entry!=null;entry=field(entry,"next"))bucket.add(value(scan.entry(entry)));
        buckets.add(bucket);
      }
      Object access=field(segment,"accessQueue"),recency=field(segment,"recencyQueue"),reads=field(segment,"readCount");
      require(access,"com.google.common.cache.LocalCache$AccessQueue");
      require(recency,"java.util.concurrent.ConcurrentLinkedQueue");
      require(reads,"java.util.concurrent.atomic.AtomicInteger");
      pin(result,access);pin(result,recency);pin(result,reads);
      JsonArray accessRows=array(),recentRows=array();
      for(Object entry:(Queue<?>)access)accessRows.add(value(scan.entry(entry)));
      for(Object entry:(Queue<?>)recency)recentRows.add(value(scan.entry(entry)));
      rows.add(object("count",field(segment,"count"),"threshold",field(segment,"threshold"),
          "maxWeight",Long.toString((Long)field(segment,"maxSegmentWeight")),
          "totalWeight",field(segment,"totalWeight"),
          "readCount",((java.util.concurrent.atomic.AtomicInteger)reads).get(),
          "buckets",buckets,"access",accessRows,"recency",recentRows));
    }
    JsonObject state=object("shift",result.shift,"mask",result.mask,"keys",scan.keys,"entries",scan.entries,"segments",rows);
    if(CanonicalJson.bytes(state).length>16*1024*1024)throw unsupported("Native cache transition observation exceeds byte budget");
    return state;
  }

  private final class StateScan {
    final Snapshot result;final JsonArray keys=array(),entries=array();
    final IdentityHashMap<Object,Integer> keyIds=new IdentityHashMap<>(),entryIds=new IdentityHashMap<>();
    final IdentityHashMap<Object,Object> mutableOwners=new IdentityHashMap<>();
    StateScan(Snapshot result){this.result=result;}
    int entry(Object entry){
      Integer previous=entryIds.get(entry);if(previous!=null)return previous;
      if(entries.size()>=4096)throw unsupported("Native cache transition entry budget exceeded");
      require(entry,"com.google.common.cache.LocalCache$StrongAccessEntry");pin(result,entry);
      Object key=field(entry,"key"),reference=field(entry,"valueReference");
      require(key,"net.minecraft.nbt.NBTTagCompound");
      require(reference,"com.google.common.cache.LocalCache$StrongValueReference");
      Object genome=field(reference,"referent");require(genome,"forestry.apiculture.genetics.BeeGenome");
      pin(result,key);pin(result,reference);pin(result,genome);
      Integer keyId=keyIds.get(key);
      if(keyId==null){
        NativeStackAudit.nbt((NBTTagCompound)key);checkAliases((net.minecraft.nbt.NBTBase)key,key);
        keyId=keys.size();keyIds.put(key,keyId);keys.add(TypedNbt.encode((NBTTagCompound)key));
      }
      Object raw=field(genome,"chromosomes");pin(result,raw);JsonElement chromosome=JsonNull.INSTANCE;
      if(raw!=null){
        if(!(raw instanceof IChromosome[])||((IChromosome[])raw).length!=chromosomeCount)
          throw unsupported("Unadapted native cached genome array");
        IChromosome first=((IChromosome[])raw)[0];pin(result,first);
        if(first!=null){
          require(first,"forestry.core.genetics.Chromosome");
          Object a=field(first,"primary"),b=field(first,"secondary");pin(result,a);pin(result,b);
          if(a!=null&&!(a instanceof IAllele)||b!=null&&!(b instanceof IAllele))throw unsupported("Invalid native cached allele");
          chromosome=object("primary",source.cachedAllele((IAllele)a),"secondary",source.cachedAllele((IAllele)b));
        }
      }
      int id=entries.size();entryIds.put(entry,id);
      entries.add(object("hash",field(entry,"hash"),"key",keyId,
          "accessNanos",Long.toString((Long)field(entry,"accessTime")),"chromosome",chromosome));return id;
    }
    private void checkAliases(net.minecraft.nbt.NBTBase tag,Object root){
      if(tag==null)return;
      // The portable key table preserves root identity. Shared mutable descendants
      // require a full NBT heap contract; never flatten them into independent copies.
      if(tag instanceof net.minecraft.nbt.NBTTagCompound||tag instanceof net.minecraft.nbt.NBTTagList){
        Object prior=mutableOwners.put(tag,root);
        if(prior!=null)throw unsupported("Native cache keys share mutable NBT descendants");
        if(tag instanceof NBTTagCompound)
          for(Object child:((Map<?,?>)field(tag,"tagMap")).values())checkAliases((net.minecraft.nbt.NBTBase)child,root);
        else for(Object child:(List<?>)field(tag,"tagList"))checkAliases((net.minecraft.nbt.NBTBase)child,root);
      }
    }
  }

  private static void pin(Snapshot result,Object value){
    Jobs.checkpoint();if(result.pins.size()>=65536)throw unsupported("Native cache identity budget exceeded");result.pins.add(value);
  }
  private static void require(Object value,String owner){
    if(value==null||value.getClass()!=type(owner))throw unsupported("Unadapted native cache owner: "+owner);
  }
  private static int rehash(int h){
    h+=(h<<15)^0xffffcd7d;h^=h>>>10;h+=h<<3;h^=h>>>6;h+=(h<<2)+(h<<14);return h^(h>>>16);
  }
  private void alive(){if(initial==null)throw new Jobs.Fault("capture_closed","Native pool cache snapshot is closed");}
  public void close(){initial=null;cache=null;binding=null;runtimeConfiguration=null;}
  private static Jobs.Fault unsupported(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native bee cache changed or is concurrently in use");}
  private static final class Snapshot {
    final List<Object> pins=new ArrayList<>();final List<Segment> segments=new ArrayList<>();String signature;JsonObject definition,state;int shift,mask;
  }
  private static final class Segment {
    final int count,length;final List<List<Entry>> buckets=new ArrayList<>();
    Segment(int count,int length){this.count=count;this.length=length;}
  }
  private static final class Entry {
    final int hash;final long access;final NBTTagCompound key;final JsonElement chromosome;
    Entry(int hash,long access,NBTTagCompound key,JsonElement chromosome){this.hash=hash;this.access=access;this.key=key;this.chromosome=chromosome;}
  }
}
