package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** Serial owned pool session. Reads, repairs, selection and per-recipe output
 * caches share one state. It never calls a live callback or mutates borrowed NBT.
 * Published program validation/admission is a separate boundary. */
final class MagicBeesPoolSession {
  private final MagicBeesPoolLaw law;
  private MagicBeesPoolCacheMachine cache;
  private final JsonArray entries;
  private final List<JsonElement> outputs=new ArrayList<>();
  private boolean valid=true;

  MagicBeesPoolSession(JsonObject source,JsonArray entries,JsonObject state){
    if(entries.size()>4096||CanonicalJson.bytes(entries).length>16*1024*1024)throw fault();
    law=new MagicBeesPoolLaw(source);cache=new MagicBeesPoolCacheMachine(state);
    this.entries=copy(entries).getAsJsonArray();
    for(JsonElement entry:this.entries)outputs.add(entry.getAsJsonObject().get("kind").getAsString().equals("bee")
        ?copy(entry.getAsJsonObject().get("cachedOutput")):JsonNull.INSTANCE);
  }
  JsonArray outputs(){alive();JsonArray rows=array();for(JsonElement output:outputs)rows.add(copy(output));return rows;}
  JsonObject cacheState(){alive();return cache.snapshot();}
  JsonElement takeOutput(int index){
    alive();
    if(index<0||index>=entries.size()||!entries.get(index).getAsJsonObject().get("kind").getAsString().equals("bee"))throw fault();
    JsonElement result=copy(outputs.get(index));
    outputs.set(index,copy(entries.get(index).getAsJsonObject().get("prototype")));return result;
  }

  JsonObject select(ItemStack offered,boolean alchemy,boolean conjuration,int mana,LongSupplier clock){
    alive();
    try{
      JsonObject result=selectOwned(offered,alchemy,conjuration,mana,clock);
      cache=new MagicBeesPoolCacheMachine(cache.snapshot());return result;
    }catch(RuntimeException|Error failure){valid=false;throw failure;}
  }
  private JsonObject selectOwned(ItemStack offered,boolean alchemy,boolean conjuration,int mana,LongSupplier clock){
    if(offered!=null)NativeStackAudit.auditRaw(offered);
    ItemStack owned=offered==null?null:offered.copy();
    JsonObject result=object("state","notMatched");int visited=0,predicates=0;
    if(owned!=null)for(int index=0;index<entries.size();index++){
      Jobs.checkpoint();visited++;
      JsonObject entry=entries.get(index).getAsJsonObject();boolean accepted=false;JsonElement output=null;
      switch(entry.get("kind").getAsString()){
        case "bee":{
          JsonObject match=law.evaluateOwned(entry.get("form").getAsString(),entry.get("inputSpecies").getAsString(),
              entry.get("outputSpecies").getAsString(),owned,new MagicBeesPoolLaw.GenomeReads(){
                public JsonElement read(NBTTagCompound genome){
                  int id=cache.bindOwned(genome);
                  JsonObject read=cache.get(id,clock,key->law.loadGenome(cache.key(key)));
                  if(read.get("kind").getAsString().equals("nativeError"))
                    throw new MagicBeesPoolLaw.NativeFailure();
                  return read.get("chromosome");
                }
                public void finished(){}
              });
          String state=match.get("state").getAsString();
          if(state.equals("nativeError"))return finish(object("state","nativeError","index",index),owned,visited);
          if(state.equals("matched")){
            accepted=true;outputs.set(index,copy(match.get("output")));
          }
          break;
        }
        case "item":
          for(JsonElement raw:entry.getAsJsonArray("predicates")){
            if(++predicates>65536)throw fault();JsonObject predicate=raw.getAsJsonObject();int meta=predicate.get("metadata").getAsInt();
            if(predicate.get("registry").getAsString().equals(Item.itemRegistry.getNameForObject(owned.getItem()))
                &&(meta==32767||meta==Items.feather.getDamage(owned))){accepted=true;break;}
          }
          output=entry.get("output");break;
        case "mini":{
          NBTBase key=owned.getTagCompound()==null?null:owned.getTagCompound().getTag("type");
          String observed;
          if(key==null)observed="";
          else if(key instanceof NBTTagString)observed=((NBTTagString)key).func_150285_a_();
          else{
            observed=null;
            if(entry.has("coercions"))for(JsonElement raw:entry.getAsJsonArray("coercions")){
              JsonObject observation=raw.getAsJsonObject(),input=observation.getAsJsonObject("input");
              if(input.get("registry").getAsString().equals(Item.itemRegistry.getNameForObject(owned.getItem()))
                  &&input.get("meta").getAsInt()==Items.feather.getDamage(owned)
                  &&input.get("nbt").equals(TypedNbt.encode(owned.getTagCompound()))){
                String value=observation.get("value").getAsString();
                if(observed!=null&&!observed.equals(value))throw fault();
                observed=value;
              }
            }
            if(observed==null)throw new Jobs.Fault("recipe_unsupported","Native pool lacks a source-bound subtile observation");
          }
          accepted=entry.get("key").getAsString().equals(observed);
          output=entry.get("output");break;
        }
        default:throw fault();
      }
      if(!accepted||entry.get("alchemy").getAsBoolean()&&!alchemy
          ||entry.get("conjuration").getAsBoolean()&&!conjuration)continue;
      if(mana<entry.get("mana").getAsInt())result=object("state","insufficientMana","index",index);
      else result=object("state","selected","index",index,"output",
          entry.get("kind").getAsString().equals("bee")?takeOutput(index):copy(output));
      break;
    }
    return finish(result,owned,visited);
  }
  private static JsonObject finish(JsonObject result,ItemStack offered,int visited){
    return object("result",result,"offeredAfter",RecipeRow.stackEvidence(offered),"visited",visited);
  }
  private static JsonElement copy(JsonElement value){if(value==null)throw fault();return new JsonParser().parse(value.toString());}
  private void alive(){if(!valid)throw fault();}
  private static Jobs.Fault fault(){return new Jobs.Fault("recipe_unsupported","Invalid or unadapted native pool session");}
}
