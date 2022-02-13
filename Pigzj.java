import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

public class Pigzj {
  
  public final static int BLOCK_SIZE = 128 * 1024;
  public final static int DICT_SIZE = 32 * 1024;

  public static void main(String[] args) {
    try {
      Pigzj pig = new Pigzj(getNumThreads(args));
      pig.compressAll();
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  private static int getNumThreads(String[] args) {
    int processors = Runtime.getRuntime().availableProcessors();
    if (args.length == 0) {
      return processors;
    } else if (args.length == 2 && (args[0].equals("-p") || args[0].equals("--processes"))) {
      try {
        int requested = Integer.parseInt(args[1]);
        if (requested > processors*4) {
          System.err.println("Too many processes. There are " + processors + " available processors.");
          System.exit(1);
        } else if (requested <= 0) {
          System.err.println("Processes must be a positive integer");
          System.exit(1);
        } else {
          return requested;
        }
      } catch (NumberFormatException e) {
        System.err.println("Invalid integer " + args[1]);
        System.exit(1);
      }
    } else {
      System.err.println("Usage: pigzj [-p PROCESSES]");
      System.exit(1);
    }
    return -1;
  }

  private Writer writer;
  private Compressor[] compressors;

  public Pigzj(int compressorThreads) {
    this.writer = new Writer(System.out);
    this.writer.start();
    this.compressors = new Compressor[compressorThreads];
    for (int i = 0; i < compressorThreads; i++) {
      this.compressors[i] = new Compressor(this, writer);
      this.compressors[i].start();
    }
  }

  public synchronized void notifyCompressorAvailable() {
    notify();
  } 

  private synchronized Compressor getAvailableCompressor() throws InterruptedException {
    while (true) {
      for (Compressor c : this.compressors) {
        if (c.available()) {
          return c;
        }
      }
      wait();
    }
  }

  private void endAllCompressors() {
    for (Compressor c : compressors) {
      c.end();
    }
  }

  private void compressAll() throws IOException, InterruptedException {
    CRC32 crc = new CRC32();
    crc.reset();
    /* Buffers for input blocks, compressed bocks, and dictionaries */
    byte[] blockBuf = new byte[BLOCK_SIZE];
    byte[] dictBuf = new byte[DICT_SIZE];
    InputStream inStream = System.in;
    long totalBytesRead = 0;
    boolean hasDict = false;
    int nBytes = inStream.read(blockBuf);
    int blockIndex = 0;
    while (nBytes > 0) {
      totalBytesRead += nBytes;
      /* Update the CRC every time we read in a new block. */
      crc.update(blockBuf, 0, nBytes);
      /* If we saved a dictionary from the last block, prime the deflater with
       * it */
      Compressor compressor = getAvailableCompressor();
      if (hasDict) {
        while (!compressor.setInput(blockIndex, blockBuf, nBytes, dictBuf, nBytes != BLOCK_SIZE)) {
          // try again
        }
      } else {
        while (!compressor.setInput(blockIndex, blockBuf, nBytes, null, nBytes != BLOCK_SIZE)) {
          // try again
        }
      }
      /* If we read in enough bytes in this block, store the last part as the
 diction ary for the next iteration */
      if (nBytes >= DICT_SIZE) {
        dictBuf = new byte[DICT_SIZE];
        System.arraycopy(blockBuf, nBytes - DICT_SIZE, dictBuf, 0, DICT_SIZE);
        hasDict = true;
      } else {
        hasDict = false;
      }

      blockBuf = new byte[BLOCK_SIZE];
      nBytes = inStream.read(blockBuf);
      blockIndex++;
    }
    /* Finally, write the trailer and then write to STDOUT */
    writer.writeTrailer(crc.getValue(), totalBytesRead);
    endAllCompressors();
  }
}
