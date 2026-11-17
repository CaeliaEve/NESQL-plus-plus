package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;

/** Recipe replay needs its admitted members; the separate Database keeps the complete catalogue. */
final class BinnieWitnesses {
  private final JsonObject domain;
  private final Map<String,JsonElement> members=new HashMap<>();
  BinnieWitnesses(JsonObject domain){
    this.domain=domain;
    for(JsonElement member:domain.getAsJsonArray("members")){
      Jobs.checkpoint();String key=CanonicalJson.digest(member.getAsJsonObject().get("stack"));
      JsonElement prior=members.putIfAbsent(key,member);
      if(prior!=null&&!prior.equals(member))throw new Jobs.Fault("recipe_unsupported","Conflicting native Binnie member witnesses");
    }
  }
  JsonObject select(List<JsonObject> inputs){
    JsonObject result=shallow(domain);JsonArray admitted=array(),selectedConversions=array();
    Set<String> seen=new HashSet<>(),selectors=new HashSet<>();
    for(JsonObject input:inputs){
      String key=CanonicalJson.digest(input);JsonElement member=members.get(key);
      if(member!=null&&seen.add(key))admitted.add(member);
      selectors.add(selector(input));
    }
    // Gene-container rules still retain one native root witness, without repeating every species.
    if(admitted.size()==0&&domain.getAsJsonArray("members").size()>0)admitted.add(domain.getAsJsonArray("members").get(0));
    // Keep every matching native conversion in original order: duplicate selectors carry priority.
    for(JsonElement conversion:domain.getAsJsonArray("conversions"))
      if(selectors.contains(selector(conversion.getAsJsonObject().getAsJsonObject("input"))))selectedConversions.add(conversion);
    result.add("members",admitted);result.add("conversions",selectedConversions);return result;
  }
  private static String selector(JsonObject stack){return CanonicalJson.digest(object("item",stack.get("item"),"meta",stack.get("meta")));}
  static JsonObject shallow(JsonObject source){JsonObject result=new JsonObject();for(Map.Entry<String,JsonElement> e:source.entrySet())result.add(e.getKey(),e.getValue());return result;}
}
