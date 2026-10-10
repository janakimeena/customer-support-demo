package com.example.booknest.host;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The MCP <b>host</b> (notes §3, §8.2): the app a person talks to.
 *
 * <p>On startup the MCP client starter launches {@code booknest-mcp.jar} as a child process
 * (stdio transport), performs the initialize handshake, and wraps every discovered tool as
 * a Spring AI {@code ToolCallback}. The host then decides what the model gets to see:
 * tool definitions, resource text, prompt messages.
 *
 * <p>Open http://localhost:8080 for the demo page.
 */
@SpringBootApplication
public class BooknestChatHostApplication {

    public static void main(String[] args) {
        SpringApplication.run(BooknestChatHostApplication.class, args);
    }
}
