package com.github.dcysteine.nesql.exporter.capture;

import java.util.*;

/** Persistent source capabilities. Seals share immutable links instead of
 * copying every earlier texture/model proof for every completed Handler.
 * Only the new suffix is verified at a seal; final publication and recovery
 * walk the whole prefix. No source/validator is reconstructed from JSON. */
final class NativeAuthorities implements AutoCloseable {
    private static final ThreadLocal<Scope> current=new ThreadLocal<>();
    interface Proof {void check();}
    private final IdentityHashMap<Object,Map<String,Entry>> seen=new IdentityHashMap<>();
    private Entry head,sealed;private int size,scopes;private Thread thread;private boolean closed;
    Scope observe(){check();Scope scope=new Scope(this,current.get());current.set(scope);scopes++;return scope;}
    static NativeAuthorities current(){Scope scope=current.get();if(scope==null)throw new IllegalStateException("Native program sources require an active capture context");scope.owner.check();return scope.owner;}
    boolean contains(Object owner,String role){check();Map<String,Entry> roles=seen.get(owner);return roles!=null&&roles.containsKey(role);}
    void remember(Object owner,String role,Proof proof){
        check();if(owner==null||role==null||!role.matches("[a-zA-Z][a-zA-Z0-9]{0,63}")||proof==null)throw new IllegalArgumentException("Invalid native source authority");
        Map<String,Entry> roles=seen.computeIfAbsent(owner,key->new HashMap<>());if(roles.containsKey(role))return;
        if(size>=100000)throw new IllegalStateException("Native visual source budget exceeded");
        proof.check();Entry entry=new Entry(owner,role,proof,head);head=entry;roles.put(role,entry);size++;
    }
    State seal(){
        check();if(scopes!=0)throw new IllegalStateException("Native sources cannot seal during a renderer call");
        for(Entry entry=head;entry!=sealed;entry=entry.previous){if(entry==null)throw new IllegalStateException("Native seal boundary is no longer reachable");entry.proof.check();}
        sealed=head;return new State(thread,head,size);
    }
    void restore(Verifier verified){
        check();if(scopes!=0||size!=0||head!=null||!seen.isEmpty()||verified==null||!verified.complete||verified.state.thread!=thread)
            throw new IllegalStateException("Native source restore requires a verified prefix and an empty client context");
        IdentityHashMap<Object,Map<String,Entry>> staged=new IdentityHashMap<>();
        for(Entry entry=verified.state.head;entry!=null;entry=entry.previous){
            if(staged.computeIfAbsent(entry.owner,key->new HashMap<>()).put(entry.role,entry)!=null)throw new IllegalStateException("Duplicate native source authority");
        }
        seen.putAll(staged);head=sealed=verified.state.head;size=verified.state.size;
    }
    static final class State {
        private final Thread thread;private final Entry head;private final int size;
        private State(Thread thread,Entry head,int size){this.thread=thread;this.head=head;this.size=size;}
        int size(){return size;}
        Verifier verifier(){return new Verifier(this);}
        void verifyRandom(java.util.Random source){
            if(Thread.currentThread()!=thread)throw new IllegalStateException("Native Random binding crossed its client thread");
            for(Entry entry=head;entry!=null;entry=entry.previous)if(entry.owner==source&&entry.role.equals("random")){entry.proof.check();return;}
            throw new IllegalStateException("Native Random has no renderer binding in the sealed prefix");
        }
    }
    static final class Verifier {
        private final State state;private Entry next;private int remaining;private boolean complete;
        private Verifier(State state){this.state=state;this.next=state.head;this.remaining=state.size;}
        /** A bounded number of native roots per client handoff. Large individual
         * image/geometry checks retain their own existing resource bounds. */
        boolean advance(){
            if(Thread.currentThread()!=state.thread)throw new IllegalStateException("Native source proof crossed its client thread");
            if(complete)return true;long deadline=System.nanoTime()+4_000_000L;int roots=0;
            while(next!=null){next.proof.check();next=next.previous;remaining--;
                if(++roots>=32||System.nanoTime()>=deadline)return false;
            }
            if(remaining!=0)throw new IllegalStateException("Native source proof cardinality changed");complete=true;return true;
        }
    }
    private static final class Entry {
        final Object owner;final String role;final Proof proof;final Entry previous;
        Entry(Object owner,String role,Proof proof,Entry previous){this.owner=owner;this.role=role;this.proof=proof;this.previous=previous;}
    }
    final class Scope implements AutoCloseable {
        private final NativeAuthorities owner;private final Scope previous;private final Entry checkpointHead;private final int checkpointSize;private boolean finished;
        private Scope(NativeAuthorities owner,Scope previous){this.owner=owner;this.previous=previous;this.checkpointHead=owner.head;this.checkpointSize=owner.size;}
        /** Remove authorities observed in this renderer call before falling back
         * to a flat NEI frame. The outer capture keeps earlier verified roots. */
        void rollback(){
            owner.check();
            if(finished||current.get()!=this)throw new IllegalStateException("Native source scope cannot roll back");
            while(owner.head!=checkpointHead){
                Entry entry=owner.head;
                Map<String,Entry> roles=owner.seen.get(entry.owner);
                if(roles!=null&&roles.get(entry.role)==entry){roles.remove(entry.role);if(roles.isEmpty())owner.seen.remove(entry.owner);}
                owner.head=entry.previous;owner.size--;
            }
            if(owner.size!=checkpointSize)throw new IllegalStateException("Native source rollback cardinality changed");
        }
        @Override public void close(){if(finished)return;owner.check();if(current.get()!=this)throw new IllegalStateException("Native source scopes closed out of order");finished=true;owner.scopes--;if(previous==null)current.remove();else current.set(previous);}
    }
    private void check(){if(closed)throw new IllegalStateException("Native visual context is closed");if(thread==null)thread=Thread.currentThread();if(thread!=Thread.currentThread())throw new IllegalStateException("Native visual context crossed its client thread");}
    @Override public void close(){if(closed)return;check();if(scopes!=0)throw new IllegalStateException("Native source scope is still active");closed=true;seen.clear();head=sealed=null;size=0;}
}
