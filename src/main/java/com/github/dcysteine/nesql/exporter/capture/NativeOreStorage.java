package com.github.dcysteine.nesql.exporter.capture;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Exact Forge/Hodgepodge backing owners; never admit arbitrary Map/List callbacks.
 * Hodgepodge 2.6.112 rewrites nameToId and stackToId, not the indexed member tables. */
final class NativeOreStorage {
    private static final String NAMES="it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap";
    private static final String REVERSE="it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap";
    private static final String IDS="it.unimi.dsi.fastutil.ints.IntArrayList";
    private static final String VIEW="net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList";
    private NativeOreStorage() {}
    private static boolean exact(Object value,String owner){
        return value!=null&&value.getClass().getName().equals(owner)&&value.getClass()==MagicApi.type(owner);
    }
    static boolean names(Object value){
        return value!=null&&(value.getClass()==HashMap.class||exact(value,NAMES)
                &&Integer.valueOf(-1).equals(defaultValue(value)));
    }
    static boolean reverse(Object value){
        return value!=null&&(value.getClass()==HashMap.class||exact(value,REVERSE)&&defaultValue(value)==null);
    }
    static boolean indices(Object reverse,Object value){
        return value!=null&&(reverse.getClass()==HashMap.class?value.getClass()==ArrayList.class:optimizedList(value));
    }
    static boolean optimizedMap(Object value){return exact(value,NAMES)||exact(value,REVERSE);}
    static boolean optimizedList(Object value){return exact(value,IDS);}
    static boolean oreView(Object value){return exact(value,VIEW);}
    /** Forge's readonly list delegates to this exact mutable list. Callers must pin both. */
    static ArrayList<?> arrayList(Object value){
        if(oreView(value))value=MagicApi.field(value,"list");
        if(value==null||value.getClass()!=ArrayList.class)
            throw new Jobs.Fault("recipe_unsupported","Unadapted native ore member-list backing");
        return (ArrayList<?>)value;
    }
    static boolean arrayListOwner(Object value){return value!=null&&(value.getClass()==ArrayList.class||oreView(value));}
    static Object defaultValue(Object value){return MagicApi.field(value,"defRetValue");}

    /** Reproduce getOreIDs encounter order without invoking item/registry callbacks.
     * Hodgepodge uses primitive addAll and IntOpenHashSet order, which can change
     * the first matching Chisel group when exact and wildcard ores overlap. */
    @SuppressWarnings("unchecked")
    static Set<Integer> orderedIds(Map<?,?> reverse,Object wild,Object concrete){
        if(!reverse(reverse))throw new Jobs.Fault("recipe_unsupported","Unknown native ore reverse storage");
        int count=0;
        for(Object raw:new Object[]{wild,concrete})if(raw!=null){
            if(!indices(reverse,raw))throw new Jobs.Fault("recipe_unsupported","Unknown native ore index list");
            List<?> values=(List<?>)raw;
            if(values.size()>262144-count)throw new Jobs.Fault("source_budget","Native ore lookup exceeds budget");
            count+=values.size();
            for(Object value:values){Jobs.checkpoint();if(!(value instanceof Integer))throw new Jobs.Fault("recipe_unsupported","Invalid native ore index");}
        }
        if(reverse.getClass()==HashMap.class){
            Set<Integer> result=new HashSet<>();
            if(wild!=null)result.addAll((List<Integer>)wild);
            if(concrete!=null)result.addAll((List<Integer>)concrete);
            return result;
        }
        try{
            Set<Integer> result=(Set<Integer>)PrimitiveIds.OWNER.newInstance();
            if(wild!=null)PrimitiveIds.ADD_ALL.invoke(result,wild);
            if(concrete!=null)PrimitiveIds.ADD_ALL.invoke(result,concrete);
            return result;
        }catch(InvocationTargetException cause){
            Throwable target=cause.getCause();
            if(target instanceof Error)throw (Error)target;
            if(target instanceof RuntimeException)throw (RuntimeException)target;
            Jobs.Fault failure=new Jobs.Fault("recipe_unsupported","Pinned primitive ore ordering failed");failure.initCause(target);throw failure;
        }catch(ReflectiveOperationException cause){
            Jobs.Fault failure=new Jobs.Fault("recipe_unsupported","Pinned primitive ore ordering failed");failure.initCause(cause);throw failure;
        }
    }
    private static final class PrimitiveIds{
        static final Class<?> OWNER=MagicApi.type("it.unimi.dsi.fastutil.ints.IntOpenHashSet");
        static final Method ADD_ALL=addAll();
        private static Method addAll(){
            try{return OWNER.getMethod("addAll",MagicApi.type("it.unimi.dsi.fastutil.ints.IntCollection"));}
            catch(NoSuchMethodException cause){throw new ExceptionInInitializerError(cause);}
        }
    }
}
