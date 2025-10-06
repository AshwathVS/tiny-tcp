package server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import model.ApplicationConfig;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
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
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    log.debug("ByteBufferPool Size: {}", instance.byteBuffers.size());
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }).start();
    }

    private final LinkedBlockingQueue<ByteBuffer> byteBuffers;

    private final int maxPoolSize;

    @Getter
    private final int bufferSize;

    private final AtomicInteger totalBufferCreated = new AtomicInteger();

    private ByteBufferPool(ApplicationConfig config) {
        this.bufferSize = config.byteBufferProperties().bufferSize();
        this.maxPoolSize = (int) (config.byteBufferProperties().maxPoolSize() * 1.25); // keeping a 25% extra for request spike

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
            synchronized (byteBuffers) {
                if (totalBufferCreated.get() < maxPoolSize) {
                    totalBufferCreated.incrementAndGet();
                    return ByteBuffer.allocate(bufferSize);
                }
            }
        }

        // Do NOT block I/O completion threads here; allocate a temporary buffer.
        // It will be GC'ed if the pool is full upon returnBuffer().
        return ByteBuffer.allocate(bufferSize);
    }

    @SuppressWarnings("java:S899")
    public void returnBuffer(ByteBuffer byteBuffer) {
        byteBuffer.clear();
        byteBuffers.offer(byteBuffer);
    }
}
