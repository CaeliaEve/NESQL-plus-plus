package com.github.dcysteine.nesql.exporter.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Bounded process-local capabilities for exact, released checkpoint chains.
 * State objects are supplied by the native producer, never deserialized from
 * disk. Every unit retains its own boundary state, including during replay of
 * an older chain which might itself stop before reaching its terminal unit. */
public final class Continuations<S> implements AutoCloseable {
    private final LinkedHashMap<Path,Chain<S>> chains=new LinkedHashMap<>();
    private final int retained;private boolean closed;
    public Continuations(int retained){if(retained<1||retained>16)throw new IllegalArgumentException("Invalid native continuation budget");this.retained=retained;}
    public synchronized void begin(Path path)throws IOException{
        open();Path key=key(path);if(chains.containsKey(key))throw new IOException("Native continuation attempt already exists");
        while(chains.size()>=retained){Iterator<Map.Entry<Path,Chain<S>>> entries=chains.entrySet().iterator();boolean removed=false;
            while(entries.hasNext()){if(entries.next().getValue().released){entries.remove();removed=true;break;}}
            if(!removed)throw new IOException("Native continuation budget is occupied by active attempts");
        }
        chains.put(key,new Chain<>());
    }
    public synchronized void save(Path path,String hash,int units,S state)throws IOException{
        open();Chain<S> chain=chains.get(key(path));
        if(chain==null||chain.released||units!=chain.states.size()+1||units>513||!digest(hash)||state==null)
            throw new IOException("Native continuation does not match its next sealed unit");
        chain.states.add(state);chain.latest=hash;
    }
    public synchronized void release(Path path,boolean clean)throws IOException{
        open();Chain<S> chain=chains.get(key(path));if(chain==null)throw new IOException("Native continuation attempt is missing");
        if(chain.released){if(chain.clean!=clean)throw new IOException("Native continuation cleanup result changed");return;}
        chain.released=true;chain.clean=clean;if(!clean)chain.states.clear();
    }
    public synchronized Lease<S> acquire(Path path,String hash,int units)throws IOException{
        open();Chain<S> chain=chains.get(key(path));
        if(chain==null||!chain.released||!chain.clean||!digest(hash)||!hash.equals(chain.latest)||chain.states.size()!=units||units<1)
            throw new IOException("No process-local authority for this released native checkpoint prefix");
        return new Lease<>(new ArrayList<>(chain.states));
    }
    public static final class Lease<S> {
        private final List<S> states;
        private Lease(List<S> states){this.states=Collections.unmodifiableList(states);}
        public S state(int unit)throws IOException{if(unit<0||unit>=states.size())throw new IOException("Native continuation unit is missing");return states.get(unit);}
        public S terminal(){return states.get(states.size()-1);}
        public int size(){return states.size();}
    }
    private static final class Chain<S> {final List<S> states=new ArrayList<>();String latest;boolean released,clean;}
    private static Path key(Path path){return Objects.requireNonNull(path,"Checkpoint path is required").toAbsolutePath().normalize();}
    private static boolean digest(String hash){return hash!=null&&hash.matches("[a-f0-9]{64}");}
    private void open()throws IOException{if(closed)throw new IOException("Native continuation store is closed");}
    @Override public synchronized void close(){closed=true;chains.clear();}
}
