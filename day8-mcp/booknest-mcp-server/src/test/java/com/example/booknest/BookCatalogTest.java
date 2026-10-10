package com.example.booknest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BookCatalogTest {

    private final BookCatalog catalog = new BookCatalog();

    @Test
    void searchMatchesTitleOrAuthorIgnoringCase() {
        assertThat(catalog.search("JAVA")).extracting(Book::id).containsExactly("B2", "B3", "B4");
        assertThat(catalog.search("bloch")).extracting(Book::id).containsExactly("B3");
    }

    @Test
    void blankSearchMatchesNothing() {
        assertThat(catalog.search("  ")).isEmpty();
        assertThat(catalog.search(null)).isEmpty();
    }

    @Test
    void byIdIgnoresCaseAndReportsMissingBooks() {
        assertThat(catalog.byId("b2")).map(Book::title).contains("Head First Java");
        assertThat(catalog.byId("B9")).isEmpty();
    }

    @Test
    void maxPriceIsInclusive() {
        // Boundary: exactly the price of B2.
        assertThat(catalog.atMost(new BigDecimal("599"))).extracting(Book::id).containsExactly("B1", "B2");
        assertThat(catalog.atMost(BigDecimal.ZERO)).isEmpty();
    }

    @Test
    void negativeOrMissingMaxPriceIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> catalog.atMost(new BigDecimal("-1")));
        assertThatIllegalArgumentException().isThrownBy(() -> catalog.atMost(null));
    }
}
