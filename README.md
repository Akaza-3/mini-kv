So i read Designing Data Intensive Applications by Martin Kleppmann and decided to design a simple key-value store based on LSM trees.

#step 1: build a memtable

a memtable is a sorted in memory place where data is stored. it uses skiplists or red-black trees to keep the data sorted. in my implementation i have used concurrentskiplistmap which is a thread safe map and keeps the keys in sorted order.

Q1. What is a memtable and why does an LSM-tree have one?
It’s the in-memory, sorted buffer that takes all recent writes. Writing to memory is cheap, and disk is only touched in large sequential flushes. That’s the source of LSM’s high write throughput.

Q2. Why a sorted structure and not a HashMap?
The memtable is flushed to an SSTable, which must be sorted by key. A sorted map hands over entries in order, so the flush is a single pass. It also supports range scans. A HashMap would need a sort at flush time and can’t do ordered reads.

Q3. Why a skip list, not a red-black tree or a B-tree?
All give O(log n) operations. A skip list is simpler to make concurrent: it can be lock-free, because inserts only change local pointers, while a balanced tree needs rotations that touch several nodes. That’s why LevelDB and RocksDB use skip lists for the memtable. A B-tree is for disk pages and isn’t needed in memory.

Q4. How do you handle deletes?
By writing a tombstone entry, never by removing the key. Older SSTables are immutable and may still contain the key. If a=1 is on disk and I only remove a from memory, get(a) falls through and returns 1. The tombstone shadows older values, since reads go newest to oldest. SSTables are immutable as that enables fast, sequential writes, no concurrency locks for reading data and simplified crash recovery (if machine crashes, partially written or deleted records dont corrupt data).

Q5. What if the key being deleted isn’t in the memtable?
The tombstone must still be written. The key may live in an SSTable, and the memtable can’t know. A delete that only writes when the key is present has the same resurrection bug.

Q6. How do you represent a tombstone?
As an explicit field: Entry(value, tombstone). null is ambiguous (and ConcurrentSkipListMap rejects null values), and a magic string like "__DELETED__" can collide with user data.

Q7. Why does get return three outcomes instead of a value or null?
The caller must tell apart “deleted” (stop searching, return not found) from “never seen” (keep searching older files). A single null conflates them and causes the resurrection bug.

Q8. When does the memtable get flushed, and how do you track that?
When its size passes a threshold, for example 4 MB. I keep an AtomicLong of key and value bytes. On overwrite, map.put returns the old entry, so I subtract its size and add the new one. It’s approximate because it ignores object overhead.

Q9. Is your memtable thread-safe?
The map operations are, and the size counter is atomic. But store() does two steps: it updates the map and then the counter, which isn’t one atomic action. A reader could briefly see a counter that’s slightly stale. That’s acceptable, since the size is only a flush trigger. Compound operations such as “check size, then flush” would need extra coordination, such as a lock or swapping in a new memtable.

Q10. What happens when the memtable is full while writes keep arriving?
Make the current memtable immutable (frozen), start a new active memtable for writes, and flush the frozen one in the background. Reads check the active one, then the frozen ones, then the SSTables. That avoids blocking writes during a flush.

Q11. What happens to the memtable if the process crashes?
It’s in memory, so it’s lost. That’s why every write is first appended to a write-ahead log, and on restart the log is replayed to rebuild the memtable.

Q12. How would you reduce memory overhead for many small entries?
Store keys and values as byte arrays and not Strings (less overhead and no encoding ambiguity). Use an arena allocator, as RocksDB does, so entries are packed into large blocks, which cuts per-object overhead and GC pressure.


