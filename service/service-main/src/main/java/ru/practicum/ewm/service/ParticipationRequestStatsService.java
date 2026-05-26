package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.model.enums.RequestStatus;
import ru.practicum.ewm.repository.ParticipationRequestRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParticipationRequestStatsService {

    private final ParticipationRequestRepository requestRepository;

    public Map<Long, Long> getConfirmedByEventIds(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        eventIds.forEach(id -> result.put(id, 0L));
        List<Object[]> rows = requestRepository.countConfirmedByEventIds(eventIds, RequestStatus.CONFIRMED);
        for (Object[] row : rows) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    public long getConfirmedForEvent(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }
}
