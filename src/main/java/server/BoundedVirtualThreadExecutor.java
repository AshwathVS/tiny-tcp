package server;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class BoundedVirtualThreadExecutor extends AbstractExecutorService {
    private final ExecutorService delegate;
    private final Semaphore semaphore;
    private volatile boolean isShutdown = false;

    public BoundedVirtualThreadExecutor(int maxConcurrentTasks) {
        this.delegate = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        this.semaphore = new Semaphore(maxConcurrentTasks);
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) throw new RejectedExecutionException("Executor is shut down");

        // Do not block the caller (often an I/O completion thread).
        // Instead, submit a virtual thread that acquires the permit and runs the task.
        delegate.execute(() -> {
            boolean acquired = false;
            try {
                semaphore.acquire();
                acquired = true;
                command.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for permit", e);
            } finally {
                if (acquired) semaphore.release();
            }
        });
    }

    public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, this);
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        isShutdown = true;
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
