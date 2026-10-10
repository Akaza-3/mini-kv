package minikv;

public class Main {
    public static void main(String args[]){

        MemTable m = new MemTable();
        
        m.put("a", "1");
        System.out.println(m.get("a"));                 // Found[value=1]
        System.out.println(m.approximateSizeBytes());   // 2

        m.put("a", "333");
        System.out.println(m.approximateSizeBytes());   // 4

        m.delete("a");
        System.out.println(m.get("a"));                 // Deleted[]
        System.out.println(m.approximateSizeBytes());   // 1

        System.out.println(m.get("z"));    

    }
}
