package minikv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class BloomFilter {
    private final BitSet bits;
    private final int numBits;
    private final int numHashes;

    public BloomFilter(int expectedKeys) {
        this.numBits = Math.max(64, expectedKeys * 10);
        this.numHashes = 7;
        this.bits = new BitSet(numBits);
    }

    public BloomFilter(BitSet bits, int numBits, int numHashes) {
        this.numBits = numBits;
        this.numHashes = numHashes;
        this.bits = bits;
    }

    private int bitIndex(String key, int i) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;  h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;  h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        long h1 = (int) h;
        long h2 = ((int) (h >>> 32)) | 1;
        return (int) Math.floorMod(h1 + i * h2, (long) numBits);
    }

    public void add(String key) {
        for(int i=0;i<numHashes;i++){
            bits.set(bitIndex(key,i));
        }
    }

    public boolean mightContain(String key) {
        for(int i=0;i<numHashes;i++){
            if(!bits.get(bitIndex(key,i)))return false;
        }
        return true;
    }

    public static BloomFilter fromBytestoFilter(byte[] data) throws IOException{
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int numBits = in.readInt();
        int numHashes = in.readInt();
        int len = in.readInt();
        byte[] arr = new byte[len];
        in.readFully(arr);
        return new BloomFilter(BitSet.valueOf(arr), numBits, numHashes);
    }

    public byte[] fromFilterToBytes() throws IOException{
        byte[] arr = bits.toByteArray();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeInt(numBits);
        out.writeInt(numHashes);
        out.writeInt(arr.length);
        out.write(arr);
        return buf.toByteArray();
    }
}