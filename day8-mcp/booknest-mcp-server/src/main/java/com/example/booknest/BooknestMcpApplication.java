package com.example.booknest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BookNest MCP server (notes §7).
 *
 * <p>No web server starts. The MCP starter reads JSON-RPC messages from stdin and writes
 * responses to stdout, so the process "hangs" when run directly: it is waiting for a host.
 * The {@code @Mcp*} beans in this package are found by component scanning and advertised
 * through {@code tools/list}, {@code resources/list} and {@code prompts/list}.
 */
@SpringBootApplication
public class BooknestMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(BooknestMcpApplication.class, args);
    }
}
