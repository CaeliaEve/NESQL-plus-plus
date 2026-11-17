package com.github.dcysteine.nesql.exporter.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Forestry observations. Hooks forward values the native renderer
 * already consumed; they never call genetics getters or resolve optional mods. */
public final class ForestryTrace {
    public static final String BEE_ITEM="forestry.apiculture.render.RenderBeeItem";
    public static final String BUTTERFLY_ITEM="forestry.lepidopterology.render.RenderButterflyItem";
    public static final String BEE_MODEL="forestry.apiculture.render.ModelBee";
    public static final String BUTTERFLY_MODEL="forestry.lepidopterology.render.ModelButterfly";
    public static final String BEE_ENTITY="forestry.apiculture.render.RenderBeeEntity";
    public static final String BUTTERFLY_ENTITY="forestry.lepidopterology.render.RenderButterflyEntity";
    public static final String BUTTERFLY_STATE="forestry.lepidopterology.entities.EnumButterflyState";
    private static final ThreadLocal<Scope> current=new ThreadLocal<>();
    private static final Set<String> installed=ConcurrentHashMap.newKeySet();
    public static boolean item(String type){return BEE_ITEM.equals(type)||BUTTERFLY_ITEM.equals(type);}
    public static boolean model(String type){return BEE_MODEL.equals(type)||BUTTERFLY_MODEL.equals(type);}
    public static boolean entity(String type){return BEE_ENTITY.equals(type)||BUTTERFLY_ENTITY.equals(type);}
    public static boolean reviewed(String type){return item(type)||model(type)||entity(type)||BUTTERFLY_STATE.equals(type);}
    static void installed(String type){installed.add(type);}
    public static boolean instrumented(String type){return installed.contains(type);}
    public interface Observer {
        default void individual(Object value){}
        default void alive(boolean value){}
        default void hash(int value){}
        default void partial(float value){}
        default void butterfly(Object state,Object entity,long hash,float partial){}
        default void flap(float value){}
        default void entity(Object renderer,Object entity){}
        default void texture(Object value){}
        default void model(Object model,Object entity,float limb,float amount,float swing,float yaw,float pitch,float scale){}
        default void part(Object value,float scale){}
        default void drawn(Object value){}
        default void end(Object model){}
    }
    private static Observer observer(){Scope scope=current.get();return scope==null?null:scope.observer;}
    public static void individual(Object value){Observer o=observer();if(o!=null)o.individual(value);}
    public static void alive(boolean value){Observer o=observer();if(o!=null)o.alive(value);}
    public static void hash(int value){Observer o=observer();if(o!=null)o.hash(value);}
    public static void partial(float value){Observer o=observer();if(o!=null)o.partial(value);}
    public static void butterfly(Object state,Object entity,long hash,float partial){Observer o=observer();if(o!=null)o.butterfly(state,entity,hash,partial);}
    public static void flap(float value){Observer o=observer();if(o!=null)o.flap(value);}
    public static void entity(Object renderer,Object entity){Observer o=observer();if(o!=null)o.entity(renderer,entity);}
    public static void texture(Object value){Observer o=observer();if(o!=null)o.texture(value);}
    public static void model(Object model,Object entity,float limb,float amount,float swing,float yaw,float pitch,float scale){
        Observer o=observer();if(o!=null)o.model(model,entity,limb,amount,swing,yaw,pitch,scale);
    }
    public static void part(Object value,float scale){Observer o=observer();if(o!=null)o.part(value,scale);}
    public static void drawn(Object value){Observer o=observer();if(o!=null)o.drawn(value);}
    public static void end(Object model){Observer o=observer();if(o!=null)o.end(model);}
    public static Scope observe(Observer observer){if(observer==null)throw new IllegalArgumentException("Forestry observer is required");Scope scope=new Scope(observer,current.get());current.set(scope);return scope;}
    public static final class Scope implements AutoCloseable {
        private final Observer observer;private final Scope previous;private final Thread thread=Thread.currentThread();private boolean closed;
        private Scope(Observer observer,Scope previous){this.observer=observer;this.previous=previous;}
        @Override public void close(){if(closed)return;if(thread!=Thread.currentThread()||current.get()!=this)throw new IllegalStateException("Forestry observer scope is not current");closed=true;if(previous==null)current.remove();else current.set(previous);}
    }
}
