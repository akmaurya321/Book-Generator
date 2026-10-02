package book.example.services;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfiguration {
    @Bean(name = "documentationTaskExecutor")
    public Executor documentationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // Keep accepted jobs queued instead of failing the request when the four
        // execution threads are busy. The durable job record remains the source of truth.
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("documentation-worker-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "chapterGenerationExecutor", destroyMethod = "shutdown")
    public ExecutorService chapterGenerationExecutor(LlmWorkerPool workerPool) {
        int parallelism = Math.max(1, Math.min(4, workerPool.configuredWorkerCount()));
        return Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("chapter-generation-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }
}

