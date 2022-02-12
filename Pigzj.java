import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

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
    byte[] cmpBlockBuf = new byte[BLOCK_SIZE * 2];
    byte[] dictBuf = new byte[DICT_SIZE];
    Deflater compressor = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    InputStream inStream = System.in;
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Writer writer = new Writer(System.out);
    writer.start();
    long totalBytesRead = 0;
    boolean hasDict = false;
    int nBytes = inStream.read(blockBuf);
    int blockIndex = 0;
    while (nBytes > 0) {
      totalBytesRead += nBytes;
      /* Update the CRC every time we read in a new block. */
      crc.update(blockBuf, 0, nBytes);
      compressor.reset();
      /* If we saved a dictionary from the last block, prime the deflater with
       * it */
      if (hasDict) {
        compressor.setDictionary(dictBuf);
      }

      compressor.setInput(blockBuf, 0, nBytes);

      if (nBytes != BLOCK_SIZE) {
        /* If we've read all the bytes in the file, this is the last block.
           We have to clean out the deflater properly */
        if (!compressor.finished()) {
          compressor.finish();
          while (!compressor.finished()) {
            int deflatedBytes = compressor.deflate(
                cmpBlockBuf, 0, cmpBlockBuf.length, Deflater.NO_FLUSH);
            if (deflatedBytes > 0) {
              outStream.write(cmpBlockBuf, 0, deflatedBytes);
            }
          }
          writer.writeBlock(blockIndex, outStream, true);
        }
      } else {
        /* Otherwise, just deflate and then write the compressed block out. Not
      using SYNC _FLUSH here leads to some issues, but using it probably results
      in less efficient compression. Ther e's probably a better
           way to deal with this. */
        int deflatedBytes = compressor.deflate(
            cmpBlockBuf, 0, cmpBlockBuf.length, Deflater.SYNC_FLUSH);
        if (deflatedBytes > 0) {
          outStream.write(cmpBlockBuf, 0, deflatedBytes);
        }
        writer.writeBlock(blockIndex, outStream, false);
      }
      /* If we read in enough bytes in this block, store the last part as the
 diction ary for the next iteration */
      if (nBytes >= DICT_SIZE) {
        System.arraycopy(blockBuf, nBytes - DICT_SIZE, dictBuf, 0, DICT_SIZE);
        hasDict = true;
      } else {
        hasDict = false;
      }

      compressor = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
      outStream = new ByteArrayOutputStream();
      nBytes = inStream.read(blockBuf);
      blockIndex++;
    }
    /* Finally, write the trailer and then write to STDOUT */
    writer.writeTrailer(crc.getValue(), totalBytesRead);
  }
}
