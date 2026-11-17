package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Bounded, content-addressed Database facts. Each leaf has an independently checked source row. */
public final class GeneticsReferences {
    public static final String DESCRIPTION="genetics.nei.tip.databaseDesc";
    private GeneticsReferences() {}
    public static List<JsonObject> rows(JsonObject nativeReference) {
        if(!nativeReference.get("kind").getAsString().equals("binnie_genetics_reference")||nativeReference.get("consuming").getAsBoolean())
            throw new IllegalArgumentException("Database reference must be native and read-only");
        JsonArray catalog=nativeReference.getAsJsonArray("catalog"),data=array();int chromosomes=0,alleles=0,genes=0;
        for(JsonElement value:catalog){JsonObject root=value.getAsJsonObject();chromosomes+=root.getAsJsonArray("chromosomes").size();genes+=root.getAsJsonArray("genes").size();
            for(JsonElement chromosome:root.getAsJsonArray("chromosomes"))alleles+=chromosome.getAsJsonObject().getAsJsonArray("alleles").size();}
        data.add(object("kind","definition","source",clone(nativeReference.get("source")),"explanation",object("key",DESCRIPTION,"text",description()),"containers",clone(nativeReference.get("containers")),
            "tracker",clone(nativeReference.get("tracker")),"discovery",clone(nativeReference.get("discovery")),"consuming",false,
            "counts",object("roots",catalog.size(),"chromosomes",chromosomes,"alleles",alleles,"genes",genes,"containers",2)));
        for(JsonElement value:catalog){JsonObject root=value.getAsJsonObject(),resources=root.getAsJsonObject("resources");String uid=root.get("root").getAsString();
            data.add(object("kind","root","definition",object("root",uid,"rootClass",clone(root.get("rootClass")),"systemClass",clone(root.get("systemClass")),
                "memberTypes",clone(root.get("memberTypes")),"serum",clone(resources.get("serum")),"genome",clone(resources.get("genome")),"sequence",clone(resources.get("sequence")))));
            for(JsonElement element:root.getAsJsonArray("chromosomes")){JsonObject chromosome=element.getAsJsonObject(),descriptor=clone(chromosome).getAsJsonObject();descriptor.remove("alleles");
                data.add(object("kind","chromosome","root",uid,"chromosome",descriptor));
                for(JsonElement allele:chromosome.getAsJsonArray("alleles"))data.add(object("kind","allele","root",uid,"chromosome",chromosome.get("ordinal").getAsInt(),"allele",clone(allele)));
            }
            for(JsonElement gene:root.getAsJsonArray("genes"))data.add(object("kind","gene","gene",clone(gene)));
        }
        String domain=Identity.content("reference",object("kind","binnieGenetics","records",data));List<JsonObject> rows=new ArrayList<>();int order=0;
        for(JsonElement value:data){JsonObject row=object("domain",domain,"order",order++,"data",clone(value));row.addProperty("id",Identity.content("refrow",row));
            if(CanonicalJson.bytes(row).length>Dataset.RECORD_LIMIT)throw new IllegalArgumentException("Native Database leaf exceeds 1 MiB source row budget");rows.add(row);}
        return Collections.unmodifiableList(rows);
    }
    private static JsonElement clone(JsonElement value){return new JsonParser().parse(value.toString());}
    public static String description(){return net.minecraft.util.StatCollector.translateToLocalFormatted(DESCRIPTION,new Object[0]);}
}
