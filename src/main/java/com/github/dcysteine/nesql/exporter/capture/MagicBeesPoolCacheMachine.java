package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.nbt.*;

/** Owned serial Guava-17 transition kernel. Clock reads are explicit; no live cache,
 * ticker or mod callback is invoked. The caller supplies an owned loader.
 * Production admission separately has to bind the reviewed runtime and clock. */
final class MagicBeesPoolCacheMachine {
  private static final long EXPIRE=60000000000L;
  private final int shift,mask;
  private final List<NBTTagCompound> keys=new ArrayList<>();
  private final List<Segment> segments=new ArrayList<>();

  MagicBeesPoolCacheMachine(JsonObject state){
    if(CanonicalJson.bytes(state).length>16*1024*1024)throw fault();
    shift=state.get("shift").getAsInt();mask=state.get("mask").getAsInt();
    for(JsonElement raw:state.getAsJsonArray("keys")){
      NBTBase key=decode(raw.getAsJsonObject(),0,new int[1]);
      if(!(key instanceof NBTTagCompound)||keys.size()>=4096)throw fault();
      keys.add((NBTTagCompound)key);
    }
    List<Entry> entries=new ArrayList<>();
    for(JsonElement raw:state.getAsJsonArray("entries")){
      JsonObject row=raw.getAsJsonObject();int key=row.get("key").getAsInt();
      if(key<0||key>=keys.size()||entries.size()>=4096)throw fault();
      entries.add(new Entry(row.get("hash").getAsInt(),key,nanos(row.get("accessNanos")),copy(row.get("chromosome"))));
    }
    JsonArray rows=state.getAsJsonArray("segments");
    if(rows.size()==0||rows.size()>128||Integer.bitCount(rows.size())!=1
        ||mask!=rows.size()-1||shift!=32-Integer.numberOfTrailingZeros(rows.size()))throw fault();
    Set<Entry> active=Collections.newSetFromMap(new IdentityHashMap<Entry,Boolean>());
    long totalMax=0;
    for(int i=0;i<rows.size();i++){
      JsonObject row=rows.get(i).getAsJsonObject();Segment segment=new Segment();
      segment.count=row.get("count").getAsInt();segment.threshold=row.get("threshold").getAsInt();
      segment.maxWeight=nanos(row.get("maxWeight"));segment.weight=row.get("totalWeight").getAsInt();
      totalMax+=segment.maxWeight;
      segment.reads=row.get("readCount").getAsInt();JsonArray buckets=row.getAsJsonArray("buckets");
      if(buckets.size()==0||buckets.size()>65536||Integer.bitCount(buckets.size())!=1
          ||segment.count<0||segment.count>4096||segment.weight!=segment.count
          ||segment.maxWeight<0||segment.maxWeight>128||segment.count>segment.maxWeight
          ||segment.threshold<0||segment.threshold>buckets.size())throw fault();
      int count=0;
      for(int slot=0;slot<buckets.size();slot++){
        List<Entry> bucket=references(buckets.get(slot).getAsJsonArray(),entries);segment.buckets.add(bucket);
        for(Entry entry:bucket){
          if(!active.add(entry)||((entry.hash>>>shift)&mask)!=i||(entry.hash&(buckets.size()-1))!=slot)throw fault();
          count++;
        }
      }
      segment.access.addAll(references(row.getAsJsonArray("access"),entries));
      segment.recency.addAll(references(row.getAsJsonArray("recency"),entries));
      if(count!=segment.count||segment.access.size()!=count
          ||new HashSet<>(segment.access).size()!=count)throw fault();
      for(Entry entry:segment.access)if(!segment.buckets.get(entry.hash&(buckets.size()-1)).contains(entry))throw fault();
      for(Entry entry:segment.recency)if(((entry.hash>>>shift)&mask)!=i)throw fault();
      segments.add(segment);
    }
    if(totalMax!=128)throw fault();
  }

  NBTTagCompound key(int id){if(id<0||id>=keys.size())throw fault();return keys.get(id);}
  int bindOwned(NBTTagCompound key){
    NativeStackAudit.nbt(key);
    for(int i=0;i<keys.size();i++)if(keys.get(i)==key)return i;
    if(keys.size()>=4096)throw fault();keys.add(key);return keys.size()-1;
  }

  JsonObject get(int keyId,LongSupplier clock,IntFunction<JsonObject> loader){
    NBTTagCompound query=key(keyId);NativeStackAudit.nbt(query);
    int hash=rehash(query.hashCode());Segment segment=segments.get((hash>>>shift)&mask);
    try{
      Entry found=segment.count==0?null:find(segment,hash,keyId);
      if(found!=null){
        long now=clock.getAsLong();
        if(now-found.access<EXPIRE){
          found.access=now;segment.recency.add(found);
          return object("kind","hit","chromosome",copy(found.chromosome));
        }
        expire(segment,now);
      }
      long now=clock.getAsLong();cleanup(segment,now);
      Entry placeholder=find(segment,hash,keyId);
      if(placeholder==null){
        placeholder=new Entry(hash,keyId,Long.MAX_VALUE,JsonNull.INSTANCE);
        segment.buckets.get(hash&(segment.buckets.size()-1)).add(0,placeholder);
      }else if(now-placeholder.access>=EXPIRE){
        segment.access.remove(placeholder);segment.weight--;segment.count--;
      }else{
        // A later clock read can precede the first one (native nanoTime wraps).
        placeholder.access=now;moveLast(segment.access,placeholder);
        return object("kind","hit","chromosome",copy(placeholder.chromosome));
      }
      Object loading=new Object();placeholder.loading=loading;
      JsonObject loaded=loader.apply(keyId);NativeStackAudit.nbt(query);
      if(loaded.has("nativeError")&&loaded.get("nativeError").getAsBoolean()){
        // removeLoadingValue searches using the stored hash plus current key equality.
        Entry current=find(segment,hash,keyId);
        if(current!=null&&current.loading==loading)remove(segment,current,false);
        return object("kind","nativeError");
      }
      if(!loaded.has("chromosome"))throw fault();
      long write=clock.getAsLong();cleanup(segment,write);
      if(segment.count+1>segment.threshold)expand(segment);
      Entry current=find(segment,hash,keyId);
      if(current!=null&&current.loading!=loading)
        throw new Jobs.Fault("recipe_unsupported","Native mutable-key collision retained a loading reference; this state is not yet adapted");
      if(current==null){
        current=new Entry(hash,keyId,Long.MAX_VALUE,JsonNull.INSTANCE);
        segment.buckets.get(hash&(segment.buckets.size()-1)).add(0,current);
      }
      current.loading=null;current.chromosome=copy(loaded.get("chromosome"));drain(segment);
      current.access=write;moveLast(segment.access,current);segment.count++;segment.weight++;
      drain(segment);
      while(segment.weight>segment.maxWeight)remove(segment,segment.access.get(0),true);
      return object("kind","loaded","chromosome",copy(loaded.get("chromosome")));
    }finally{
      if((++segment.reads&63)==0)cleanup(segment,clock.getAsLong());
    }
  }

  JsonObject snapshot(){
    JsonArray keyRows=array(),entryRows=array(),rows=array();
    IdentityHashMap<Entry,Integer> ids=new IdentityHashMap<>();
    Map<Integer,Integer> keyIds=new HashMap<>();
    Function<Entry,Integer> encode=entry->{
      Integer existing=ids.get(entry);if(existing!=null)return existing;
      int key=keyIds.computeIfAbsent(entry.key,id->{int next=keyRows.size();keyRows.add(TypedNbt.encode(keys.get(id)));return next;});
      int id=entryRows.size();ids.put(entry,id);
      entryRows.add(object("hash",entry.hash,"key",key,"accessNanos",Long.toString(entry.access),"chromosome",copy(entry.chromosome)));
      return id;
    };
    for(Segment segment:segments){
      JsonArray buckets=array(),access=array(),recency=array();
      for(List<Entry> bucket:segment.buckets){JsonArray cells=array();for(Entry entry:bucket)cells.add(value(encode.apply(entry)));buckets.add(cells);}
      for(Entry entry:segment.access)access.add(value(encode.apply(entry)));
      for(Entry entry:segment.recency)recency.add(value(encode.apply(entry)));
      rows.add(object("count",segment.count,"threshold",segment.threshold,"maxWeight",Long.toString(segment.maxWeight),
          "totalWeight",segment.weight,"readCount",segment.reads,"buckets",buckets,"access",access,"recency",recency));
    }
    return object("shift",shift,"mask",mask,"keys",keyRows,"entries",entryRows,"segments",rows);
  }

  private Entry find(Segment segment,int hash,int key){
    for(Entry entry:segment.buckets.get(hash&(segment.buckets.size()-1)))
      if(entry.hash==hash&&(entry.key==key||keys.get(key).equals(keys.get(entry.key))))return entry;
    return null;
  }
  private void cleanup(Segment segment,long now){expire(segment,now);segment.reads=0;}
  private void expire(Segment segment,long now){
    drain(segment);
    while(!segment.access.isEmpty()&&now-segment.access.get(0).access>=EXPIRE)remove(segment,segment.access.get(0),true);
  }
  private void drain(Segment segment){
    for(Entry entry:segment.recency)if(segment.access.contains(entry))moveLast(segment.access,entry);
    segment.recency.clear();
  }
  private static void moveLast(List<Entry> queue,Entry entry){queue.remove(entry);queue.add(entry);}
  private Entry copyEntry(Segment segment,Entry original){
    Entry copy=new Entry(original.hash,original.key,original.access,original.chromosome);
    copy.loading=original.loading;
    int position=segment.access.indexOf(original);
    if(position>=0)segment.access.set(position,copy);
    // Recency still points at the old entry, whose access links native copyEntry clears.
    return copy;
  }
  private void remove(Segment segment,Entry entry,boolean active){
    List<Entry> bucket=segment.buckets.get(entry.hash&(segment.buckets.size()-1));
    int at=bucket.indexOf(entry);if(at<0)throw fault();
    List<Entry> replacement=new ArrayList<>(bucket.subList(at+1,bucket.size()));
    for(int i=0;i<at;i++)replacement.add(0,copyEntry(segment,bucket.get(i)));
    segment.buckets.set(entry.hash&(segment.buckets.size()-1),replacement);
    segment.access.remove(entry);
    if(active){segment.count--;segment.weight--;}
  }
  private void expand(Segment segment){
    int length=segment.buckets.size();if(length>=65536)throw fault();
    List<List<Entry>> grown=new ArrayList<>();
    for(int i=0;i<length*2;i++)grown.add(new ArrayList<>());
    int mask=length*2-1;
    for(List<Entry> bucket:segment.buckets){
      if(bucket.isEmpty())continue;
      int last=0,route=bucket.get(0).hash&mask;
      for(int i=1;i<bucket.size();i++)if((bucket.get(i).hash&mask)!=route){last=i;route=bucket.get(i).hash&mask;}
      grown.get(route).addAll(bucket.subList(last,bucket.size()));
      for(int i=0;i<last;i++){Entry copy=copyEntry(segment,bucket.get(i));grown.get(copy.hash&mask).add(0,copy);}
    }
    segment.buckets=grown;segment.threshold=length*2*3/4;
  }
  private static List<Entry> references(JsonArray raw,List<Entry> entries){
    if(raw.size()>65536)throw fault();List<Entry> result=new ArrayList<>();
    for(JsonElement id:raw){int at=id.getAsInt();if(at<0||at>=entries.size())throw fault();result.add(entries.get(at));}return result;
  }
  private static int rehash(int h){
    h+=(h<<15)^0xffffcd7d;h^=h>>>10;h+=h<<3;h^=h>>>6;h+=(h<<2)+(h<<14);return h^(h>>>16);
  }
  private static long nanos(JsonElement value){
    String raw=value.getAsString();long at=Long.parseLong(raw);if(!Long.toString(at).equals(raw))throw fault();return at;
  }
  private static JsonElement copy(JsonElement raw){return new JsonParser().parse(raw.toString());}
  private static NBTBase decode(JsonObject row,int depth,int[] nodes){
    if(depth>48||++nodes[0]>65536)throw fault();String kind=row.get("type").getAsString();JsonElement raw=row.get("value");
    switch(kind){
      case "byte":return new NBTTagByte(Byte.parseByte(raw.getAsString()));
      case "short":return new NBTTagShort(Short.parseShort(raw.getAsString()));
      case "int":return new NBTTagInt(Integer.parseInt(raw.getAsString()));
      case "long":return new NBTTagLong(Long.parseLong(raw.getAsString()));
      case "float":return new NBTTagFloat(Float.intBitsToFloat((int)Long.parseLong(raw.getAsString(),16)));
      case "double":return new NBTTagDouble(Double.longBitsToDouble(Long.parseUnsignedLong(raw.getAsString(),16)));
      case "string":return new NBTTagString(raw.getAsString());
      case "byte_array":return new NBTTagByteArray(Base64.getDecoder().decode(raw.getAsString()));
      case "int_array":{
        JsonArray values=raw.getAsJsonArray();int[] array=new int[values.size()];
        for(int i=0;i<array.length;i++)array[i]=Integer.parseInt(values.get(i).getAsString());return new NBTTagIntArray(array);
      }
      case "compound":{
        NBTTagCompound result=new NBTTagCompound();
        for(Map.Entry<String,JsonElement> child:raw.getAsJsonObject().entrySet())
          result.setTag(child.getKey(),decode(child.getValue().getAsJsonObject(),depth+1,nodes));
        return result;
      }
      case "list":{
        NBTTagList result=new NBTTagList();for(JsonElement child:raw.getAsJsonArray())result.appendTag(decode(child.getAsJsonObject(),depth+1,nodes));
        if(raw.getAsJsonArray().size()==0){
          String[] types={"end","byte","short","int","long","float","double","byte_array","string","list","compound","int_array"};
          int type=Arrays.asList(types).indexOf(row.get("element").getAsString());if(type<0)throw fault();
          try{java.lang.reflect.Field field=cpw.mods.fml.relauncher.ReflectionHelper.findField(NBTTagList.class,"tagType","field_74746_b");field.setAccessible(true);field.setByte(result,(byte)type);}
          catch(IllegalAccessException error){throw new IllegalStateException(error);}
        }
        return result;
      }
      default:throw fault();
    }
  }
  private static Jobs.Fault fault(){return new Jobs.Fault("recipe_unsupported","Invalid or unadapted owned native pool cache transition");}
  private static final class Segment {
    int count,threshold,weight,reads;long maxWeight;
    List<List<Entry>> buckets=new ArrayList<>();final List<Entry> access=new ArrayList<>(),recency=new ArrayList<>();
  }
  private static final class Entry {
    final int hash,key;long access;JsonElement chromosome;Object loading;
    Entry(int hash,int key,long access,JsonElement chromosome){this.hash=hash;this.key=key;this.access=access;this.chromosome=chromosome;}
  }
}
