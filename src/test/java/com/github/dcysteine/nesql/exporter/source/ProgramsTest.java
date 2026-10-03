package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

final class ProgramsTest {
    static void run() {
        JsonArray ordinary = new JsonArray();
        for (int i=0;i<257;i++) ordinary.add(object("time",i));
        JsonObject rules=object("ordinary",ordinary,"containers",new JsonArray(),"filled",new JsonArray(),"dynamic",new JsonArray());
        List<JsonObject> records=new ArrayList<>();
        String id=Programs.squeezer(rules,records::add);
        require(records.size()==6,"Shared rules were not divided into bounded source rows");
        require(id.equals(Identity.content("program",object("kind","forestrySqueezer","rules",rules))),"Program identity lost its whole-context content");
        JsonArray restored=new JsonArray();int offset=0;
        for(JsonObject record:records){
            require(record.get("program").getAsString().equals(id),"Chunks do not share an identity");
            require(record.get("id").getAsString().equals(id+"."+Identity.content("chunk",record)),"Chunk hash omits content or program key range");
            JsonObject data=record.getAsJsonObject("data");
            if(data.get("kind").getAsString().equals("squeezerRecipes")){
                require(record.get("offset").getAsInt()==offset,"Chunk order lost native priority");
                for(JsonElement value:data.getAsJsonArray("rows")){restored.add(value);offset++;}
            }
        }
        require(restored.equals(ordinary),"Chunking changed ordered rules");
        ordinary.get(0).getAsJsonObject().addProperty("time",-1);
        require(restored.get(0).getAsJsonObject().get("time").getAsInt()==0,"Chunk retained mutable source objects");
        rules.addProperty("unknown",true);
        try{Programs.squeezer(rules,row->{});throw new AssertionError("Unknown program section accepted");}
        catch(IllegalArgumentException expected){}
        char[] large=new char[65500];Arrays.fill(large,'x');JsonArray callbacks=new JsonArray();
        for(int i=0;i<32;i++)callbacks.add(new JsonPrimitive(new String(large)));
        List<JsonObject> bounded=new ArrayList<>();
        Programs.squeezer(object("ordinary",array(),"containers",array(),"filled",array(),"dynamic",callbacks),bounded::add);
        int restoredCallbacks=0;
        for(JsonObject row:bounded){
            require(CanonicalJson.bytes(row).length<=Dataset.RECORD_LIMIT,"Byte-heavy rules exceeded the source record limit");
            if(row.getAsJsonObject("data").get("kind").getAsString().equals("squeezerCallbacks"))restoredCallbacks+=row.getAsJsonObject("data").getAsJsonArray("rows").size();
        }
        require(restoredCallbacks==32,"Byte-based partitioning dropped rules");
        System.out.println("Shared machine rules: bounded chunks, native order, whole-context hash and owned records passed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
