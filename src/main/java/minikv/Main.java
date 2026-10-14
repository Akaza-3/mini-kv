package minikv;

import java.io.File;

public class Main {
    public static void main(String args[]) throws Exception{

        // MemTable m = new MemTable();
        
        // m.put("a", "1");
        // System.out.println(m.get("a"));                 // Found[value=1]
        // System.out.println(m.approximateSizeBytes());   // 2

        // m.put("a", "333");
        // System.out.println(m.approximateSizeBytes());   // 4

        // m.delete("a");
        // System.out.println(m.get("a"));                 // Deleted[]
        // System.out.println(m.approximateSizeBytes());   // 1

        // System.out.println(m.get("z")); 
        
        // try (WriteAheadLog wal = new WriteAheadLog("wal.log")) {
        //     wal.append(WriteAheadLog.PUT, "a", "1");
        //     wal.append(WriteAheadLog.PUT, "b", "2");
        //     wal.append(WriteAheadLog.DELETE, "a", null);
        // }catch(Exception e){}

        // try(WriteAheadLog wal = new WriteAheadLog("wal.log")){
        //     wal.replay(new WalHandler() {
        //         public void onPut(String k, String v) { System.out.println("PUT " + k + " " + v); }
        //         public void onDelete(String k) { System.out.println("DELETE " + k); }
        //     });
        // }catch(Exception e){}


        // try (KVStore db = new KVStore("wal.log")) {
        //     // db.put("a", "1");
        //     // db.put("b", "2");
        //     db.put("c","3");
        //     // db.delete("a");

        // }catch(Exception e){}
        // try (KVStore db = new KVStore("wal.log")) {
        //     System.out.println(db.get("a"));   // Deleted[]
        //     System.out.println(db.get("b"));   // Found[value=2]
        //     System.out.println(db.get("c"));   // Absent[]
        // }catch(Exception e){}

        // MemTable m = new MemTable();
        // m.put("a", "1");
        // m.put("b", "2");
        // m.delete("c");
        // try{
        //     SSTableWriter.write("sst-1.db", m.entriesInOrder());
        // }catch(Exception e){}

        // System.out.println("data dir: " + new File("data").getAbsolutePath());

        // try{
        //     KVStore db = new KVStore("data");
        //     db.put("key1", "val1");
        //     db.put("key2", "val2");
        //     db.put("key3", "val3");   // 24 bytes, triggers the flush
        //     db.put("key4", "val4");
        //     db.close();
        // }catch(Exception e){}
        MemTable m = new MemTable();
        m.put("a", "1"); m.put("b", "2"); m.delete("c");
        try{SSTableWriter.write("sst-test.db", m.entriesInOrder());}catch(Exception e){};

        for (String k : new String[]{"a", "b", "c", "d", "0"}) {
            System.out.println(k + " -> " + SSTableReader.get("sst-test.db", k));
        } 
    }
}
