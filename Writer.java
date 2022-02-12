import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

public class Writer extends Thread {

  private final static int GZIP_MAGIC = 0x8b1f;
  private final static int TRAILER_SIZE = 8;
  
  // where the output goes when it's all recieved
  private final OutputStream finalDestination;
  // until all output is recieved, it is buffered in here
  private final ByteArrayOutputStream outStream;
  private final Map<Integer, ByteArrayOutputStream> compressedBlocks;

  private int next;
  private int finalBlock;
  private byte[] trailer;

  public Writer(OutputStream out) {
    this.finalDestination = out;
    this.outStream = new ByteArrayOutputStream();
    this.compressedBlocks = new HashMap<>();

    this.next = 0;
    this.finalBlock = -1;
    this.trailer = null;
  }

  public synchronized void writeBlock(int blockIndex, ByteArrayOutputStream block, boolean finalBlock)
  throws IllegalArgumentException {
    if (blockIndex < 0) {
      throw new IllegalArgumentException("negative block index");
    }
    if (blockIndex < this.next || compressedBlocks.containsKey(blockIndex)) {
      throw new IllegalArgumentException("duplicate block index");
    }
    compressedBlocks.put(blockIndex, block);
    if (finalBlock) {
      this.finalBlock = blockIndex;
    }
    notify();
  }

  public synchronized void writeTrailer(long crcval, long totalBytes) throws IOException {
    byte[] t = new byte[TRAILER_SIZE];
    buildTrailer(crcval, totalBytes, t, 0);
    this.trailer = t;
    notify();
  }

  public synchronized void run() {
    try {
      outputHeader();
      while (blocksRemaining()) {
        while (!compressedBlocks.containsKey(next)) {
          wait();
        }
        outputNextBlock();
      }
      while (this.trailer == null) {
        wait();
      }
      outputTrailer();
      this.outStream.writeTo(finalDestination);
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  private boolean blocksRemaining() {
    return this.finalBlock == -1 || this.next <= this.finalBlock;
  }

  /* 
   * Tries to write block with index next to outStream
   * Returns false if this writer hasn't recieved that block,
   * true if it is able to output successfully.
   */
  private boolean outputNextBlock() throws IOException {
    if (!compressedBlocks.containsKey(next)) {
      return false;
    }
    compressedBlocks.get(next).writeTo(outStream);
    compressedBlocks.remove(next);
    next++;
    return true;
  }
  private void outputHeader() throws IOException {
    this.outStream.write(new byte[] {
        (byte)GZIP_MAGIC,        // Magic number (short)
        (byte)(GZIP_MAGIC >> 8), // Magic number (short)
        (byte)Deflater.DEFLATED, // Compression method (CM)
        0,                       // Flags (FLG)
        0,                       // Modification time MTIME (int)
        0,                       // Modification time MTIME (int)
        0,                       // Modification time MTIME (int)
        0,                       // Modification time MTIME (int)Sfil
        0,                       // Extra flags (XFLG)
        0                        // Operating system (OS)
    });
  }
  private boolean outputTrailer() throws IOException {
    if (this.trailer == null) {
      return false;
    }
    outStream.write(this.trailer);
    return true;
  } 
  /*
   * Writes GZIP member trailer to a byte array, starting at a given
   * offset.
   */
  private static void buildTrailer(long crcval, long totalBytes, byte[] buf, int offset)
      throws IOException {
    writeInt((int)crcval, buf, offset); // CRC-32 of uncompr. data
    writeInt((int)totalBytes, buf, offset + 4); // Number of uncompr. bytes
  }
  /*
   * Writes integer in Intel byte order to a byte array, starting at a
   * given offset.
   */
  private static void writeInt(int i, byte[] buf, int offset) throws IOException {
    writeShort(i & 0xffff, buf, offset);
    writeShort((i >> 16) & 0xffff, buf, offset + 2);
  }
  /*
   * Writes short integer in Intel byte order to a byte array, starting
   * at a given offset
   */
  private static void writeShort(int s, byte[] buf, int offset) throws IOException {
    buf[offset] = (byte)(s & 0xff);
    buf[offset + 1] = (byte)((s >> 8) & 0xff);
  }
}
