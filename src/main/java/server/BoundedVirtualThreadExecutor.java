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

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for permit", e);
        }

        delegate.execute(() -> {
            try {
                command.run();
            } finally {
                semaphore.release();
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
