package com.github.dcysteine.nesql.exporter.core;

/** Observe semantic Tessellator inputs, never an assumed raw vertex stride.
 * Inactive hooks have one cheap guard and perform no GL or optional-mod work. */
public final class InventoryDrawTrace {
    private static final ThreadLocal<Scope> current=new ThreadLocal<>();
    private static volatile int observers;
    private static volatile boolean installed;
    private InventoryDrawTrace(){ }
    public interface Observer {
        void begin(Object tessellator,int mode);
        void vertex(Object tessellator,double x,double y,double z);
        void draw(Object tessellator);
        default void gaussian(java.util.Random source,RendererRandom.Snapshot initial,long draw,double value){ }
    }
    public static void begin(Object tessellator,int mode){
        if(observers==0)return;Scope scope=current.get();if(scope!=null)scope.observer.begin(tessellator,mode);
    }
    public static void vertex(Object tessellator,double x,double y,double z){
        if(observers==0)return;Scope scope=current.get();if(scope!=null)scope.observer.vertex(tessellator,x,y,z);
    }
    public static void draw(Object tessellator){
        if(observers==0)return;Scope scope=current.get();if(scope!=null)scope.observer.draw(tessellator);
    }
    public static void gaussian(java.util.Random source,RendererRandom.Snapshot initial,long draw,double value){
        if(observers==0)return;Scope scope=current.get();if(scope!=null)scope.observer.gaussian(source,initial,draw,value);
    }
    public static boolean instrumented(){return installed;}
    static void installed(){installed=true;}
    public static Scope observe(Observer observer){
        if(observer==null)throw new IllegalArgumentException("Native draw observer is required");
        Scope scope=new Scope(observer,current.get());
        synchronized(InventoryDrawTrace.class){observers=Math.addExact(observers,1);}
        current.set(scope);return scope;
    }
    public static final class Scope implements AutoCloseable {
        private final Observer observer;private final Scope previous;private final Thread owner=Thread.currentThread();private boolean closed;
        private Scope(Observer observer,Scope previous){this.observer=observer;this.previous=previous;}
        @Override public void close(){
            if(closed)return;
            if(owner!=Thread.currentThread()||current.get()!=this)throw new IllegalStateException("Native draw scopes crossed a thread or closed out of order");
            closed=true;if(previous==null)current.remove();else current.set(previous);
            synchronized(InventoryDrawTrace.class){observers--;}
        }
    }
}
