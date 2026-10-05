package edu.rag;

import edu.rag.ingest.IngestionService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }

    // Section 4.2: ingest once. The isEmpty() guard prevents duplicate chunks on every restart.
    @Bean
    ApplicationRunner ingestOnStartup(IngestionService ingestion) {
        return args -> {
            if (ingestion.isEmpty()) {
                int n = ingestion.ingest();
                System.out.printf("Ingested %d chunks%n", n);
            }
        };
    }
}
