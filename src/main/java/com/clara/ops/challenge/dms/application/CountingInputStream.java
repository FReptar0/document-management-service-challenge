package com.clara.ops.challenge.dms.application;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * {@link FilterInputStream} that records the number of bytes successfully read. Used by the upload
 * use case to discover the actual file size after the stream has been consumed by the storage
 * adapter — avoids any need to buffer the payload to determine its length.
 */
public final class CountingInputStream extends FilterInputStream {

  private long count;

  public CountingInputStream(InputStream in) {
    super(in);
  }

  /** Total bytes read from this stream so far. */
  public long count() {
    return count;
  }

  @Override
  public int read() throws IOException {
    int c = super.read();
    if (c >= 0) {
      count++;
    }
    return c;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int n = super.read(b, off, len);
    if (n > 0) {
      count += n;
    }
    return n;
  }
}
