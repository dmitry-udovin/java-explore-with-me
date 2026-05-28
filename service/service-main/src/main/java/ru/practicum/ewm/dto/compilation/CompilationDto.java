package ru.practicum.ewm.dto.compilation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.ewm.dto.event.EventShortDto;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationDto {

    private Set<EventShortDto> events;

    private Long id;

    private Boolean pinned;

    private String title;
}
