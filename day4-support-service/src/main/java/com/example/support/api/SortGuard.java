package com.example.support.api;

import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Allow-lists the {@code ?sort=} properties a client may use. Without it a client could sort by any entity
 * attribute, including ones without an index (slow full scans) or ones we never meant to expose.
 */
final class SortGuard {

    private SortGuard() {
    }

    static Pageable requireSortable(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), allowed);
            }
        }
        return pageable;
    }
}
