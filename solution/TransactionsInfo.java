package cp1.solution;

import cp1.base.Resource;
import cp1.base.ResourceId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransactionsInfo {
    /* A map that represents usage of resources, if there exitst key ResourceId, then
       value that's bined with it, is the id of Thread that owns this Resource.
     */
    private ConcurrentMap<ResourceId, Long> busy;

    /* Synchronizing locks, that prevent multiple threads from owning
       the same resource.
     */
    private ConcurrentMap<ResourceId, Lock> sync;

    /* Mutex that protects editing the map */
    private Lock mutex;

    /* A map that representing waiting graph, Long value represents Thread ID,
       and the ResourceId binded with it, is the resource that this thread want to obtain.
     */
    private ConcurrentMap<Long, ResourceId> waiting;

    /* Object that contains necessary info about Threads. */
    private TransactionThreads threads;

    public TransactionsInfo(Collection<Resource> resources) {
        busy = new ConcurrentHashMap<>();
        sync = new ConcurrentHashMap<>();
        mutex = new ReentrantLock(true);
        waiting = new ConcurrentHashMap<>();
        threads = new TransactionThreads();

        for (Resource res : resources) {
            sync.put(res.getId(), new ReentrantLock(true));
        }
    }

    public void start(Long time) {
        threads.addThread(time);
    }


    /* Function that search for possible Deadlock,
       if current Thread tries to obtain resource rid
       then if it would cause a Deadlock, this function returns
       a Set of ThreadId, that contains id's of deadlocked threads.
       If the return value is null, then this thread can wait for the resource
       without any special action needed.
     */
    public Set<Long> searchForLoop(ResourceId rid) {
        Set<ResourceId> group = new HashSet<>();
        Set<Long> threadsGroup = new HashSet<>();
        Long myId = Thread.currentThread().getId();
        ResourceId wanted = rid;
        Long tid = busy.get(wanted);
        threadsGroup.add(myId);

        while (wanted != null) {
            if (group.contains(wanted)) {
                return threadsGroup;
            }

            tid = busy.get(wanted);

            if (tid == null || status(tid) == TransactionStatus.ABORTED)
                return null;

            group.add(wanted);
            threadsGroup.add(tid);

            wanted = waiting.get(tid);
        }

        if (tid != null && tid.equals(Thread.currentThread().getId())) {
            return threadsGroup;
        }
        else {
            return null;
        }
    }

    public TransactionStatus status(Long tid) {
        return threads.status(tid);
    }

    /* Tries to acquire resource rid, returns if the thread succeeded, and if there was an error,
       throws InterruptedException. If resource is being used, and if thread can wait without causing a deadlock,
       then it would wait here...
     */
    public void acquire(ResourceId rid) throws InterruptedException {
        Lock myLock = sync.get(rid);
        long myId = Thread.currentThread().getId();

        try {
            mutex.lock();

            if (busy.containsKey(rid) && busy.get(rid) == myId)
                return;

            Set<Long> loopGroup = searchForLoop(rid);

            if (loopGroup != null) {
                threads.abortGroup(loopGroup);
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            waiting.put(myId, rid);
        }
        finally {
            mutex.unlock();
        }

        try {
            myLock.lockInterruptibly();
        }
        finally {
            try {
                mutex.lock();
            }
            finally {
                waiting.remove(myId);
                mutex.unlock();
            }
        }

        busy.putIfAbsent(rid, myId);
    }

    /* Resolves all the resources that are binded with current Thread */
    public void end() {
        Long myId = Thread.currentThread().getId();

        try {
            mutex.lock();

            for (ResourceId rid : busy.keySet()) {
                if (busy.get(rid).equals(myId)) {
                    sync.get(rid).unlock();
                    busy.remove(rid);
                }
            }

            threads.endT(myId);
        }
        finally {
            mutex.unlock();
        }
    }

    /* Returns a list of ResourceId, that contains every ResourceId that this thread uses */
    public List<ResourceId> getResources() {
        Long myId = Thread.currentThread().getId();
        List<ResourceId> out = new ArrayList<>();

        for (ResourceId rid : busy.keySet()) {
            Long owner = busy.get(rid);

            if (owner != null && owner.equals(myId)) {
                out.add(rid);
            }
        }

        return out;
    }
}
