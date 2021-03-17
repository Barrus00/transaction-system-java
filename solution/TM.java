package cp1.solution;

import cp1.base.*;

import java.util.*;
import java.util.concurrent.*;

public class TM implements TransactionManager {

    private ConcurrentMap<ResourceId, Resource> resources;

    /* Map that contains Stack (value) of all the operations that were performed on given (Key) resource.
       (In actual transactions).
     */
    private ConcurrentMap<ResourceId, Stack<ResourceOperation>> operations;

    private LocalTimeProvider timeProvider;

    private TransactionsInfo transactionsInfo;

    public TM(Collection<Resource> resources,
              LocalTimeProvider timeProvider) {
        this.resources = new ConcurrentHashMap<>();
        transactionsInfo = new TransactionsInfo(resources);
        operations = new ConcurrentHashMap<>();

        this.timeProvider = timeProvider;

        for (Resource res : resources) {
            this.resources.put(res.getId(), res);
            operations.put(res.getId(), new Stack<>());
        }
    }

    @Override
    public synchronized void startTransaction() throws AnotherTransactionActiveException {
        long startT = timeProvider.getTime();
        long myId = Thread.currentThread().getId();

        if (transactionsInfo.status(myId) == TransactionStatus.NOT_ACTIVE) {
            transactionsInfo.start(startT);
        } else {
            throw new AnotherTransactionActiveException();
        }
    }

    @Override
    public void operateOnResourceInCurrentTransaction(ResourceId rid, ResourceOperation operation) throws NoActiveTransactionException, UnknownResourceIdException, ActiveTransactionAborted, ResourceOperationException, InterruptedException {
        long myId = Thread.currentThread().getId();

        switch (transactionsInfo.status(myId)){
            case NOT_ACTIVE:
                throw new NoActiveTransactionException();
            case ABORTED:
                throw new ActiveTransactionAborted();
        }

        if (!resources.containsKey(rid)) {
            throw new UnknownResourceIdException(rid);
        }

        try {
            transactionsInfo.acquire(rid);
        }
        catch (InterruptedException e) {
            Thread.interrupted();

            if (isTransactionAborted()) {
                throw new ActiveTransactionAborted();
            }

            throw new InterruptedException();
        }

        operations.putIfAbsent(rid, new Stack<>());
        operation.execute(resources.get(rid));

        /* If current thread was interrupted during or after executing an operation
           then it should undo the action, and throw InterruptedException.
           I'm not sure if that's correct, but I assumed that we should do that, because
           the moodle forum suggest that.
         */
        if(Thread.currentThread().isInterrupted()) {
            operation.undo(resources.get(rid));
            Thread.interrupted();

            throw new InterruptedException();
        }

        /* We push the operation only if it was performed without any errors */
        operations.get(rid).push(operation);
    }

    /* Removes all the operations that was performed on all the resources that current thread own. */
    private void commitOperations() {
        for (ResourceId rid : transactionsInfo.getResources()) {
            operations.remove(rid);
        }
    }

    @Override
    public void commitCurrentTransaction() throws NoActiveTransactionException, ActiveTransactionAborted {
        long myId = Thread.currentThread().getId();

        switch (transactionsInfo.status(myId)) {
            case ABORTED:
                throw new ActiveTransactionAborted();
            case NOT_ACTIVE:
                throw new NoActiveTransactionException();
            case ACTIVE:
                commitOperations();
                endT();
        }
    }

    @Override
    public void rollbackCurrentTransaction() {
        long myId = Thread.currentThread().getId();

        if (transactionsInfo.status(myId) != TransactionStatus.NOT_ACTIVE) {
            //If there are any operations that were performed on the resource, then we should undo them.
            for (ResourceId rid : transactionsInfo.getResources()) {
                Stack<ResourceOperation> stack = operations.get(rid);
                Resource zasob = resources.get(rid);

                while (!stack.empty()){
                    stack.pop().undo(zasob);
                }
            }

            endT();
        }
    }

    public void endT() {
        transactionsInfo.end();
    }

    @Override
    public boolean isTransactionActive() {
        long myId = Thread.currentThread().getId();

        return transactionsInfo.status(myId) != TransactionStatus.NOT_ACTIVE;
    }

    @Override
    public boolean isTransactionAborted() {
        long myId = Thread.currentThread().getId();

        return transactionsInfo.status(myId) == TransactionStatus.ABORTED;
    }
}
