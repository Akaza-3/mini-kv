package minikv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SSTableWriter {
    private static final int INDEX_INTERVAL = 2;

    private static void writeRecord(DataOutputStream out, String key, Entry e) throws IOException{
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        byte[] v = e.value()==null? new byte[0]: e.value().getBytes(StandardCharsets.UTF_8);
        
        out.writeInt(k.length);
        out.write(k);
        out.writeByte(e.tombstone()?1:0);
        out.writeInt(v.length);
        out.write(v);

        
    }

    public static void write(String path, Iterable<Map.Entry<String, Entry>> entries) throws IOException {
        String tmp = path + ".tmp";
        List<String> indexKeys = new ArrayList<>();
        List<Long> indexOffsets = new ArrayList<>();

        try (FileOutputStream fos = new FileOutputStream(tmp);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos))) {

            int i = 0;
            for (Map.Entry<String, Entry> me : entries) {
                if(i%INDEX_INTERVAL==0){
                    indexKeys.add(me.getKey());
                    indexOffsets.add((long)out.size());
                }
                writeRecord(out,me.getKey(),me.getValue());
                i++;
            }

            long indexOffset = out.size();
            for(int j=0;j<indexKeys.size();j++){
                byte[] k = indexKeys.get(j).getBytes(StandardCharsets.UTF_8);
                out.writeInt(k.length);
                out.write(k);
                out.writeLong(indexOffsets.get(j));
            }

            out.writeLong(indexOffset);
            out.writeInt(indexKeys.size());

            out.flush();
            fos.getFD().sync();
        }
        Files.move(Paths.get(tmp), Paths.get(path), StandardCopyOption.ATOMIC_MOVE);
    }
}