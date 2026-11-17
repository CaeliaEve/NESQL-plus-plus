package com.github.dcysteine.nesql.exporter.core;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/** Detaches reviewed inventory randomness. Ordinary renders still invoke their
 * original Random; capture scopes advance an owned JDK copy, including its
 * cached Gaussian. No RNG or clock is modified in the game. */
public final class RendererRandom {
    private static final long MASK = (1L << 48) - 1;
    private static final ThreadLocal<Session> current = new ThreadLocal<>();
    private static final ThreadLocal<Scope> activeScope = new ThreadLocal<>();
    private RendererRandom() { }

    public static double gaussian(Random nativeRandom) {
        Session session = current.get();
        return session == null ? nativeRandom.nextGaussian() : session.next(nativeRandom);
    }

    /** An in-process capability, not a deserializable claim about a Random.
     * It binds the original native object/seed authority and its client thread. */
    public static final class State {
        private final Thread owner;
        private final List<Position> positions;
        private State(List<Position> positions){this.owner=Thread.currentThread();this.positions=Collections.unmodifiableList(positions);}
        public List<Position> positions(){return positions;}
    }
    public static final class Position {
        public final Snapshot initial,current;
        public final long draws;
        private final Random source;
        private final AtomicLong seedRoot;
        private Position(Random source,Entry entry){
            this.source=source;this.seedRoot=entry.seedRoot;this.initial=entry.start;this.draws=entry.draws;
            this.current=read(entry.detached,initial.stream);
        }
    }
    public interface Validator {void verify(Random nativeSource,Snapshot initial);}

    public static final class Snapshot {
        public final int stream;
        public final String seed, gaussian;
        public final boolean haveGaussian;
        private Snapshot(int stream, String seed, boolean haveGaussian, String gaussian) {
            this.stream = stream; this.seed = seed; this.haveGaussian = haveGaussian; this.gaussian = gaussian;
        }
        public static Snapshot from(int stream, String seed, boolean haveGaussian, String gaussian) {
            if (stream < 1 || stream > 4096 || seed == null || !seed.matches("0|[1-9][0-9]{0,14}")
                    || gaussian == null || !gaussian.matches("[0-9a-f]{16}")) throw new IllegalArgumentException("Invalid native Random state");
            long rawSeed = Long.parseLong(seed);
            double cached = Double.longBitsToDouble(Long.parseUnsignedLong(gaussian, 16));
            if (rawSeed > MASK || !Double.isFinite(cached)) throw new IllegalArgumentException("Invalid native Random state");
            return new Snapshot(stream, seed, haveGaussian, gaussian);
        }
        public Random copy() {
            Random result = new Random(0L);
            try {
                ((AtomicLong) Fields.SEED.get(result)).set(Long.parseLong(seed));
                Fields.HAVE.setBoolean(result, haveGaussian);
                Fields.GAUSSIAN.setDouble(result, Double.longBitsToDouble(Long.parseUnsignedLong(gaussian, 16)));
                return result;
            } catch (IllegalAccessException failure) { throw unavailable(failure); }
        }
    }

    public static final class Session implements AutoCloseable {
        private final IdentityHashMap<Random, Entry> streams = new IdentityHashMap<>();
        private Thread owner;
        private int scopes;
        private boolean closed;
        public Scope scope() {
            check();Scope scope=new Scope(this,current.get(),activeScope.get());current.set(this);activeScope.set(scope);scopes++;
            return scope;
        }
        public Snapshot snapshot(Random nativeRandom) { return entry(nativeRandom).start; }
        public long draws(Random nativeRandom) { return entry(nativeRandom).draws; }
        public State save(){
            check();if(scopes!=0)throw new IllegalStateException("Random state cannot seal during a native draw");
            List<Position> positions=new ArrayList<>();
            for(java.util.Map.Entry<Random,Entry> stream:streams.entrySet()){
                verifyRoot(stream.getKey(),stream.getValue().seedRoot);positions.add(new Position(stream.getKey(),stream.getValue()));
            }
            positions.sort(java.util.Comparator.comparingInt(position->position.initial.stream));return new State(positions);
        }
        /** The caller must validate the real renderer-to-Random binding. A JSON
         * record or matching seed is never sufficient authority to restore. */
        public void restore(State state,Validator validator){
            java.util.Objects.requireNonNull(validator,"Native renderer binding validation is required");
            if(state==null||state.owner!=Thread.currentThread())throw new IllegalStateException("Random state crossed its native client thread");
            check();if(scopes!=0||!streams.isEmpty())throw new IllegalStateException("Random restore requires a fresh idle capture session");
            IdentityHashMap<Random,Entry> staged=new IdentityHashMap<>();int ordinal=0;
            for(Position position:state.positions){
                if(position.initial.stream!=++ordinal||position.current.stream!=ordinal||position.draws<0||staged.containsKey(position.source))
                    throw new IllegalStateException("Invalid sealed Random stream order");
                verifyRoot(position.source,position.seedRoot);validator.verify(position.source,position.initial);verifyRoot(position.source,position.seedRoot);
                Entry entry=new Entry(position.initial,position.seedRoot,position.current,position.draws);staged.put(position.source,entry);
            }
            streams.putAll(staged);
        }
        private double next(Random nativeRandom) {
            Entry stream = entry(nativeRandom);
            stream.draws = Math.addExact(stream.draws, 1);
            double value=stream.detached.nextGaussian();
            InventoryDrawTrace.gaussian(nativeRandom,stream.start,stream.draws,value);
            return value;
        }
        private Entry entry(Random nativeRandom) {
            check();
            if (nativeRandom == null || nativeRandom.getClass() != Random.class)
                throw new IllegalArgumentException("Unreviewed inventory Random implementation");
            Entry stream = streams.get(nativeRandom);
            try {
                Object liveSeed = Fields.SEED.get(nativeRandom);
                if (liveSeed == null || liveSeed.getClass() != AtomicLong.class)
                    throw new IllegalStateException("Native Random seed authority changed");
                if (stream == null) {
                    if (streams.size() >= 4096) throw new IllegalStateException("Inventory Random stream budget exceeded");
                    synchronized (nativeRandom) {
                        AtomicLong root = (AtomicLong) liveSeed; long seed = root.get();
                        boolean have = Fields.HAVE.getBoolean(nativeRandom);
                        double cached = Fields.GAUSSIAN.getDouble(nativeRandom);
                        if (root.get() != seed || Fields.SEED.get(nativeRandom) != root)
                            throw new IllegalStateException("Native Random changed while detaching");
                        String raw = Long.toHexString(Double.doubleToRawLongBits(cached));
                        Snapshot state = Snapshot.from(streams.size() + 1, Long.toString(seed), have,
                                "0000000000000000".substring(raw.length()) + raw);
                        stream = new Entry(state, root); streams.put(nativeRandom, stream);
                    }
                } else if (stream.seedRoot != liveSeed) {
                    throw new IllegalStateException("Native Random seed authority changed");
                }
                return stream;
            } catch (IllegalAccessException failure) { throw unavailable(failure); }
        }
        private void check() {
            if (closed) throw new IllegalStateException("Inventory Random session is closed");
            if (owner == null) owner = Thread.currentThread();
            if (owner != Thread.currentThread()) throw new IllegalStateException("Inventory Random session crossed its client thread");
        }
        @Override public void close() {
            if (closed) return;
            check();
            if (scopes != 0) throw new IllegalStateException("Inventory Random scope is still active");
            closed = true; streams.clear();
        }
    }
    public static final class Scope implements AutoCloseable {
        private final Session installed, previous;
        private final Scope previousScope;
        private boolean closed;
        private Scope(Session installed, Session previous,Scope previousScope) { this.installed = installed; this.previous = previous;this.previousScope=previousScope; }
        @Override public void close() {
            if (closed) return;
            installed.check();
            if (current.get() != installed||activeScope.get()!=this) throw new IllegalStateException("Inventory Random scopes closed out of order");
            closed = true; installed.scopes--;
            if (previous == null) current.remove(); else current.set(previous);
            if(previousScope==null)activeScope.remove();else activeScope.set(previousScope);
        }
    }
    private static final class Entry {
        final Snapshot start;
        final Random detached;
        final AtomicLong seedRoot;
        long draws;
        Entry(Snapshot start, AtomicLong seedRoot) { this(start,seedRoot,start,0); }
        Entry(Snapshot start,AtomicLong seedRoot,Snapshot position,long draws){this.start=start;this.seedRoot=seedRoot;this.detached=position.copy();this.draws=draws;}
    }
    private static Snapshot read(Random source,int stream){
        try{synchronized(source){
            AtomicLong seed=(AtomicLong)Fields.SEED.get(source);long value=seed.get();boolean have=Fields.HAVE.getBoolean(source);
            String raw=Long.toHexString(Double.doubleToRawLongBits(Fields.GAUSSIAN.getDouble(source)));
            if(seed.get()!=value||Fields.SEED.get(source)!=seed)throw new IllegalStateException("Owned Random changed while sealing");
            return Snapshot.from(stream,Long.toString(value),have,"0000000000000000".substring(raw.length())+raw);
        }}catch(IllegalAccessException error){throw unavailable(error);}
    }
    private static void verifyRoot(Random source,AtomicLong root){
        if(source==null||source.getClass()!=Random.class)throw new IllegalStateException("Native Random authority changed");
        try{if(Fields.SEED.get(source)!=root)throw new IllegalStateException("Native Random seed authority changed");}
        catch(IllegalAccessException error){throw unavailable(error);}
    }
    /** Resolve private JDK fields only inside a capture operation. */
    private static final class Fields {
        static final Field SEED = field("seed"), HAVE = field("haveNextNextGaussian"), GAUSSIAN = field("nextNextGaussian");
        private static Field field(String name) {
            try { Field field = Random.class.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (ReflectiveOperationException failure) { throw unavailable(failure); }
        }
    }
    private static IllegalStateException unavailable(Exception failure) {
        return new IllegalStateException("Reviewed Java 8 Random state is unavailable", failure);
    }
}
