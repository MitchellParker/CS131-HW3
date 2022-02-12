import java.util.zip.Deflater;

public class Compressor extends Thread {

  private Deflater def;

  private byte[] input;
  private byte[] dict;
  private int nBytes;
  private boolean lastBlock;

  public Compressor() {
    def = new Deflater();
  }

  public synchronized void setInput(byte[] blockBuf, int nBytes, byte[] dict, boolean lastBlock) {
    this.input = blockBuf;
    this.dict = dict;
    this.nBytes = nBytes;
    this.lastBlock = lastBlock;
    notify();
  }




}
