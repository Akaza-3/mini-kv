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


Step 2: build a WAL

Q1. What is a write-ahead log and why does an LSM store need one?
The memtable is in memory, so a crash loses it. Before applying any write to the memtable, I append it to a log file on disk. After a crash, I replay the log from the start and rebuild the memtable. Disk gets sequential appends only, so it’s cheap.

Q2. Why log first and apply to the memtable second?
If the memtable is updated first and the process crashes before the log write, memory had a write that disk never saw. Logging first means anything that was acknowledged is already on disk. If the log write throws, the method exits before touching the memtable, so memory and disk stay in agreement.

Q3. What’s the difference between flush() and fsync(), and why does it matter?
flush() only pushes Java’s buffered bytes to the OS. The OS may still hold them in its page cache, and a power cut loses them. fsync (FileDescriptor.sync()) forces the OS to write to the physical device. A write can be acknowledged only after fsync returns. Otherwise “durable” is a false promise.

Q4. fsync on every write is slow. What are the options?

Sync every write: safest, slowest.
Group commit: batch several concurrent writes into one fsync, then acknowledge them all. This is the standard answer.
Periodic sync (for example every second): fast, but you can lose up to a second of acknowledged writes. Redis offers this as appendfsync everysec.

It’s a durability vs throughput tradeoff, and the right choice depends on whether losing the last second is acceptable.

Q5. Describe your record format. Why is each part there?
[op][keyLen][key][valLen][value][crc].

op: distinguishes PUT from DELETE.
Length prefixes: the reader needs to know where the key ends and the value begins. Raw bytes don’t say.
CRC32: detects a damaged or half-written record.

Q6. What is a torn write and how do you handle it?
A crash in the middle of a write leaves a partial record at the end of the file. On replay, reading it either hits end-of-file mid-record or fails the CRC check. I stop at that record and ignore it. That’s safe because the client was never told that write succeeded.

Q7. Why stop at a bad record instead of skipping it?
After a corrupt record I can’t trust its length fields, so I don’t know where the next record starts. Skipping risks decoding garbage as valid data. Stopping gives a clean prefix of the history. Real systems expose this as a recovery mode: RocksDB, for example, has options like tolerating a corrupted tail only, or point-in-time recovery.

Q8. There’s a subtle bug after a torn write. What is it?
A crash leaves a half record at the end. After restart, replay stops at it, but the store then appends new records after the half record. The next replay stops at the half record again, so the new, acknowledged writes are never read. They’re lost.
Fix: during recovery, track the offset where the last good record ended and truncate the file there before accepting new writes.

Q9. What if the corruption is in the middle of the file, not the tail?
Everything after it is unreachable with my approach, and truncating would delete valid records. A tail tear is expected after a crash. Mid-file corruption means disk damage, so I’d report it loudly and not silently truncate. Production systems also use per-block checksums and replicas.

Q10. Is replay idempotent? Does it matter?
Yes, replaying the same log twice gives the same memtable, because a put overwrites and a delete writes a tombstone. It matters because a crash during recovery just means recovery runs again.

Q11. When can the WAL be deleted?
Only after the memtable it covers has been flushed to an SSTable and that file is durable. After that, the WAL’s contents exist in the SSTable. Delete it earlier and a crash loses data.

Q12. Why does replay take a handler and not write to the memtable directly?
It decouples the log from the memtable. The WAL class only reads and writes log files, so I can test it with a handler that prints. The same method can serve other consumers. Also, the replay handler must write to the memtable directly. If it called the store’s own put, each replayed record would be appended to the log again.

Q13. Why guard the length fields during replay?
A corrupt length could be huge. new byte[keyLen] with a garbage value could allocate gigabytes or throw NegativeArraySizeException. I check 0 <= len <= MAX before allocating and treat a bad value as corruption.

Q14. Is the single write() call per record atomic?
No. It reduces interleaving, but a crash can still leave a partial record. The CRC is what detects that. I shouldn’t claim atomicity from one write call.

Q15. How does this WAL differ from a B-tree’s WAL?
In an LSM store, the WAL protects only the in-memory memtable, and it can be discarded after a flush. In a B-tree, pages are updated in place, and a multi-page change (such as a split) can crash halfway and leave the tree inconsistent. The WAL there is needed for redo (and undo) on every restart, and it’s tied to checkpoints.

Q16. What happens with multiple writer threads?
Appends must be serialized, or records can interleave in the file. Use a lock around append, or a single writer thread that batches records (which also enables group commit). My version isn’t thread-safe yet, and I’d say so.

Q17. What else is needed for full durability?

fsync on the directory when creating a new file, so the file’s existence survives a crash.
Handling fsync failure: on some systems, retrying after a failed fsync can falsely report success, so the safe choice is to treat it as fatal.
