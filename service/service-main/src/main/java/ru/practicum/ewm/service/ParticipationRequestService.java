package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.event.enums.RequestStatusAction;
import ru.practicum.ewm.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.ewm.dto.request.ParticipationRequestDto;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.ForbiddenOperationException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.ParticipationRequest;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.model.enums.EventState;
import ru.practicum.ewm.model.enums.RequestStatus;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserService userService;
    private final EventRepository eventRepository;
    private final RequestMapper requestMapper;

    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        userService.getUserOrThrow(userId);
        return requestRepository.findAllByRequesterId(userId).stream()
                .map(requestMapper::toDto)
                .toList();
    }

    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId) {
        User requester = userService.getUserOrThrow(userId);
        Event event = getEventOrThrow(eventId);

        if (event.getInitiator().getId().equals(userId)) {
            throw new ForbiddenOperationException("Инициатор события не может отправить приглашение самому себе");
        }
        if (event.getState() != EventState.PUBLISHED) {
            throw new ForbiddenOperationException("Невозможно участвовать в неопубликованном событии");
        }
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Запрос на участие уже существует");
        }

        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        int limit = event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();
        if (limit > 0 && confirmed >= limit) {
            throw new ForbiddenOperationException("Лимит участников был достигнут");
        }

        RequestStatus status = Boolean.FALSE.equals(event.getRequestModeration())
                ? RequestStatus.CONFIRMED
                : RequestStatus.PENDING;

        ParticipationRequest request = ParticipationRequest.builder()
                .created(LocalDateTime.now())
                .event(event)
                .requester(requester)
                .status(status)
                .build();
        return requestMapper.toDto(requestRepository.save(request));
    }

    @Transactional
    public ParticipationRequestDto cancel(Long userId, Long requestId) {
        userService.getUserOrThrow(userId);
        ParticipationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " was not found"));
        if (!request.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Request with id=" + requestId + " was not found");
        }
        request.setStatus(RequestStatus.CANCELED);
        return requestMapper.toDto(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        getUserEventOrThrow(userId, eventId);
        return requestRepository.findAllByEventId(eventId).stream()
                .map(requestMapper::toDto)
                .toList();
    }

    @Transactional
    public EventRequestStatusUpdateResult changeStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest dto) {
        Event event = getUserEventOrThrow(userId, eventId);
        List<ParticipationRequest> requests = requestRepository.findAllByIdInAndEventId(
                dto.getRequestIds(), eventId);

        if (requests.size() != dto.getRequestIds().size()) {
            throw new NotFoundException("Request was not found");
        }
        if (requests.stream().anyMatch(r -> r.getStatus() != RequestStatus.PENDING)) {
            throw new ConflictException("Request must have status PENDING");
        }

        long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        int limit = event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        if (dto.getStatus() == RequestStatusAction.CONFIRMED) {
            for (ParticipationRequest request : requests) {
                if (limit > 0 && confirmedCount >= limit) {
                    throw new ConflictException("Лимит участников был достигнут");
                }
                request.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(requestMapper.toDto(requestRepository.save(request)));
                confirmedCount++;
            }
            if (limit > 0 && confirmedCount >= limit) {
                rejectRemainingPending(eventId, rejected);
            }
        } else {
            for (ParticipationRequest request : requests) {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(requestMapper.toDto(requestRepository.save(request)));
            }
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }

    private Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private Event getUserEventOrThrow(Long userId, Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        return event;
    }

    private void rejectRemainingPending(Long eventId, List<ParticipationRequestDto> rejected) {
        List<ParticipationRequest> pending = requestRepository.findAllByEventId(eventId).stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING)
                .toList();
        for (ParticipationRequest request : pending) {
            request.setStatus(RequestStatus.REJECTED);
            rejected.add(requestMapper.toDto(requestRepository.save(request)));
        }
    }
}
