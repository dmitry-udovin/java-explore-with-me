package ru.practicum.ewm.util;

import java.time.format.DateTimeFormatter;

public final class DateTimeFormatterConst {

    public static final DateTimeFormatter PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final DateTimeFormatter REQUEST_PATTERN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private DateTimeFormatterConst() {
    }
}
