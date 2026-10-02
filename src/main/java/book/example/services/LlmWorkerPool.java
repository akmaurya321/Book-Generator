package book.example.services;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LlmWorkerPool {
    private static final Logger log = LoggerFactory.getLogger(LlmWorkerPool.class);
    public enum State { AVAILABLE, BUSY, UNAVAILABLE, RECOVERING }

    private static final class Worker {
        final ConfiguredLlmProvider provider;
        final AtomicReference<State> state = new AtomicReference<>(State.AVAILABLE);
        Worker(ConfiguredLlmProvider provider) { this.provider = provider; }
    }

    private final BlockingQueue<Worker> readyWorkers = new LinkedBlockingQueue<>();
    private final List<Worker> workers = new ArrayList<>();
    private final ScheduledExecutorService recoveryExecutor = Executors.newScheduledThreadPool(1);
    private final LlmProperties properties;

    public LlmWorkerPool(LlmProperties properties, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.properties = properties;
        for (LlmProperties.Provider config : properties.getProviders()) {
            config.validate();
        }
        List<LlmProperties.Provider> configs = properties.getProviders().stream()
                .filter(LlmProperties.Provider::isConfigured)
                .toList();
        if (configs.isEmpty()) {
            LlmProperties.Provider fallback = new LlmProperties.Provider();
            fallback.setName("legacy-primary");
            fallback.setProvider(aiProperties.getProvider());
            fallback.setModel(aiProperties.getChatModel());
            fallback.setBaseUrl(aiProperties.getBaseUrl());
            fallback.setApiKey(aiProperties.getApiKey());
            fallback.setEnabled(true);
            fallback.validate();
            configs = List.of(fallback);
        }
        for (LlmProperties.Provider config : configs) {
            Worker worker = new Worker(new ConfiguredLlmProvider(config, objectMapper,
                    properties.getConnectionTimeoutMs(), properties.getReadTimeoutMs()));
            workers.add(worker);
            readyWorkers.offer(worker);
        }
    }

    public String generate(String prompt) {
        Worker worker = takeWorker();
        worker.state.set(State.BUSY);
        try {
            return worker.provider.generate(prompt);
        } catch (RuntimeException failure) {
            if (failure instanceof LlmProviderException lpe && lpe.isTransientFailure()) {
                markUnavailable(worker);
            } else {
                releaseWorker(worker);
            }
            throw failure;
        } finally {
            if (worker.state.get() == State.BUSY) {
                worker.state.set(State.AVAILABLE);
                readyWorkers.offer(worker);
            }
        }
    }

    public String generateWithRetry(String prompt) {
        return generateWithRetry(prompt, null);
    }

    public String generateWithRetry(String prompt, java.util.Map<String, Object> responseSchema) {
        RuntimeException last = null;
        int attempts = Math.max(1, properties.getMaxProviderAttempts());
        Worker worker = null;
        int attemptsOnWorker = 0;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (worker == null) {
                worker = takeWorker();
                worker.state.set(State.BUSY);
                attemptsOnWorker = 0;
            }
            attemptsOnWorker++;
            long started = System.nanoTime();
            log.debug("LLM generation attempt={} worker={} attemptOnWorker={}", attempt, worker.provider.getName(), attemptsOnWorker);
            try {
                String result = worker.provider.generate(prompt, responseSchema);
                log.debug("LLM generation success worker={} durationMs={}", worker.provider.getName(), (System.nanoTime() - started) / 1_000_000);
                releaseWorker(worker);
                worker = null;
                return result;
            } catch (RuntimeException e) {
                last = e;
                log.warn("LLM generation failed worker={} attempt={} durationMs={} reason={}", worker.provider.getName(), attempt, (System.nanoTime() - started) / 1_000_000, e.getMessage());
                boolean transientFailure =
                        e instanceof LlmProviderException providerException
                                && providerException.isTransientFailure();
                if (!transientFailure) {
                    releaseWorker(worker);
                    throw e;
                }
                if (attempt == attempts) {
                    markUnavailable(worker);
                    worker = null;
                    break;
                }
                // Keep retries for the active generation attempt on the same worker.
                // After one worker has failed an attempt, release it and requeue the
                // chapter so the next attempt may use another healthy worker.
                if (attemptsOnWorker >= 2) {
                    markUnavailable(worker);
                    worker = null;
                }
                try {
                    Thread.sleep(Math.max(0, properties.getRetryBackoffMs()) * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    if (worker != null) releaseWorker(worker);
                    throw e;
                }
            }
        }
        throw last == null ? new IllegalStateException("No LLM provider available") : last;
    }

    private void releaseWorker(Worker worker) {
        if (worker != null && worker.state.get() == State.BUSY) {
            worker.state.set(State.AVAILABLE);
            readyWorkers.offer(worker);
        }
    }

    private Worker takeWorker() {
        try {
            // Do not fail a healthy queued chapter merely because every provider is
            // temporarily busy. A worker is returned when its current request finishes,
            // and failed workers are reintroduced by the cooldown recovery task.
            return readyWorkers.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an LLM worker", e);
        }
    }

    private void markUnavailable(Worker worker) {
        worker.state.set(State.UNAVAILABLE);
        recoveryExecutor.schedule(() -> {
            worker.state.set(State.RECOVERING);
            worker.state.set(State.AVAILABLE);
            readyWorkers.offer(worker);
        }, Math.max(1000, properties.getProviderCooldownMs()), TimeUnit.MILLISECONDS);
    }

    public List<String> providerStates() {
        return workers.stream().map(w -> w.provider.getName() + "=" + w.state.get()).toList();
    }

    public int availableWorkers() { return (int) workers.stream().filter(w -> w.state.get() == State.AVAILABLE).count(); }

    public int configuredWorkerCount() { return workers.size(); }

    @PreDestroy
    void shutdown() {
        recoveryExecutor.shutdownNow();
    }
}
