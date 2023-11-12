package example.streaming;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import example.streaming.util.future.LazyDirectExecutorService;

import java.util.concurrent.ExecutorService;

@Configuration
public class FuturesConfig {

    @Bean
    public ExecutorService blockingExecutorService() {
        return new LazyDirectExecutorService();
    }

}
