import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

public class Pigzj {
  
  public final static int BLOCK_SIZE = 128 * 1024;
  public final static int DICT_SIZE = 32 * 1024;

  public static void main(String[] args) {
    try {
      // SingleThreadedGZipCompressor c = new SingleThreadedGZipCompressor(args[0]);
      // c.compress();
      compressAll();
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  private static void compressAll() throws IOException {
    CRC32 crc = new CRC32();
    crc.reset();
    /* Buffers for input blocks, compressed bocks, and dictionaries */
    byte[] blockBuf = new byte[BLOCK_SIZE];
    byte[] dictBuf = new byte[DICT_SIZE];
    InputStream inStream = System.in;
    Writer writer = new Writer(System.out);
    writer.start();
    Compressor compressor = new Compressor(writer);
    compressor.start();
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
      if (hasDict) {
        while (!compressor.setInput(blockIndex, blockBuf, nBytes, dictBuf, nBytes != BLOCK_SIZE)) {
          Thread.yield();
        }
      } else {
        while (!compressor.setInput(blockIndex, blockBuf, nBytes, null, nBytes != BLOCK_SIZE)) {
          Thread.yield();
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
    compressor.end();
  }
}
