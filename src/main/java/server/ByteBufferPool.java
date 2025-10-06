package server;

import lombok.Getter;
import model.ApplicationConfig;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ByteBufferPool {
    private static ByteBufferPool instance;

    public static ByteBufferPool getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ByteBufferPool has not been initialized");
        }
        return instance;
    }

    public static synchronized void initialise(ApplicationConfig applicationConfig) {
        if (instance != null) {
            throw new IllegalStateException("ByteBufferPool is already initialized");
        }

        instance = new ByteBufferPool(applicationConfig);
    }

    private final LinkedBlockingQueue<ByteBuffer> byteBuffers;

    private final int maxPoolSize;

    @Getter
    private final int bufferSize;

    private final AtomicInteger totalBufferCreated = new AtomicInteger();

    private ByteBufferPool(ApplicationConfig config) {
        this.bufferSize = config.byteBufferProperties().bufferSize();
        this.maxPoolSize = config.byteBufferProperties().maxPoolSize();

        byteBuffers = new LinkedBlockingQueue<>(this.maxPoolSize);

        int poolSize = config.byteBufferProperties().minPoolSize();
        while (poolSize-- > 0) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
            byteBuffers.add(byteBuffer);
            totalBufferCreated.incrementAndGet();
        }
    }

    public ByteBuffer get() {
        var byteBuffer = byteBuffers.poll();
        if (byteBuffer != null) {
            return byteBuffer;
        }

        if (totalBufferCreated.get() < maxPoolSize) {
            synchronized (this) {
                if (totalBufferCreated.get() < maxPoolSize) {
                    totalBufferCreated.incrementAndGet();
                    return ByteBuffer.allocate(bufferSize);
                }
            }
        }

        try {
            return byteBuffers.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("java:S899")
    public void returnBuffer(ByteBuffer byteBuffer) {
        byteBuffer.clear();
        byteBuffers.offer(byteBuffer);
    }
}
