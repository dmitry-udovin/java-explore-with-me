package ru.practicum.ewm.dto.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
public class ErrorResponse {

    private final List<String> errors = Collections.emptyList();

    private final String message;

    private final String reason;

    private final String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp = LocalDateTime.now();

    public ErrorResponse(String status, String reason, String message) {
        this.status = status;
        this.reason = reason;
        this.message = message;
    }
}
