package com.github.dcysteine.nesql.exporter.capture;

/** One source ownership chain, not one test per renderer or Handler. */
final class NativeAuthoritiesTest {
    static void run() throws Exception {
        int[] first={7},later={11};Object owner=new Object(),other=new Object();
        NativeAuthorities original=new NativeAuthorities();
        original.remember(owner,"source",()->{if(first[0]!=7)throw new IllegalStateException("first changed");});
        NativeAuthorities.State early=original.seal();
        original.remember(other,"source",()->{if(later[0]!=11)throw new IllegalStateException("later changed");});
        NativeAuthorities.State terminal=original.seal();later[0]=12;
        NativeAuthorities.Verifier check=early.verifier();while(!check.advance()){};
        NativeAuthorities resumed=new NativeAuthorities();resumed.restore(check);
        require(resumed.seal().size()==1,"A later Handler source leaked into an earlier checkpoint state");
        try{NativeAuthorities.Verifier bad=terminal.verifier();while(!bad.advance()){};throw new AssertionError("Native source drift was accepted");}catch(IllegalStateException expected){}
        first[0]=8;
        try{NativeAuthorities.Verifier bad=early.verifier();while(!bad.advance()){};throw new AssertionError("A sealed source was treated as immutable without checking it");}catch(IllegalStateException expected){}
        first[0]=7;NativeAuthorities.Verifier threadCheck=early.verifier();Throwable[] failure={null};Thread thread=new Thread(()->{try{threadCheck.advance();}catch(Throwable e){failure[0]=e;}});thread.start();thread.join();
        require(failure[0] instanceof IllegalStateException,"Native source authority crossed its client thread");
        original.close();original.close();resumed.close();
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
