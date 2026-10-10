package com.example.booknest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * MCP <b>tools</b>: operations a model can ask the host to invoke with arguments (notes §4.1, §7.5).
 *
 * <p>Spring AI builds each tool's JSON input schema from the method signature and the
 * {@code @McpToolParam} descriptions. Those descriptions are all the model sees, so they
 * say exactly what to pass.
 */
@Component
public class BookTools {

    // Never System.out in a stdio server: stdout is the protocol channel (notes §6).
    // logback-spring.xml sends this logger to stderr.
    private static final Logger log = LoggerFactory.getLogger(BookTools.class);

    private final BookCatalog catalog;

    public BookTools(BookCatalog catalog) {
        this.catalog = catalog;
    }

    @McpTool(name = "search_books",
            description = "Search the BookNest catalogue by title or author keyword. "
                    + "Returns matching books with ID, title, author and price in INR. "
                    + "Returns an empty list when nothing matches.")
    public List<Book> searchBooks(
            @McpToolParam(description = "Title or author keyword, for example 'java' or 'bloch'", required = true)
            String query) {
        log.info("search_books query={}", query);
        return catalog.search(query);
    }

    @McpTool(name = "get_book_price",
            description = "Get the demo price of one BookNest book. The input must be the book ID "
                    + "(B1 to B5); do not pass a title. Use search_books first to find an ID.")
    public String getBookPrice(
            @McpToolParam(description = "BookNest ID such as B2. Not a title.", required = true)
            String bookId) {
        log.info("get_book_price bookId={}", bookId);
        // A missing ID is an expected outcome, so it is a normal result, not an error (notes §9).
        return catalog.byId(bookId)
                .map(book -> book.title() + " costs Rs. " + book.priceInr())
                .orElse("No book with id " + bookId + ". Use search_books to find a valid ID.");
    }

    @McpTool(name = "books_by_max_price",
            description = "List BookNest books whose price in INR is at most maxPrice (inclusive).")
    public List<Book> booksByMaxPrice(
            @McpToolParam(description = "Upper price limit in INR, zero or more, for example 600", required = true)
            BigDecimal maxPrice) {
        log.info("books_by_max_price maxPrice={}", maxPrice);
        // Invalid input throws. Spring AI turns the exception into a tool result with isError=true,
        // which is the other way to report failure (compare with get_book_price above).
        return catalog.atMost(maxPrice);
    }
}
