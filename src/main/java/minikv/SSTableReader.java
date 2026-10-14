package minikv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import minikv.LookupResult.*;

public class SSTableReader {
    public static LookupResult get(String path, String key) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {

            raf.seek(raf.length() - 12);
            long indexOffset = raf.readLong();
            int indexCount = raf.readInt();

            raf.seek(indexOffset);
            List<String> idxKeys = new ArrayList<>();
            List<Long> idxOffsets = new ArrayList<>();
            
            for(int i = 0; i<indexCount; i++){
                int keylen = raf.readInt();
                byte[] kb = new byte[keylen];
                raf.readFully(kb);

                long offset = raf.readLong();

                idxKeys.add(new String(kb, StandardCharsets.UTF_8));
                idxOffsets.add(offset);

            }

            int low = 0, high = idxKeys.size()-1;
            int ans = -1;
            while(low<=high){
                int mid = low+(high-low)/2;
                if(idxKeys.get(mid).compareTo(key)<=0){
                    ans = mid;
                    low = mid+1;
                }
                else high = mid-1;
            }

            if(ans==-1)return new LookupResult.Absent();

            // 4) scan from that offset
            raf.seek(idxOffsets.get(ans));
            while (raf.getFilePointer() < indexOffset) {
                int keylen = raf.readInt();
                byte[] kb = new byte[keylen];
                raf.readFully(kb);
                String k = new String(kb, StandardCharsets.UTF_8);

                int flag = (int)raf.readByte();;
                int vallen = raf.readInt();
                byte[] vb = new byte[vallen];
                raf.readFully(vb);

                int cmp = k.compareTo(key);
                if(cmp==0){
                    return flag==1?new LookupResult.Deleted():new LookupResult.Found(new String(vb, StandardCharsets.UTF_8));
                }else if(cmp>0){
                    return new LookupResult.Absent();
                }

            }
            return new LookupResult.Absent();
        }
    }
}