package book.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import book.example.services.LlmProperties;

@EnableScheduling
@EnableConfigurationProperties(LlmProperties.class)
@SpringBootApplication
public class BookGeneratorProjectApplication {

	public static void main(String[] args) {

		SpringApplication.run(
				BookGeneratorProjectApplication.class,
				args
		);
	}
}