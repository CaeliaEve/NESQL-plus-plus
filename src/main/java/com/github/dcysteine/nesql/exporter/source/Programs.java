package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Content-addressed shared machine rules, split within the source row budget. */
public final class Programs {
    private Programs() {}
    public static String squeezer(JsonObject rules, Consumer<JsonObject> sink) {
        String[] fields={"ordinary","containers","filled","dynamic"};
        String[] kinds={"squeezerRecipes","squeezerContainers","squeezerFluids","squeezerCallbacks"};
        Set<String> keys=new HashSet<>();for(Map.Entry<String,JsonElement> entry:rules.entrySet())keys.add(entry.getKey());
        if(!keys.equals(new HashSet<>(Arrays.asList(fields))))throw new IllegalArgumentException("Unknown or missing program section");
        for(String field:fields)if(!rules.get(field).isJsonArray())throw new IllegalArgumentException("Program section must be an array: "+field);
        byte[] encoded=CanonicalJson.bytes(rules);
        if(encoded.length>16*1024*1024)throw new IllegalArgumentException("Shared machine rules exceed 16 MiB");
        JsonObject owned=new JsonParser().parse(new String(encoded,StandardCharsets.UTF_8)).getAsJsonObject();
        String id=Identity.content("program",object("kind","forestrySqueezer","rules",owned));
        for(int field=0;field<fields.length;field++){
            JsonArray section=owned.getAsJsonArray(fields[field]),rows=new JsonArray();int offset=0,bytes=0;
            for(JsonElement value:section){
                Jobs.checkpoint();int length=CanonicalJson.bytes(value).length+1;
                if(length>900000)throw new IllegalArgumentException("One machine rule exceeds its source row budget");
                if(rows.size()==128||bytes+length>900000){emit(id,kinds[field],offset,rows,sink);offset+=rows.size();rows=new JsonArray();bytes=0;}
                rows.add(value);bytes+=length;
            }
            // An empty section is explicit, so lost chunks cannot masquerade as no rules.
            if(rows.size()>0||section.size()==0)emit(id,kinds[field],offset,rows,sink);
        }
        return id;
    }
    private static void emit(String program,String kind,int offset,JsonArray rows,Consumer<JsonObject> sink){
        JsonObject row=object("program",program,"offset",offset,"data",object("kind",kind,"rows",rows));
        row.addProperty("id",program+"."+Identity.content("chunk",row));sink.accept(row);
    }
}
