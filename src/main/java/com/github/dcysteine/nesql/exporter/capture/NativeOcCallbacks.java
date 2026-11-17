package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeCellQueries.call;
import static com.github.dcysteine.nesql.exporter.capture.NativeUsageReferences.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Native static Callback documentation. Does not construct environments or invoke a Callback. */
final class NativeOcCallbacks implements NativeUsageReferences.Source {
    private Object registry(){return NativeOcQueries.api("driver","li.cil.oc.server.driver.Registry$");}
    public Object[] roots(){return roots(true);}
    public Object[] registrationRoots(){return roots(false);}
    private Object[] roots(boolean admit){
        Object registry=registry();List<Object> roots=new ArrayList<>();roots.add(registry);
        if(!Boolean.TRUE.equals(field(registry,"locked")))throw new Jobs.Fault("handler_not_ready","OC driver registration is not complete");
        NativeOcQueries.itemRoots(roots);
        Object config=field(type("li.cil.oc.api.API"),null,"config");roots.add(config!=null);if(config!=null)roots.add(config);
        roots.add(field(registry,"locked"));
        for(String name:Arrays.asList("environmentProviders","items","blocks")){
            Object buffer=field(registry,name);NativeOcQueries.bind(roots,buffer);
            for(Object member:NativeOcQueries.sequence(buffer)){
                if(admit&&name.equals("environmentProviders"))NativeUsageAdmission.admit(member,"environment");
                if(admit&&name.equals("items"))NativeUsageAdmission.admit(member,"itemDriver");
            }
        }
        int count=NativeCellQueries.panelSize();roots.add(ItemList.items);roots.add(count);roots.add(ItemList.loadFinished);
        return roots.toArray();
    }
    public Data read(String locale){
        roots();Object registry=registry();
        List<Object> providers=NativeOcQueries.sequence(field(registry,"environmentProviders"));
        List<Object> items=NativeOcQueries.sequence(field(registry,"items"));
        List<Object> blocks=NativeOcQueries.sequence(field(registry,"blocks"));
        Map<String,JsonObject> documents=new TreeMap<>();
        Data data=new Data("ocCallbacks","1.11.20-GTNH");List<ItemStack> panel=NativeCellQueries.panel();data.scanned=panel.size();
        for(int index=0;index<panel.size();index++){
            Jobs.checkpoint();ItemStack input=Data.owned(panel.get(index));if(!data.query(input))continue;
            Set<Class<?>> environments=new LinkedHashSet<>();
            for(Object provider:providers){
                NativeUsageAdmission.admit(provider,"environment");
                Object result=call(provider,"getEnvironment",new Class<?>[]{ItemStack.class},input.copy());
                if(result!=null){if(!(result instanceof Class))throw fault("Invalid OC environment class");environments.add((Class<?>)result);}
            }
            List<Class<?>> selected=new ArrayList<>(environments);
            if(!hasCallbacks(selected,documents)){
                Object driver=null;
                for(Object candidate:items){
                    NativeUsageAdmission.admit(candidate,"itemDriver");
                    if(Boolean.TRUE.equals(call(candidate,"worksWith",new Class<?>[]{ItemStack.class},input.copy()))){driver=candidate;break;}
                }
                selected.clear();Class<?> aware=type("li.cil.oc.api.driver.EnvironmentAware");
                if(driver!=null&&aware.isInstance(driver))addEnvironment(selected,driver,input);
                else for(Object block:blocks)if(aware.isInstance(block)){
                    if(type("li.cil.oc.api.prefab.DriverTileEntity").isInstance(block)){
                        NativeUsageAdmission.admit(block,"tileDriver");Object tile=call(block,"getTileEntityClass");
                        if(tile==null||((Class<?>)tile).isInterface())continue;
                    }
                    addEnvironment(selected,block,input);
                }
            }
            JsonArray links=array();
            for(Class<?> environment:selected){
                JsonObject document=documents.computeIfAbsent(environment.getName(),key->document(environment));
                if(document.getAsJsonObject("content").getAsJsonArray("methods").size()>0)links.add(value(document.get("key").getAsString()));
            }
            if(links.size()==0)data.unmatched++;
            else data.observation(index,input,object("kind","callbacks","documents",links));
        }
        Set<String> used=new HashSet<>();for(JsonElement row:data.observations)for(JsonElement key:row.getAsJsonObject().getAsJsonObject("result").getAsJsonArray("documents"))used.add(key.getAsString());
        for(JsonObject document:documents.values())if(used.contains(document.get("key").getAsString()))data.append(data.documents,document);
        return data;
    }
    private static void addEnvironment(List<Class<?>> list,Object driver,ItemStack input){
        NativeUsageAdmission.admit(driver,"aware");Object value=call(driver,"providedEnvironment",new Class<?>[]{ItemStack.class},input.copy());
        if(value!=null){if(!(value instanceof Class))throw fault("Invalid OC legacy environment class");list.add((Class<?>)value);}
    }
    private static boolean hasCallbacks(List<Class<?>> classes,Map<String,JsonObject> documents){
        boolean result=false;
        for(Class<?> type:classes)if(documents.computeIfAbsent(type.getName(),key->document(type)).getAsJsonObject("content").getAsJsonArray("methods").size()>0)result=true;
        return result;
    }
    @SuppressWarnings("unchecked")
    static JsonObject document(Class<?> environment){
        Class<? extends Annotation> annotation=(Class<? extends Annotation>)type("li.cil.oc.api.machine.Callback");
        Map<String,JsonObject> methods=new TreeMap<>();int ignored=0;
        for(Class<?> owner=environment;owner!=null&&owner!=Object.class;owner=owner.getSuperclass())for(Method method:owner.getDeclaredMethods()){
            Jobs.checkpoint();if(!method.isAnnotationPresent(annotation))continue;
            Class<?>[] parameters=method.getParameterTypes();
            if(!Modifier.isPublic(method.getModifiers())||method.getReturnType()!=Object[].class||parameters.length!=2
                    ||parameters[0]!=type("li.cil.oc.api.machine.Context")||parameters[1]!=type("li.cil.oc.api.machine.Arguments")){ignored++;continue;}
            Object nativeAnnotation=method.getAnnotation(annotation);String named=(String)call(nativeAnnotation,"value");
            String name=named==null||named.trim().isEmpty()?method.getName():named;
            String doc=(String)call(nativeAnnotation,"doc");if(doc==null)doc="";
            if(name.length()>1024||doc.length()>65536||methods.size()>=65536)throw fault("OC callback documentation exceeds budget");
            methods.put(name,object("name",name,"declaringClass",owner.getName(),"javaName",method.getName(),"doc",doc,
                "direct",call(nativeAnnotation,"direct"),"limit",call(nativeAnnotation,"limit"),
                "getter",call(nativeAnnotation,"getter"),"setter",call(nativeAnnotation,"setter")));
        }
        JsonArray entries=array();for(JsonObject method:methods.values())entries.add(method);
        String key=Identity.content("usage_doc",object("backend","ocCallbacks","environment",environment.getName()));
        return object("kind","usageDocument","key",key,"content",object("kind","callbacks","environment",environment.getName(),
            "analysis","nativeStaticAnnotations","ignoredAnnotations",ignored,"methods",entries));
    }
}
