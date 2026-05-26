package ru.practicum.ewm.util;

import java.util.Collections;
import java.util.List;

public final class PaginationUtil {

    private PaginationUtil() {
    }

    public static <T> List<T> paginate(List<T> source, int from, int size) {
        if (from >= source.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + size, source.size());
        return source.subList(from, to);
    }
}
