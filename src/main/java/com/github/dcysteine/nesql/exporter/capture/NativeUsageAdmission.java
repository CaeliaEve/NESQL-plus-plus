package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.NativeUsageReferences.fault;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact pinned callback classes and operation roles; no interface/package-wide fallback. */
final class NativeUsageAdmission {
    private static final Map<String,JsonObject> CLASSES=load();
    private static final Map<Class<?>,String> HASHES=new IdentityHashMap<>();
    private static Map<String,JsonObject> load(){
        try(InputStream input=NativeUsageAdmission.class.getResourceAsStream("/native-usage-admission.json")){
            if(input==null)throw new IllegalStateException("Missing native usage admission inventory");
            JsonObject data=new JsonParser().parse(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,JsonObject> classes=new HashMap<>();
            for(JsonElement entry:data.getAsJsonArray("classes")){JsonObject row=entry.getAsJsonObject();if(classes.put(row.get("owner").getAsString(),row)!=null)throw new IllegalStateException("Duplicate native usage admission");}
            return Collections.unmodifiableMap(classes);
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    static void admit(Object object,String role){
        if(object==null)throw fault("Null native usage callback");
        Class<?> owner=object.getClass();JsonObject entry=CLASSES.get(owner.getName());
        boolean admitted=false;if(entry!=null)for(JsonElement allowed:entry.getAsJsonArray("roles"))if(role.equals(allowed.getAsString()))admitted=true;
        if(!admitted)throw fault("Unadapted native usage "+role+" callback: "+owner.getName());
        MagicApi.version("NativeUsageAdmission",entry.get("mod").getAsString());
        String hash;
        synchronized(HASHES){
            hash=HASHES.get(owner);
            if(hash==null){
                String resource="/"+owner.getName().replace('.','/')+".class";
                try(InputStream input=owner.getResourceAsStream(resource);ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    if(input==null)throw fault("Missing native usage callback bytecode: "+owner.getName());
                    byte[] buffer=new byte[8192];int size;
                    while((size=input.read(buffer))!=-1){if(output.size()+size>2*1024*1024)throw fault("Oversized native usage callback");output.write(buffer,0,size);}
                    hash=CanonicalJson.digest(output.toByteArray());HASHES.put(owner,hash);
                }catch(IOException failure){throw new IllegalStateException(failure);}
            }
        }
        if(!hash.equals(entry.get("sha256").getAsString()))throw fault("Native usage callback bytecode differs: "+owner.getName());
    }
}
