package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Owned ordered-selection precursor. Explicit traces do not model cache writes
 * or clock progression. It is not a published recipe/program admission path. */
final class MagicBeesPoolSelection {
  private MagicBeesPoolSelection() {}

  static JsonObject select(JsonObject source,JsonArray entries,ItemStack offered,
      boolean alchemy,boolean conjuration,int mana,JsonArray traces){
    if(entries==null||traces==null||entries.size()>4096||traces.size()>entries.size()
        ||CanonicalJson.bytes(entries).length>16*1024*1024)throw fault();
    if(offered!=null)NativeStackAudit.nbt(offered.getTagCompound());
    ItemStack owned=offered==null?null:offered.copy();
    if(owned==null)return finish(object("state","notMatched"),null,0,0,traces);
    MagicBeesPoolLaw law=new MagicBeesPoolLaw(source);
    JsonObject result=object("state","notMatched");int visited=0,at=0,predicates=0;
    for(int index=0;index<entries.size();index++){
      Jobs.checkpoint();visited++;
      JsonObject entry=entries.get(index).getAsJsonObject();JsonObject matched=null;
      boolean accepted=false;
      switch(entry.get("kind").getAsString()){
        case "bee":
          if(at==traces.size())throw fault();JsonObject trace=traces.get(at++).getAsJsonObject();
          if(trace.get("index").getAsInt()!=index)throw fault();
          matched=law.evaluateOwned(entry.get("form").getAsString(),entry.get("inputSpecies").getAsString(),
              entry.get("outputSpecies").getAsString(),owned,trace.getAsJsonArray("reads"));
          if(matched.get("state").getAsString().equals("nativeError")){
            result=object("state","nativeError","index",index);return finish(result,owned,visited,at,traces);
          }
          accepted=matched.get("state").getAsString().equals("matched");break;
        case "item":
          for(JsonElement raw:entry.getAsJsonArray("predicates")){
            if(++predicates>65536)throw fault();JsonObject predicate=raw.getAsJsonObject();
            int meta=predicate.get("metadata").getAsInt();
            if(predicate.get("registry").getAsString().equals(Item.itemRegistry.getNameForObject(owned.getItem()))
                &&(meta==32767||meta==Items.feather.getDamage(owned))){accepted=true;break;}
          }
          break;
        case "mini":
          NBTTagCompound tag=owned.getTagCompound();NBTBase key=tag==null?null:tag.getTag("type");
          // Other native key coercions need explicit observations, not invented compound order.
          if(key!=null&&!(key instanceof NBTTagString))throw fault();
          accepted=entry.get("key").getAsString().equals(key==null?"":((NBTTagString)key).func_150285_a_());break;
        default:throw fault();
      }
      // TilePool invokes matches before catalyst checks; never skip the above effects.
      if(!accepted||entry.get("alchemy").getAsBoolean()&&!alchemy
          ||entry.get("conjuration").getAsBoolean()&&!conjuration)continue;
      if(mana<entry.get("mana").getAsInt())result=object("state","insufficientMana","index",index);
      else result=object("state","selected","index",index,"output",
          matched==null?new JsonParser().parse(entry.get("output").toString()):matched.get("output"));
      break; // The first eligible match blocks cheaper recipes even with insufficient mana.
    }
    return finish(result,owned,visited,at,traces);
  }

  private static JsonObject finish(JsonObject result,ItemStack owned,int visited,int at,JsonArray traces){
    if(at!=traces.size())throw fault();
    return object("result",result,"offeredAfter",RecipeRow.stackEvidence(owned),"visited",visited);
  }
  private static Jobs.Fault fault(){return new Jobs.Fault("recipe_unsupported","Invalid or unsupported explicit native pool selection trace");}
}
