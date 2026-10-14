package minikv;

import java.io.*;
import java.nio.file.*;
    import java.util.*;

public class KVStore implements AutoCloseable{
    private MemTable memtable;
    private WriteAheadLog wal;

    private static final long FLUSH_THRESHOLD = 20;
    private final String dir;
    private final List<String>sstables = new ArrayList<>();
    private int nextSstId = 1; 

    private final List<BloomFilter>filters = new ArrayList<>();

    private static int idOf(File f){
        String n = f.getName();
        return Integer.parseInt(n.substring(4,n.length()-3));
    }

    public KVStore(String dir) throws IOException{
        this.dir = dir;
        Files.createDirectories(Paths.get(dir));
        this.wal = new WriteAheadLog(dir+"/wal.log");
        this.memtable = new MemTable();


        File[] files = new File(dir).listFiles(
            (d,name) -> name.startsWith("sst-") && name.endsWith(".db")
        );

        if(files!=null){
            Arrays.sort(files,Comparator.comparingInt(KVStore::idOf));
            for(File f: files){
                sstables.add(f.getPath());
                filters.add(SSTableReader.loadBloom(f.getPath()));
                nextSstId = idOf(f)+1;
            }
        }

        int[] replayed = {0};   // an array, so the anonymous class can change it

        wal.replay(new WalHandler() {
            public void onPut(String k, String v){
                memtable.put(k,v);
                replayed[0]++;
            }

            public void onDelete(String k){
                memtable.delete(k);
                replayed[0]++;
            }
        });

        System.out.println("startup: " + sstables.size() + " sstable(s) found, " + replayed[0] + " record(s) replayed from WAL");

    }

    public void put(String key, String value) throws IOException{
        wal.append(WriteAheadLog.PUT, key, value);
        memtable.put(key,value);
        if(memtable.approximateSizeBytes()>=FLUSH_THRESHOLD)flush();
    }

    public void delete(String key) throws IOException{
        wal.append(WriteAheadLog.DELETE,key,null);
        memtable.delete(key);
        if(memtable.approximateSizeBytes()>=FLUSH_THRESHOLD)flush();
    }

    public LookupResult get(String key){
        LookupResult r = memtable.get(key);
        if(!(r instanceof LookupResult.Absent)){
            System.out.println("get(" + key + ") -> " + r + "  [MEMTABLE]");
            return r;
        }

        for(int i = sstables.size()-1;i>=0;i--){

            String path = sstables.get(i);
            if(!filters.get(i).mightContain(key)){
                System.out.println("get(" + key + ") skipped " + path + "  [BLOOM: definitely absent]");
                continue;
            }

            try{
                r = SSTableReader.get(path, key);
            }catch(Exception e){
                System.out.println(e);
            }
            
            if (!(r instanceof LookupResult.Absent)) {
                System.out.println("get(" + key + ") -> " + r + "  [SSTABLE " + sstables.get(i) + "]");
                return r;
            }
            System.out.println("get(" + key + ") not in " + sstables.get(i));
        }

        System.out.println("get(" + key + ") -> Absent  [NOT FOUND anywhere]");
        return new LookupResult.Absent();
        
    }

    private void flush() throws IOException {
        String path = dir+"/sst-"+nextSstId+".db";
        SSTableWriter.write(path, memtable.entriesInOrder());
        
        wal.close();
        Files.delete(Paths.get(dir+"/wal.log"));
        wal = new WriteAheadLog(dir+"/wal.log");

        memtable = new MemTable();
        sstables.add(path);
        filters.add(SSTableReader.loadBloom(path));
        nextSstId++;
        System.out.println("flush -> " + path);
    }

    @Override 
    public void close() throws IOException{
        wal.close();
    }

}
