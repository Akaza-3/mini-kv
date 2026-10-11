package minikv;

import java.io.*;

public class KVStore implements AutoCloseable{
    private final MemTable memtable;
    private final WriteAheadLog wal;

    public KVStore(String walPath) throws IOException{
        this.wal = new WriteAheadLog(walPath);
        this.memtable = new MemTable();

        wal.replay(new WalHandler() {
            public void onPut(String k, String v){
                memtable.put(k,v);
            }

            public void onDelete(String k){
                memtable.delete(k);
            }
        });
    }

    public void put(String key, String value) throws IOException{
        wal.append(WriteAheadLog.PUT, key, value);
        memtable.put(key,value);
    }

    public void delete(String key) throws IOException{
        wal.append(WriteAheadLog.DELETE,key,null);
        memtable.delete(key);
    }

    public LookupResult get(String key){
        return memtable.get(key);
    }

    @Override 
    public void close() throws IOException{
        wal.close();
    }

}
