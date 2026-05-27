package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.compilation.CompilationDto;
import ru.practicum.ewm.dto.compilation.NewCompilationDto;
import ru.practicum.ewm.dto.compilation.UpdateCompilationRequest;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CompilationMapper;
import ru.practicum.ewm.model.Compilation;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.enums.EventState;
import ru.practicum.ewm.repository.CompilationRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.util.PaginationUtil;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;
    private final ParticipationRequestStatsService requestStatsService;
    private final StatsService statsService;

    @Transactional
    public CompilationDto create(NewCompilationDto dto) {
        Set<Event> events = resolveEvents(dto.getEvents());
        Compilation compilation = compilationMapper.toEntity(dto, events);
        return toDto(compilationRepository.save(compilation));
    }

    @Transactional
    public CompilationDto update(long compId, UpdateCompilationRequest dto) {
        Compilation compilation = getCompilationOrThrow(compId);
        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }
        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }
        if (dto.getEvents() != null) {
            compilation.setEvents(resolvePublishedEvents(dto.getEvents()));
        }
        return toDto(compilationRepository.save(compilation));
    }

    @Transactional
    public void delete(long compId) {
        getCompilationOrThrow(compId);
        compilationRepository.deleteById(compId);
    }

    @Transactional(readOnly = true)
    public CompilationDto getById(long compId) {
        return toDto(getCompilationOrThrow(compId));
    }

    @Transactional(readOnly = true)
    public List<CompilationDto> getAll(Boolean pinned, int from, int size) {
        List<Compilation> compilations;
        if (pinned == null) {
            compilations = compilationRepository.findAll();
        } else {
            compilations = compilationRepository.findAllByPinned(pinned);
        }
        return PaginationUtil.paginate(
                        compilations.stream().sorted(Comparator.comparing(Compilation::getId)).toList(),
                        from,
                        size
                ).stream()
                .map(this::toDto)
                .toList();
    }

    private CompilationDto toDto(Compilation compilation) {
        Set<Long> eventIds = compilation.getEvents() == null
                ? Set.of()
                : compilation.getEvents().stream().map(Event::getId).collect(Collectors.toSet());
        Map<Long, Long> confirmed = requestStatsService.getConfirmedByEventIds(eventIds);
        Map<Long, Long> views = statsService.getViewsByEventIds(eventIds);
        return compilationMapper.toDto(compilation, confirmed, views);
    }

    private Set<Event> resolveEvents(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Event> events = eventRepository.findAllById(eventIds);
        if (events.size() != eventIds.size()) {
            throw new BadRequestException("Не все события были найдены");
        }
        return new HashSet<>(events);
    }

    private Set<Event> resolvePublishedEvents(Set<Long> eventIds) {
        Set<Event> events = resolveEvents(eventIds);
        boolean allPublished = events.stream().allMatch(e -> e.getState() == EventState.PUBLISHED);
        if (!allPublished) {
            throw new BadRequestException("Только опубликованные события могут быть добавлены в подборку");
        }
        return events;
    }

    private Compilation getCompilationOrThrow(long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
    }
}
