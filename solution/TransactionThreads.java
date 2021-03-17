package cp1.solution;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TransactionThreads {
    /* Map that contains informations about the thread that has given (Key) id, and time of it's
       set transaction.
     */
    private ConcurrentMap<Long, Pair<Thread, Long>> threads;

    /* Map that contaions informations about transaction status(Value) of given ThreadId (Key). */
    private ConcurrentMap<Long, TransactionStatus> aktywne;

    public TransactionThreads() {
        aktywne = new ConcurrentHashMap<>();
        threads = new ConcurrentHashMap<>();
    }

    public void addThread(Long time) {
        Thread current = Thread.currentThread();

        aktywne.put(current.getId(), TransactionStatus.ACTIVE);

        threads.putIfAbsent(current.getId(), new Pair<>(current, time));
    }

    public TransactionStatus status(Long tid) {
        TransactionStatus ts = aktywne.get(tid);
        if (ts != null) {
            return ts;
        }
        else {
            return TransactionStatus.NOT_ACTIVE;
        }
    }

    /* Removes given thread */
    public void endT(Long tid) {
        threads.remove(tid);
        aktywne.remove(tid);
    }

    public void abort(Long tid) {
        aktywne.put(tid, TransactionStatus.ABORTED);
    }

    /* Method that interrupts the youngest of given Threads, and
        abort its active transaction.
     */
    public void abortGroup(Set<Long> group) {
        Pair<Thread, Long> youngest = null;

        for (Long tid : group) {
            Pair<Thread, Long> threadPair = threads.get(tid);

            if (cmp(threadPair, youngest)) {
                youngest = threadPair;
            }
        }

        assert youngest != null;

        abort(youngest.getFirst().getId());

        youngest.getFirst().interrupt();
    }

    /* Comparator for informations about thread */
    private boolean cmp(Pair<Thread, Long> thread1, Pair<Thread, Long> thread2) {
        if (thread1 == null) {
            return false;
        }

        if (thread2 == null) {
            return true;
        }

        if (thread1.getSecond() > thread2.getSecond()) {
            return true;
        }
        else if (thread1.getSecond() < thread2.getSecond()) {
            return false;
        }
        else {
            return thread1.getFirst().getId() > thread2.getFirst().getId();
        }
    }
}
