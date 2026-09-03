package com.example.foxaiagent;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
public class FoxAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoxAiAgentApplication.class, args);
    }

}
