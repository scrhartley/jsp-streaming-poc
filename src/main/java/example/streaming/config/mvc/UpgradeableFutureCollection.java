package example.streaming.config.mvc;

import static java.util.Collections.*;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UpgradeableFutureCollection<T> extends AbstractCollection<Future<T>> {

    private List<UpgradeableFuture<T>> futures;
    private Collection<Future<T>> upgraded;

    UpgradeableFutureCollection(Callable<T>[] callables) {
        this.futures = Stream.of(callables)
                .map(UpgradeableFuture::new)
                .collect(Collectors.toList());
    }

    @Override
    public Iterator<Future<T>> iterator() {
        if (upgraded != null) {
            return upgraded.iterator();
        } else {
            return Collections.<Future<T>>unmodifiableList(futures).iterator();
        }
    }

    @Override
    public int size() {
        if (upgraded != null) {
            return upgraded.size();
        } else {
            return futures.size();
        }
    }

    public List<UpgradeableFuture<T>> getFuturesPreUpgrade() {
        if (futures == null) {
            throw new IllegalStateException("Attempted access after upgrade");
        }
        return futures;
    }

    public void setUpgradedFutures(List<UpgradeableFuture<T>> completed, PendingQueue<T> queue) {
        if (upgraded != null) {
            throw new IllegalStateException("Already set");
        }
        upgraded = new UpgradedCollection<>(completed, queue);
        futures = null; // Allow GC.
    }

    interface PendingQueue<T> {
        UpgradeableFuture<T> take();

        int size();
    }


    private static class UpgradedCollection<T> extends AbstractCollection<Future<T>> {
        private final List<UpgradeableFuture<T>> allCompleted;
        private final PendingQueue<T> queue;

        private UpgradedIterator activeIterator; // Only latest snapshot is valid.

        private UpgradedCollection(List<UpgradeableFuture<T>> preCompleted, PendingQueue<T> queue) {
            this.allCompleted = new ArrayList<>(preCompleted);
            this.queue = queue;
        }

        @Override
        public int size() {
            return queue.size() + allCompleted.size();
        }

        @Override
        public Iterator<Future<T>> iterator() {
            UpgradedIterator iterator = new UpgradedIterator();
            this.activeIterator = iterator;
            return iterator;
        }

        private void checkActive(UpgradedIterator iterator) {
            if (iterator != this.activeIterator) {
                throw new IllegalStateException("Attempted access of dead iterator");
            }
        }


        class UpgradedIterator implements Iterator<Future<T>> {
            final Iterator<UpgradeableFuture<T>> completedIt = allCompleted.isEmpty()
                    ? emptyIterator() : new ArrayList<>(allCompleted).iterator(); // Snapshot

            @Override
            public boolean hasNext() {
                checkActive(this);
                return completedIt.hasNext() || queue.size() > 0;
            }

            @Override
            public Future<T> next() {
                checkActive(this);
                if (completedIt.hasNext()) {
                    return completedIt.next();
                } else if (queue.size() > 0) {
                    UpgradeableFuture<T> future = queue.take();
                    allCompleted.add(future);
                    return future;
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

    }

}
