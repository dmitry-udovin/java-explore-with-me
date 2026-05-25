package ru.practicum.ewm.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.dto.compilation.CompilationDto;
import ru.practicum.ewm.dto.compilation.NewCompilationDto;
import ru.practicum.ewm.dto.event.EventShortDto;
import ru.practicum.ewm.model.Compilation;
import ru.practicum.ewm.model.Event;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompilationMapper {

    private final EventMapper eventMapper;

    public CompilationDto toDto(Compilation compilation, Map<Long, Long> confirmedByEvent,
                                Map<Long, Long> viewsByEvent) {
        Set<EventShortDto> events = compilation.getEvents() == null
                ? Collections.emptySet()
                : compilation.getEvents().stream()
                .map(event -> eventMapper.toShortDto(
                        event,
                        confirmedByEvent.getOrDefault(event.getId(), 0L),
                        viewsByEvent.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toSet());
        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.getPinned())
                .title(compilation.getTitle())
                .events(events)
                .build();
    }

    public Compilation toEntity(NewCompilationDto dto, Set<Event> events) {
        return Compilation.builder()
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .title(dto.getTitle())
                .events(events)
                .build();
    }
}
