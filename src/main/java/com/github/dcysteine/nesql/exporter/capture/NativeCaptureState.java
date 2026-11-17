package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.RendererRandom;
import com.github.dcysteine.nesql.exporter.task.ClientThread;

/** A process-local boundary state for one closed Handler. It owns no GPU
 * resources, has no JSON decoder and never writes a borrowed game Random. */
final class NativeCaptureState {
    private final ClientThread.Guard guard;
    private final NativeAuthorities.State sources;
    private final RendererRandom.State random;
    private NativeCaptureState(ClientThread.Guard guard,NativeAuthorities.State sources,RendererRandom.State random){this.guard=guard;this.sources=sources;this.random=random;}
    static NativeCaptureState seal(Images images,ClientThread.Session session){
        session.check();return new NativeCaptureState(session.guard(),images.authorities.seal(),images.random.save());
    }
    Verification verification(){return new Verification();}
    final class Verification {
        private final NativeAuthorities.Verifier source=sources.verifier();private boolean complete;
        boolean advance(){guard.check();complete=source.advance();return complete;}
        boolean advanceAndRestore(Images images){if(!advance())return false;restore(images);return true;}
        void restore(Images images){
            guard.check();if(!complete)throw new IllegalStateException("Native continuation has not verified its whole prefix");
            images.random.restore(random,(nativeSource,initial)->sources.verifyRandom(nativeSource));
            images.authorities.restore(source);
        }
    }
}
