package minikv;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;


public class MemTable {
    
    private final ConcurrentSkipListMap<String,Entry>map = new ConcurrentSkipListMap<>();
    private final AtomicLong size = new AtomicLong();
    
    private static long entrySize(String key, Entry e){
        long n = key.getBytes(StandardCharsets.UTF_8).length;
        if(e.tombstone()==false){
            n+=e.value().getBytes(StandardCharsets.UTF_8).length;
        }

        return n;
    }

    private void store(String key, Entry newEntry){
        Entry old = map.put(key, newEntry);
        if(old!=null)size.addAndGet(-entrySize(key,old));
        size.addAndGet(entrySize(key,newEntry));
    }

    public void put(String key, String val){
        Objects.requireNonNull(key,"key");
        Objects.requireNonNull(val,"val");
        store(key, new Entry(val,false));
    }

    public LookupResult get(String key){
        Objects.requireNonNull(key, "key");
        Entry e = map.get(key);
        if(e==null)return new LookupResult.Absent();
        if(e.tombstone())return new LookupResult.Deleted();
        return new LookupResult.Found(e.value());
    }

    public void delete(String key){
        Objects.requireNonNull(key, "key");
        store(key, new Entry(null,true));
    }

    public Iterable<Map.Entry<String,Entry>> entriesInOrder(){
        return map.entrySet();
    }

    public long approximateSizeBytes(){
        return size.get();
    }
}