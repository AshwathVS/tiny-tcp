package client;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Scenario-based load test. Fill the REQUESTS list with the exact traffic you want to generate.
 * Each RequestSpec is sent 'repeat' times. All requests are distributed across 'concurrency' keep-alive connections.
 * Response frame expected: [status:int][len:int][body]
 * NOTE: GPT GENERATED CODE
 */
@Slf4j
public class ScenarioLoadTester {

    // === Fill this list ===
    // Example entries are commented out. Add your own paths, headers and payloads.
    private static final List<RequestSpec> REQUESTS = List.of(
        RequestSpec.builder()
            .path("/hello")
            .headers(Map.of("Keep-Alive", "true"))
            .body(randomAsciiBytes(0, 1500))
            .repeat(10000)
            .build(),
        RequestSpec.builder()
            .path("/delay")
            .headers(Map.of("Keep-Alive", "true", "Delay", "10"))
            .body("ping".getBytes(StandardCharsets.UTF_8))
            .repeat(10000)
            .build()
    );

    public static void main(String[] args) throws Exception {
        Config cfg = new Config("127.0.0.1", 9998, 1000);
        if (REQUESTS.isEmpty()) {
            log.info("REQUESTS list is empty. Please add RequestSpec entries in ScenarioLoadTester.");
            return;
        }

        // Flatten scenario to a concurrent task queue
        Queue<RequestSpec> queue = new ConcurrentLinkedQueue<>();
        for (RequestSpec spec : REQUESTS) {
            for (int i = 0; i < spec.repeat; i++) {
                queue.add(spec);
            }
        }
        int totalPlanned = queue.size();

        ExecutorService pool = Executors.newFixedThreadPool(cfg.concurrency);
        List<Future<Result>> futures = new ArrayList<>();

        Instant t0 = Instant.now();
        for (int i = 0; i < cfg.concurrency; i++) {
            futures.add(pool.submit(new Worker(cfg, queue)));
        }

        long ok = 0, notFound = 0, err = 0;
        List<Long> latencies = new ArrayList<>();
        for (Future<Result> f : futures) {
            Result r = f.get();
            ok += r.ok;
            notFound += r.notFound;
            err += r.err;
            latencies.addAll(r.latenciesNanos);
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        Instant t1 = Instant.now();

        long durationMs = Duration.between(t0, t1).toMillis();
        double rps = durationMs == 0 ? totalPlanned : (totalPlanned * 1000.0) / durationMs;

        Collections.sort(latencies);
        long p50 = pct(latencies, 50);
        long p95 = pct(latencies, 95);
        long p99 = pct(latencies, 99);
        long min = latencies.isEmpty() ? 0 : latencies.get(0);
        long max = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);

        log.info("=== Scenario Load Test Results ===");
        log.info("Total planned: {}", totalPlanned);
        log.info("Completed:     {}", (ok + notFound + err));
        log.info("Duration:      {} ms", durationMs);
        log.info("Throughput:    {} req/s", String.format("%.2f", rps));
        log.info("Status 200:    {}", ok);
        log.info("Status 404:    {}", notFound);
        log.info("Status 500+:   {}", err);
        log.info("Latency (ms):  min={} p50={} p95={} p99={} max={}",
            TimeUnit.NANOSECONDS.toMillis(min),
            TimeUnit.NANOSECONDS.toMillis(p50),
            TimeUnit.NANOSECONDS.toMillis(p95),
            TimeUnit.NANOSECONDS.toMillis(p99),
            TimeUnit.NANOSECONDS.toMillis(max));
    }

    private static long pct(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0L;
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        idx = Math.clamp(idx, 0, sorted.size() - 1);
        return sorted.get(idx);
    }

    private static final class Worker implements Callable<Result> {
        private final Config cfg;
        private final Queue<RequestSpec> queue;

        Worker(Config cfg, Queue<RequestSpec> queue) {
            this.cfg = cfg;
            this.queue = queue;
        }

        @Override
        public Result call() {
            List<Long> latencies = new ArrayList<>();
            long ok = 0, notFound = 0, err = 0;

            try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(cfg.host, cfg.port))) {
                ch.configureBlocking(true);

                while (true) {
                    RequestSpec spec = queue.poll();
                    if (spec == null) break; // no more work

                    ByteBuffer frame = RequestEncoder.encode(spec.path, spec.headers, spec.body);
                    long s = System.nanoTime();
                    writeFully(ch, frame);
                    Response r = readResponse(ch);
                    long e = System.nanoTime();
                    latencies.add(e - s);

                    if (r.status >= 200 && r.status < 300) ok++;
                    else if (r.status == 404) notFound++;
                    else err++;
                }

            } catch (IOException e) {
                // connection failure: remaining tasks will be picked by other workers
            }

            return new Result(ok, notFound, err, latencies);
        }

        private static void writeFully(SocketChannel ch, ByteBuffer buf) throws IOException {
            while (buf.hasRemaining()) ch.write(buf);
        }

        private static Response readResponse(SocketChannel ch) throws IOException {
            ByteBuffer prefix = ByteBuffer.allocate(8);
            readFully(ch, prefix);
            prefix.flip();
            int status = prefix.getInt();
            int len = prefix.getInt();

            ByteBuffer body = ByteBuffer.allocate(len);
            readFully(ch, body);
            body.flip();
            byte[] bytes = new byte[len];
            body.get(bytes);
            return new Response(status, bytes);
        }

        private static void readFully(SocketChannel ch, ByteBuffer buf) throws IOException {
            while (buf.hasRemaining()) {
                int n = ch.read(buf);
                if (n == -1) throw new IOException("channel closed");
            }
        }
    }

    @Value
    private static class Config {
        String host;
        int port;
        int concurrency;
    }

    @Value
    @Builder
    public static class RequestSpec {
        String path;
        Map<String, String> headers;
        byte[] body;
        int repeat;

        public RequestSpec(String path, Map<String, String> headers, byte[] body, int repeat) {
            this.path = Objects.requireNonNull(path);
            this.headers = Objects.requireNonNull(headers);
            this.body = body == null ? new byte[0] : body;
            this.repeat = Math.max(1, repeat);
        }
    }

    private record Response(int status, byte[] body) {
    }

    private record Result(long ok, long notFound, long err, List<Long> latenciesNanos) {
    }

    private static byte[] randomAsciiBytes(int minLen, int maxLen) {
        int len = ThreadLocalRandom.current().nextInt(minLen, maxLen + 1);
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            // printable ASCII range 32..126
            bytes[i] = (byte) ThreadLocalRandom.current().nextInt(32, 127);
        }
        return bytes;
    }

}
