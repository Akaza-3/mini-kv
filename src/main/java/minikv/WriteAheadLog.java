package minikv;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;


public class WriteAheadLog implements Closeable {

    //PUT and DELETE are two types of operations.
    public static final byte PUT = 1;
    public static final byte DELETE = 2;

    //MAX_LEN is the safety limits on lengths read back from file.
    private static final int MAX_LEN = 64 * 1024 * 1024;

    //out is the open file we write to. path is saved to open the same file on replay.
    private final FileOutputStream out;
    private final String path;

    public WriteAheadLog(String path) throws IOException{
        this.out = new FileOutputStream(path, true);
        this.path = path;
    }

    //append function writes on record to the log file. 
    public void append(byte op, String key, String value) throws IOException{

        //convert key and value in byte arrays to store directly in binary format.
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        byte[] v =(value==null)? new byte[0]: value.getBytes(StandardCharsets.UTF_8);

        //ByteArrayOutputStream builds the record in memory and DataOutputStream writes to it.
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(buf);
        
        data.writeByte(op);
        data.writeInt(k.length);
        data.write(k);
        data.writeInt(v.length);
        data.write(v);

        //checksum is calculated to ensure data written in not corrupt.
        CRC32 crc = new CRC32();
        crc.update(buf.toByteArray());
        data.writeInt((int)crc.getValue());

        //write the in memory output to the file and then immediately flush it to disk.
        out.write(buf.toByteArray());
        out.flush();
        out.getFD().sync();
    }

    //reads all the records back
    public void replay(WalHandler handler) throws IOException{

        long goodOffset = 0;
        //Open the file for reading. FileInputStream reads bytes, BufferedInputStream makes reading fast, 
        //and DataInputStream lets us read ints and bytes directly (readByte, readInt).
        try(DataInputStream in = new DataInputStream(
            new BufferedInputStream(new FileInputStream(path)))){
                while(true){
                    try{    
                        //reads 1 byte of operation - PUT | DELETE
                        byte op = in.readByte();
                        
                        //reads the next integer which is key length
                        int keyLen = in.readInt();

                        //check whether keylen is out of bounds
                        if(keyLen<0 || keyLen>MAX_LEN)break;

                        //read exactly keylen bytes
                        byte[] key = new byte[keyLen];
                        in.readFully(key);

                        int valueLen = in.readInt();
                        if(valueLen<0 || valueLen>MAX_LEN)break;
                        byte[] value = new byte[valueLen];
                        in.readFully(value);

                        int crcVal = in.readInt();

                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                        DataOutputStream data = new DataOutputStream(buf);
                        data.writeByte(op);
                        data.writeInt(keyLen);
                        data.write(key);
                        data.writeInt(valueLen);
                        data.write(value);

                        //compare the current read data checksum with the checksum read from file. if they dont match -> corrupted record
                        CRC32 crc = new CRC32();
                        crc.update(buf.toByteArray());
                        if((int) crc.getValue()!=crcVal)break;

                        String k = new String(key, StandardCharsets.UTF_8);
                        if(op==PUT)handler.onPut(k, new String(value, StandardCharsets.UTF_8));
                        else if(op==DELETE) handler.onDelete(k);
                        else break;
                        
                        //adding good bytes to offset 13: 1 for operation, 4 for keylen, 4 for valuelen, 4 for crc
                        goodOffset += 13 + keyLen + valueLen;

                    }catch(EOFException e){
                        break;
                    }
                }
            }

            if(goodOffset<new File(path).length()){
                try(RandomAccessFile raf = new RandomAccessFile(path, "rw")){
                    raf.setLength(goodOffset);
                }
            }
    }

    //closes the output file
    @Override 
    public void close() throws IOException{
        out.close();
    }

   
}
