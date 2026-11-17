package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Owned snapshot lifecycle. Release borrowed references without clearing the native sources. */
abstract class ReaderSnapshot implements AutoCloseable {
  private boolean closed;

  protected final void checkOpen(){
    if(closed)throw new Jobs.Fault("capture_closed","Native reader snapshot is closed");
    Jobs.checkpoint();
  }
  public void verify(){checkOpen();}
  public void finishVerification(){verify();}
  protected abstract void release();

  @Override public final void close(){
    if(closed)return;
    closed=true;
    release();
  }
}
