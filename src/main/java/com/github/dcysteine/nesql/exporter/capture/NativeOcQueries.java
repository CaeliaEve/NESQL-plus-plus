package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeCellQueries.call;
import static com.github.dcysteine.nesql.exporter.capture.NativeUsageReferences.*;
import java.util.*;

/** The two OC usage views read documented source data, never their GUI handlers. */
final class NativeOcQueries implements NativeUsageReferences.Source {
    private final boolean manual;
    NativeOcQueries(String handler){
        if(!MANUAL.equals(handler)&&!CALLBACKS.equals(handler))throw fault("Unknown OpenComputers usage query");
        MagicApi.version("NativeOcQueries","OpenComputers");manual=MANUAL.equals(handler);
    }
    public Object[] roots(){return manual?new NativeOcManual().roots():new NativeOcCallbacks().roots();}
    public Object[] registrationRoots(){return manual?new NativeOcManual().registrationRoots():new NativeOcCallbacks().registrationRoots();}
    public Data read(String locale){return manual?new NativeOcManual().read(locale):new NativeOcCallbacks().read(locale);}
    static List<Object> sequence(Object value){
        if(value==null||!value.getClass().getName().equals("scala.collection.mutable.ArrayBuffer"))throw fault("Unadapted OC registry sequence");
        int count=((Number)call(value,"length")).intValue();if(count<0||count>262144)throw fault("OC registry sequence exceeds budget");
        List<Object> result=new ArrayList<>(count);
        for(int i=0;i<count;i++)result.add(call(value,"apply",new Class<?>[]{int.class},i));return result;
    }
    static void bind(List<Object> roots,Object buffer){roots.add(buffer);List<Object> members=sequence(buffer);roots.add(members.size());roots.addAll(members);}
    static void itemRoots(List<Object> roots){
        Object items=api("items","li.cil.oc.common.init.Items$");roots.add(items);
        for(String name:Arrays.asList("descriptors","names","aliases","registeredItems"))roots.add(field(items,name));
    }
    static Object api(String name,String expected){return NativeCellQueries.exact(field(type("li.cil.oc.api.API"),null,name),expected);}
}
