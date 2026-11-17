package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Raw target-JAR and local remapped resource guards for the audited container closures. */
final class SqueezerCallbackClasses {
    private final Map<String,Set<String>> expected=new HashMap<>();
    private final Map<ClassLoader,Map<String,Boolean>> checked=new IdentityHashMap<>();
    SqueezerCallbackClasses(){
        try(InputStream in=SqueezerCallbackClasses.class.getResourceAsStream("/squeezer-callback-classes.json")){
            if(in==null)throw new IOException("Missing squeezer callback source guards");
            JsonObject rows=new JsonParser().parse(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for(Map.Entry<String,JsonElement> row:rows.entrySet()){
                Set<String> hashes=new HashSet<>();for(JsonElement hash:row.getValue().getAsJsonArray())hashes.add(hash.getAsString());
                expected.put(row.getKey(),hashes);
            }
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot read squeezer callback source guards",failure);}
    }
    boolean pinned(ClassLoader loader,String... owners){
        if(loader==null)return false;
        Map<String,Boolean> byOwner=checked.get(loader);
        if(byOwner==null){byOwner=new HashMap<>();checked.put(loader,byOwner);}
        for(String owner:owners){
            Boolean valid=byOwner.get(owner);
            if(valid==null){valid=read(loader,owner);byOwner.put(owner,valid);}
            if(!valid)return false;
        }
        return true;
    }
    private boolean read(ClassLoader loader,String owner){
        Set<String> accepted=expected.get(owner);if(accepted==null)return false;
        try(InputStream in=loader.getResourceAsStream(owner.replace('.','/')+".class")){
            if(in==null)return false;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int total=0,count;
            while((count=in.read(buffer))!=-1){total+=count;if(total>2*1024*1024)return false;digest.update(buffer,0,count);}
            StringBuilder hash=new StringBuilder(64);for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));
            return accepted.contains(hash.toString());
        }catch(java.security.NoSuchAlgorithmException|IOException failure){return false;}
    }
}
