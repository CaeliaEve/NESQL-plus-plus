package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;

/** Shared source/registry/cache transport. Admission still requires binding the
 * live callback fields and publishing the matching recipe/result contract. */
final class MagicBeesPoolProgram {
  static JsonObject rules(JsonObject source,JsonArray entries,JsonObject cache,JsonObject runtime){
    if(!runtime.get("runtime").getAsString().equals("guava17StrongAccess")
        ||!runtime.get("clock").getAsString().equals("javaSystemNanoTime")
        ||!runtime.get("artifactSha256").getAsString().equals("8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc")
        ||!runtime.get("scope").getAsString().equals("serialOwnedTransitionsFromBoundSnapshotWithExplicitTickerReads")
        ||Long.parseLong(runtime.get("afterNanos").getAsString())-Long.parseLong(runtime.get("beforeNanos").getAsString())<0
        ||entries.size()>4096)throw fault();
    new MagicBeesPoolLaw(source);
    new MagicBeesPoolCacheMachine(cache);
    JsonObject settings=object("revision",1,"forestry",source.get("forestry"),"magicbees",source.get("magicbees"),
        "root",source.get("root"),"clearInvalidChromosomes",source.get("clearInvalidChromosomes"),
        "shift",cache.get("shift"),"mask",cache.get("mask"),"runtime",runtime);
    JsonObject result=object("settings",array(settings),"chromosomes",source.get("chromosomes"),
        "definitions",map(source.getAsJsonObject("definitions"),"allele"),
        "alleles",source.get("alleles"),"aliases",map(source.getAsJsonObject("aliases"),"uid"),
        "templates",map(source.getAsJsonObject("templates"),"alleles"),
        "defaults",source.get("defaults"),"forest",source.get("forest"),
        "forms",map(source.getAsJsonObject("forms"),"registry"),"entries",entries,
        "keys",cache.get("keys"),"cacheEntries",cache.get("entries"),"segments",cache.get("segments"));
    if(CanonicalJson.bytes(result).length>16*1024*1024)throw fault();
    return new JsonParser().parse(result.toString()).getAsJsonObject();
  }
  private static JsonArray map(JsonObject values,String name){
    JsonArray rows=array();SortedSet<String> keys=new TreeSet<>();
    for(Map.Entry<String,JsonElement> row:values.entrySet())keys.add(row.getKey());
    for(String key:keys){Jobs.checkpoint();rows.add(object("key",key,name,values.get(key)));}
    return rows;
  }
  private static Jobs.Fault fault(){return new Jobs.Fault("recipe_unsupported","Invalid native bee pool shared program");}
}
