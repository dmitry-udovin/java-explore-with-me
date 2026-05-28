package ru.practicum.ewm.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.ewm.model.enums.RequestStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipationRequestDto {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private java.time.LocalDateTime created;

    private Long event;

    private Long id;

    private Long requester;

    private RequestStatus status;
}
