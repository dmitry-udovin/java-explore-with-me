package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.event.EventFullDto;
import ru.practicum.ewm.dto.event.EventShortDto;
import ru.practicum.ewm.dto.event.NewEventDto;
import ru.practicum.ewm.dto.event.UpdateEventAdminRequest;
import ru.practicum.ewm.dto.event.UpdateEventUserRequest;
import ru.practicum.ewm.dto.event.enums.AdminStateAction;
import ru.practicum.ewm.dto.event.enums.UserStateAction;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ForbiddenOperationException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.mapper.LocationMapper;
import ru.practicum.ewm.model.Category;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.model.enums.EventState;
import ru.practicum.ewm.model.enums.RequestStatus;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.ParticipationRequestRepository;
import ru.practicum.ewm.repository.spec.EventSpecifications;
import ru.practicum.ewm.util.DateTimeFormatterConst;
import ru.practicum.ewm.util.PaginationUtil;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final String EVENTS_URI = "/events";

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final UserService userService;
    private final EventMapper eventMapper;
    private final LocationMapper locationMapper;
    private final ParticipationRequestStatsService requestStatsService;
    private final StatsService statsService;

    @Transactional
    public EventFullDto create(Long userId, NewEventDto dto) {
        User initiator = userService.getUserOrThrow(userId);
        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory() + " was not found"));
        validateEventDate(dto.getEventDate());
        Event event = eventMapper.toEntity(dto, category, initiator);
        return toFullDto(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        userService.getUserOrThrow(userId);
        List<Event> events = eventRepository.findAllByInitiatorId(userId).stream()
                .sorted(Comparator.comparing(Event::getId))
                .toList();
        return mapToShortDtos(PaginationUtil.paginate(events, from, size));
    }

    @Transactional(readOnly = true)
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        return toFullDto(getUserEventOrThrow(userId, eventId));
    }

    @Transactional
    public EventFullDto updateByUser(Long userId, Long eventId, UpdateEventUserRequest dto) {
        Event event = getUserEventOrThrow(userId, eventId);
        if (event.getState() != EventState.PENDING && event.getState() != EventState.CANCELED) {
            throw new ForbiddenOperationException("Только отклонённые/в ожидании события могут быть изменены");
        }
        applyUserUpdate(event, dto);
        if (dto.getStateAction() == UserStateAction.CANCEL_REVIEW) {
            event.setState(EventState.CANCELED);
        } else if (dto.getStateAction() == UserStateAction.SEND_TO_REVIEW) {
            event.setState(EventState.PENDING);
        }
        return toFullDto(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<EventFullDto> searchAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                          String rangeStart, String rangeEnd, int from, int size) {
        LocalDateTime start = parseDateTime(rangeStart);
        LocalDateTime end = parseDateTime(rangeEnd);
        validateDateRange(start, end);
        Specification<Event> spec = EventSpecifications.adminFilter(users, states, categories, start, end);
        List<Event> events = eventRepository.findAll(spec).stream()
                .sorted(Comparator.comparing(Event::getId))
                .toList();
        return mapToFullDtos(PaginationUtil.paginate(events, from, size));
    }

    @Transactional
    public EventFullDto updateByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = getEventOrThrow(eventId);
        applyAdminUpdate(event, dto);
        if (dto.getStateAction() == AdminStateAction.PUBLISH_EVENT) {
            publishEvent(event);
        } else if (dto.getStateAction() == AdminStateAction.REJECT_EVENT) {
            rejectEvent(event);
        }
        return toFullDto(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<EventShortDto> searchPublic(String text, List<Long> categories, Boolean paid,
                                            String rangeStart, String rangeEnd, Boolean onlyAvailable,
                                            String sort, int from, int size, HttpServletRequest request) {
        LocalDateTime start = parseDateTime(rangeStart);
        LocalDateTime end = parseDateTime(rangeEnd);
        validateDateRange(start, end);
        Specification<Event> spec = EventSpecifications.publicFilter(text, categories, paid, start, end);
        List<Event> events = eventRepository.findAll(spec).stream()
                .sorted(Comparator.comparing(Event::getId))
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        Set<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toSet());
        Map<Long, Long> confirmed = requestStatsService.getConfirmedByEventIds(eventIds);
        Map<Long, Long> views = statsService.getViewsByEventIds(eventIds);

        if (Boolean.TRUE.equals(onlyAvailable)) {
            events.removeIf(e -> !isAvailable(e, confirmed.getOrDefault(e.getId(), 0L)));
        }

        if ("VIEWS".equals(sort)) {
            events.sort(Comparator.comparing((Event e) -> views.getOrDefault(e.getId(), 0L)).reversed());
        } else {
            events.sort(Comparator.comparing(Event::getEventDate));
        }

        statsService.saveHit(request, EVENTS_URI);
        return mapToShortDtos(PaginationUtil.paginate(events, from, size), confirmed, views);
    }

    @Transactional(readOnly = true)
    public EventFullDto getPublicEvent(Long eventId, HttpServletRequest request) {
        Event event = getPublishedEventOrThrow(eventId);
        statsService.saveHit(request, EVENTS_URI + "/" + eventId);
        return toFullDto(event);
    }

    public Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    public Event getUserEventOrThrow(Long userId, Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        return event;
    }

    private Event getPublishedEventOrThrow(Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        return event;
    }

    private void publishEvent(Event event) {
        if (event.getState() != EventState.PENDING) {
            throw new ForbiddenOperationException(
                    "Невозможно опубликовать событие, поскольку у него некорректный статус: " + event.getState());
        }
        LocalDateTime publishedOn = LocalDateTime.now();
        if (event.getEventDate().isBefore(publishedOn.plusHours(1))) {
            throw new ForbiddenOperationException(
                    "Событие не может быть опубликовано раньше, чем за 1ч. перед началом");
        }
        event.setState(EventState.PUBLISHED);
        event.setPublishedOn(publishedOn);
    }

    private void rejectEvent(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ForbiddenOperationException(
                    "Событие уже опубликовано - невозможно отменить");
        }
        event.setState(EventState.CANCELED);
    }

    private void applyUserUpdate(Event event, UpdateEventUserRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            "Category with id=" + dto.getCategory() + " was not found"));
            event.setCategory(category);
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate());
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            event.setLocation(locationMapper.toEntity(dto.getLocation()));
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
    }

    private void applyAdminUpdate(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            "Category with id=" + dto.getCategory() + " was not found"));
            event.setCategory(category);
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            event.setLocation(locationMapper.toEntity(dto.getLocation()));
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
    }

    private void validateEventDate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ForbiddenOperationException(
                    "eventDate должно содержать дату, которая еще не наступила. Value: " + eventDate);
        }
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BadRequestException("Дата завершения (end) должна быть позже начала (start)");
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DateTimeFormatterConst.PATTERN);
    }

    private boolean isAvailable(Event event, long confirmed) {
        int limit = event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();
        return limit == 0 || confirmed < limit;
    }

    private EventFullDto toFullDto(Event event) {
        long confirmed = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long views = statsService.getViewsForEvent(event.getId());
        return eventMapper.toFullDto(event, confirmed, views);
    }

    private List<EventFullDto> mapToFullDtos(List<Event> events) {
        Set<Long> ids = events.stream().map(Event::getId).collect(Collectors.toSet());
        Map<Long, Long> confirmed = requestStatsService.getConfirmedByEventIds(ids);
        Map<Long, Long> views = statsService.getViewsByEventIds(ids);
        return events.stream()
                .map(e -> eventMapper.toFullDto(
                        e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    private List<EventShortDto> mapToShortDtos(List<Event> events) {
        Set<Long> ids = events.stream().map(Event::getId).collect(Collectors.toSet());
        Map<Long, Long> confirmed = requestStatsService.getConfirmedByEventIds(ids);
        Map<Long, Long> views = statsService.getViewsByEventIds(ids);
        return mapToShortDtos(events, confirmed, views);
    }

    private List<EventShortDto> mapToShortDtos(List<Event> events, Map<Long, Long> confirmed, Map<Long, Long> views) {
        return events.stream()
                .map(e -> eventMapper.toShortDto(
                        e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .toList();
    }
}
