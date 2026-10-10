package com.example.booknest;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * MCP <b>resources</b>: read-only content identified by a URI (notes §4.2, §7.6).
 * The host decides when to read them and attach them as context; the model does not call them.
 *
 * <p>The URIs are identifiers, not web addresses: nothing serves {@code booknest://} over HTTP.
 */
@Component
public class BookResources {

    private final BookCatalog catalog;

    public BookResources(BookCatalog catalog) {
        this.catalog = catalog;
    }

    @McpResource(uri = "booknest://policies/returns",
            name = "Return policy",
            description = "BookNest return and refund rules",
            mimeType = "text/plain")
    public String returnPolicy() {
        // Fictional classroom text, not a real policy.
        return """
                Books can be returned within 7 days of delivery if unused.
                Refunds go to the original payment method within 5 working days.
                E-books cannot be returned.
                """;
    }

    /**
     * A resource <i>template</i>: one URI pattern covers every book. It appears under
     * {@code resources/templates/list}; a client fills in {id} and calls {@code resources/read}.
     */
    @McpResource(uri = "booknest://books/{id}",
            name = "Book details",
            description = "Details for one book by its BookNest ID, for example booknest://books/B3",
            mimeType = "text/plain")
    public String bookDetails(String id) {
        return catalog.byId(id)
                .map(book -> """
                        ID: %s
                        Title: %s
                        Author: %s
                        Price: Rs. %s
                        """.formatted(book.id(), book.title(), book.author(), book.priceInr()))
                .orElse("No book with id " + id);
    }
}
