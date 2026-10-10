package com.example.booknest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tool methods are plain Java, so they can be tested without starting an MCP server. */
class BookToolsTest {

    private final BookTools tools = new BookTools(new BookCatalog());

    @Test
    void priceForKnownId() {
        assertThat(tools.getBookPrice("B2")).isEqualTo("Head First Java costs Rs. 599.00");
    }

    @Test
    void unknownIdIsAControlledResultNotAnException() {
        assertThat(tools.getBookPrice("B9")).startsWith("No book with id B9");
    }
}
