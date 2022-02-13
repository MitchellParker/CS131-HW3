import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

public class Compressor extends Thread {

  private final Pigzj pigzj;
  private final Writer writer;
  private final byte[] compressedBuf;
  private final Deflater def;

  private int blockIndex;     // index of current input
  private byte[] input;       // uncompressed data
  private byte[] dict;        // dictionary for better compression
  private int nBytes;         // number of valid bytes in input
  private boolean lastBlock;  // true if block being processed is the last one overall

  // the following determine what this compressor should do next
  private boolean awaitingInput;
  private boolean hasUnprocessedInput;
  private boolean readyToExit;

  public Compressor(Pigzj p, Writer w) {
    this.pigzj = p;
    this.writer = w;
    this.compressedBuf = new byte[Pigzj.BLOCK_SIZE * 2];
    def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);

    this.awaitingInput = true;
    this.hasUnprocessedInput = false;
    this.readyToExit = false;
  }

  public synchronized boolean setInput(int blockIndex, byte[] blockBuf, int nBytes, byte[] dict, boolean lastBlock) {
    if (!awaitingInput) {
      return false;
    }
    this.awaitingInput = false;
    this.hasUnprocessedInput = true;
    this.blockIndex = blockIndex;
    this.input = blockBuf;
    this.dict = dict;
    this.nBytes = nBytes;
    this.lastBlock = lastBlock;
    notify();
    return true;
  }

  public boolean available() {
    return awaitingInput;
  }

  public synchronized void end() {
    this.readyToExit = true;
    notify();
  }

  public synchronized void run() {
    try {
      while (!readyToExit) {
        while (awaitingInput && !readyToExit) {
          wait();
        }
        if (this.hasUnprocessedInput) {
          setupDeflater();
          ByteArrayOutputStream buf = new ByteArrayOutputStream();
          compressInto(buf);
          writer.writeBlock(this.blockIndex, buf, this.lastBlock);
          this.awaitingInput = true;
          pigzj.notifyCompressorAvailable();
        }
      }
    } catch (InterruptedException e) {
      System.err.println(e.getMessage());
    }
  }

  private void setupDeflater() {
    def.reset();
    if (dict != null) {
      def.setDictionary(dict);
    }
    def.setInput(input, 0, nBytes);
    if (lastBlock) {
      def.finish();
    }
  }

  private void compressInto(ByteArrayOutputStream out) {
    if (this.lastBlock) {
      /* If we've read all the bytes in the file, this is the last block.
         We have to clean out the deflater properly */
      if (!def.finished()) {
        def.finish();
        while (!def.finished()) {
          int deflatedBytes = def.deflate(
              compressedBuf, 0, compressedBuf.length, Deflater.NO_FLUSH);
          if (deflatedBytes > 0) {
            out.write(compressedBuf, 0, deflatedBytes);
          }
        }
      }
    } else {
      /* Otherwise, just deflate and then write the compressed block out. Not
    using SYNC _FLUSH here leads to some issues, but using it probably results
    in less efficient compression. Ther e's probably a better
         way to deal with this. */
      int deflatedBytes = def.deflate(
          compressedBuf, 0, compressedBuf.length, Deflater.SYNC_FLUSH);
      if (deflatedBytes > 0) {
        out.write(compressedBuf, 0, deflatedBytes);
      }
    }
    this.hasUnprocessedInput = false;
  }
}
