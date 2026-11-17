package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Snapshot-only precursor to the cross-project pool contract. No live registry,
 * recipe callback or global cache access. Explicit read traces describe resolved
 * historical hits; they do not infer native key reachability, eviction or expiry.
 * Production callback admission remains disabled until that binding is explicit. */
final class MagicBeesPoolLaw {
  private final JsonObject source;
  private final Map<String,JsonObject> lookup=new HashMap<>(),alleles=new HashMap<>();
  private final int count;
  private final boolean repair;

  MagicBeesPoolLaw(JsonObject source){
    this.source=new JsonParser().parse(source.toString()).getAsJsonObject();
    count=this.source.getAsJsonArray("chromosomes").size();
    repair=this.source.get("clearInvalidChromosomes").getAsBoolean();
    for(Map.Entry<String,JsonElement> entry:this.source.getAsJsonObject("definitions").entrySet())
      alleles.put(entry.getKey(),entry.getValue().getAsJsonObject());
    for(JsonElement raw:this.source.getAsJsonArray("alleles")){
      JsonObject value=raw.getAsJsonObject();
      if(!value.has("aliasOnly"))lookup.put(value.get("key").getAsString(),alleles.get(value.get("uid").getAsString()));
    }
    for(Map.Entry<String,JsonElement> entry:this.source.getAsJsonObject("aliases").entrySet())
      lookup.putIfAbsent(entry.getKey(),alleles.get(entry.getValue().getAsString()));
  }

  JsonObject evaluate(String form,String input,String output,ItemStack offered,JsonArray reads){
    return transition(form,input,output,offered,reads).getAsJsonObject("result");
  }

  /** Own the match-time repairs separately from the result copy. Later registry
   * entries observe repairs even when this callback rejects the stack. */
  JsonObject transition(String form,String input,String output,ItemStack offered,JsonArray reads){
    if(offered!=null)NativeStackAudit.nbt(offered.getTagCompound());
    ItemStack owned=offered==null?null:offered.copy();
    JsonObject result=evaluateOwned(form,input,output,owned,reads);
    return object("result",result,"offeredAfter",RecipeRow.stackEvidence(owned));
  }

  /** Internal selector entry point. The caller owns this stack and carries repairs forward. */
  JsonObject evaluateOwned(String form,String input,String output,ItemStack offered,JsonArray reads){
    return evaluateOwned(form,input,output,offered,new ReadTrace(reads));
  }
  interface GenomeReads {
    JsonElement read(NBTTagCompound genome);
    void finished();
  }
  JsonObject evaluateOwned(String form,String input,String output,ItemStack offered,GenomeReads trace){
    if(offered==null||offered.getItem()==null)return object("state","notMatched");
    String registry=Item.itemRegistry.getNameForObject(offered.getItem());
    if(!source.getAsJsonObject("forms").has(form))throw new Jobs.Fault("recipe_unsupported","Unknown native bee form");
    if(!source.getAsJsonObject("forms").get(form).getAsString().equals(registry))
      return object("state","notMatched");
    NativeStackAudit.nbt(offered.getTagCompound());
    NBTTagCompound tag=offered.getTagCompound();
    try{
      Species species;
      if(tag==null||!tag.hasKey("Genome"))species=parsedSpecies(active(source.getAsJsonArray("forest").get(0)));
      else{
        NBTTagCompound genome=tag.getCompoundTag("Genome");
        species=species(trace.read(genome));
      }
      // Bee.readFromNBT parses Mate before SpeciesRecipeManaInfusion compares species.
      if(tag!=null&&tag.hasKey("Mate")){
        NBTTagCompound mate=tag.getCompoundTag("Mate");
        trace.read(mate);
      }
      trace.finished();
      if(species==null||!species.beeSpecies)throw new NativeFailure();
      if(!species.sourceIdentity||!species.uid.equals(input))return object("state","notMatched");
      if(tag==null)throw new NativeFailure();
      ItemStack result=offered.copy();
      NBTBase raw=result.getTagCompound().getCompoundTag("Genome").getTag("Chromosomes");
      if(!(raw instanceof NBTTagList))throw new NativeFailure();
      // Native replaceSpecies addresses physical entry zero. Empty/wrong-element
      // lists return a detached compound; it must not become an invented entry.
      NBTTagCompound first=((NBTTagList)raw).getCompoundTagAt(0);
      first.setString("UID0",output);first.setString("UID1",output);result.stackSize=1;
      return object("state","matched","output",RecipeRow.stackEvidence(result));
    }catch(NativeFailure failure){return object("state","nativeError");}
  }

  private Species parsedSpecies(String uid){
    JsonObject allele=alleles.get(uid);
    return allele==null?null:new Species(uid,true,valid(allele,0));
  }

  private Species species(JsonElement chromosome){
    if(chromosome==null||chromosome.isJsonNull())return null;
    if(!chromosome.isJsonObject())throw traceFault();JsonObject cell=chromosome.getAsJsonObject();
    JsonObject primary=cachedAllele(cell.get("primary")),secondary=cachedAllele(cell.get("secondary"));
    if(primary==null||secondary==null)return null;
    JsonObject active=primary.get("dominant").getAsBoolean()?primary:
        secondary.get("dominant").getAsBoolean()?secondary:primary;
    return new Species(active.get("uid").getAsString(),active.get("sourceIdentity").getAsBoolean(),
        active.get("beeSpecies").getAsBoolean());
  }
  private final class ReadTrace implements GenomeReads {
    private final JsonArray reads;
    private int at;
    ReadTrace(JsonArray reads){
      if(reads==null||reads.size()>2)throw traceFault();
      this.reads=new JsonParser().parse(reads.toString()).getAsJsonArray();
    }
    public JsonElement read(NBTTagCompound genome){
      if(at==reads.size())throw traceFault();
      JsonElement raw=reads.get(at++);
      if(!raw.isJsonObject())throw traceFault();
      JsonObject read=raw.getAsJsonObject();
      if(!read.has("kind")||!read.get("kind").isJsonPrimitive())throw traceFault();
      switch(read.get("kind").getAsString()){
        case "miss":{
          JsonObject loaded=loadGenome(genome);if(loaded.has("nativeError"))throw new NativeFailure();
          return loaded.get("chromosome");
        }
        case "hit":
          if(!read.has("chromosome"))throw traceFault();
          return read.get("chromosome"); // Unused Mate must not dereference malformed cached cells.
        default:throw traceFault();
      }
    }
    public void finished(){if(at!=reads.size())throw traceFault();}
  }
  private static JsonObject cachedAllele(JsonElement raw){
    if(raw==null)throw traceFault();
    if(raw.isJsonNull())return null;
    if(!raw.isJsonObject())throw traceFault();JsonObject value=raw.getAsJsonObject();
    JsonElement uid=value.get("uid");
    if(uid==null||!uid.isJsonPrimitive()||!uid.getAsJsonPrimitive().isString()
        ||uid.getAsString().isEmpty()||uid.getAsString().length()>16384)throw traceFault();
    for(String flag:new String[]{"dominant","sourceIdentity","beeSpecies"}){
      JsonElement bit=value.get(flag);
      if(bit==null||!bit.isJsonPrimitive()||!bit.getAsJsonPrimitive().isBoolean())throw traceFault();
    }
    return value;
  }
  private static Jobs.Fault traceFault(){return new Jobs.Fault("recipe_unsupported","Invalid or incomplete explicit native pool cache-read trace");}
  private static final class Species {
    final String uid;final boolean sourceIdentity,beeSpecies;
    Species(String uid,boolean sourceIdentity,boolean beeSpecies){this.uid=uid;this.sourceIdentity=sourceIdentity;this.beeSpecies=beeSpecies;}
  }

  JsonObject loadGenome(NBTTagCompound genome){
    try{
      JsonObject pair=parse(genome);
      return object("chromosome",pair==null?null:object("primary",loadedAllele(descriptor(pair.get("primary"))),
          "secondary",loadedAllele(descriptor(pair.get("secondary")))));
    }catch(NativeFailure failure){return object("nativeError",true);}
  }
  private JsonObject loadedAllele(JsonObject allele){
    return allele==null?null:object("uid",uid(allele),"dominant",allele.get("dominant"),
        "sourceIdentity",true,"beeSpecies",valid(allele,0));
  }
  private JsonObject parse(NBTTagCompound genome){
    NBTTagList list=genome.getTagList("Chromosomes",10);
    if(list.tagCount()>65536)throw new Jobs.Fault("recipe_limit","Pool genome exceeds native entry budget");
    JsonObject[] cells=new JsonObject[count];
    for(int i=0;i<list.tagCount();i++){
      Jobs.checkpoint();NBTTagCompound entry=list.getCompoundTagAt(i);int slot=entry.getByte("Slot");
      if(slot<0||slot>=count)continue;
      JsonObject primary=lookup.get(entry.getString("UID0")),secondary=lookup.get(entry.getString("UID1"));
      if(repair){
        JsonObject fallback=valid(primary,slot)?primary:valid(secondary,slot)?secondary:descriptor(source.getAsJsonArray("defaults").get(slot));
        boolean changed=false;
        if(!valid(primary,slot)){primary=fallback;changed=true;}
        if(!valid(secondary,slot)){secondary=fallback;changed=true;}
        if(changed){
          if(primary==null||secondary==null)throw new NativeFailure();
          entry.setString("UID0",uid(primary));entry.setString("UID1",uid(secondary));
        }
      }
      if(!valid(primary,slot)||!valid(secondary,slot))throw new NativeFailure();
      cells[slot]=object("primary",uid(primary),"secondary",uid(secondary));
    }
    String species=cells[0]==null?null:active(cells[0]);
    if(cells[0]!=null){
      JsonElement template=species==null?null:source.getAsJsonObject("templates").get(species);
      if(template==null||!template.isJsonArray()||template.getAsJsonArray().size()!=count)throw new NativeFailure();
      JsonArray genes=template.getAsJsonArray();
      for(int slot=0;slot<count;slot++)if(cells[slot]==null&&!genes.get(slot).isJsonNull())
        cells[slot]=object("primary",genes.get(slot),"secondary",genes.get(slot));
    }
    return cells[0];
  }

  private boolean valid(JsonObject allele,int slot){
    if(allele==null)return false;
    for(JsonElement value:allele.getAsJsonArray("slots"))if(value.getAsInt()==slot)return true;
    return false;
  }
  private JsonObject descriptor(JsonElement uid){return uid==null||uid.isJsonNull()?null:alleles.get(uid.getAsString());}
  private static String uid(JsonObject allele){return allele.get("uid").getAsString();}
  private String active(JsonElement raw){
    if(raw==null||raw.isJsonNull())return null;
    JsonObject pair=raw.getAsJsonObject();JsonObject a=descriptor(pair.get("primary")),b=descriptor(pair.get("secondary"));
    if(a==null||b==null)return null;
    return a.get("dominant").getAsBoolean()?uid(a):b.get("dominant").getAsBoolean()?uid(b):uid(a);
  }
  static final class NativeFailure extends RuntimeException {}
}
