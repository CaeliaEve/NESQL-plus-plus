package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;

/** Strict storage-only Scala traversal. Arbitrary iterables and default functions are not admitted. */
final class NativeScala {
  private NativeScala(){}
  static List<Object> values(Object source,int limit){
    if(source==null)throw new Jobs.Fault("handler_not_ready","Native Scala storage is unavailable");
    source=storage(source);
    String name=source.getClass().getName();
    if(source.getClass()!=type(name)||!Arrays.asList("scala.collection.immutable.$colon$colon","scala.collection.immutable.Nil$",
        "scala.collection.immutable.Vector","scala.collection.immutable.Range$Inclusive",
        "scala.collection.mutable.ArrayBuffer","scala.collection.mutable.WrappedArray$ofRef",
        "scala.collection.mutable.WrappedArray$ofInt","scala.collection.mutable.HashMap",
        "scala.collection.immutable.Map$EmptyMap$","scala.collection.immutable.Map$Map1",
        "scala.collection.immutable.Map$Map2","scala.collection.immutable.Map$Map3","scala.collection.immutable.Map$Map4",
        "scala.collection.immutable.HashMap$EmptyHashMap$","scala.collection.immutable.HashMap$HashMap1",
        "scala.collection.immutable.HashMap$HashTrieMap","scala.collection.immutable.HashMap$HashMapCollision1",
        "scala.collection.immutable.Set$EmptySet$","scala.collection.immutable.Set$Set1","scala.collection.immutable.Set$Set2",
        "scala.collection.immutable.Set$Set3","scala.collection.immutable.Set$Set4","scala.collection.immutable.HashSet$HashSet1",
        "scala.collection.immutable.HashSet$HashTrieSet","scala.collection.immutable.HashSet$HashSetCollision1",
        "scala.collection.immutable.TreeSet").contains(name))
      throw new Jobs.Fault("recipe_unsupported","Unadapted native Scala storage: "+name);
    int count=((Number)invoke(source.getClass(),source,"size",new Class<?>[0])).intValue();
    if(count<0||count>limit)throw new Jobs.Fault("recipe_limit","Native Scala source exceeds budget");
    Object iterator=invoke(source.getClass(),source,"iterator",new Class<?>[0]);List<Object> result=new ArrayList<>(count);
    while((Boolean)invoke(iterator.getClass(),iterator,"hasNext",new Class<?>[0])){
      Jobs.checkpoint();if(result.size()>=limit)throw new Jobs.Fault("recipe_limit","Native Scala traversal exceeds budget");
      result.add(invoke(iterator.getClass(),iterator,"next",new Class<?>[0]));
    }
    if(count!=result.size())throw new Jobs.Fault("environment_changed","Native Scala source changed during traversal");
    return result;
  }
  /** Never call Map.apply/default: a missing entry must remain missing. */
  static Object storage(Object source){
    Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    while(source!=null&&Arrays.asList("scala.collection.mutable.Map$WithDefault","scala.collection.immutable.Map$WithDefault").contains(source.getClass().getName())){
      if(source.getClass()!=type(source.getClass().getName())||!seen.add(source)||seen.size()>8)
        throw new Jobs.Fault("recipe_unsupported","Invalid native Scala default-map chain");
      source=field(source,"underlying");
    }
    if(source==null)throw new Jobs.Fault("handler_not_ready","Native Scala map backing is unavailable");
    return source;
  }
  static Object pair(Object value,int member){
    if(value==null||value.getClass()!=type("scala.Tuple2"))throw new Jobs.Fault("recipe_unsupported","Unadapted native Scala pair");
    return invoke(value.getClass(),value,"_"+member,new Class<?>[0]);
  }
}
