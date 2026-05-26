package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.util.Constants;
import ru.practicum.ewm.util.DateTimeFormatterConst;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final LocalDateTime STATS_START = LocalDateTime.of(2020, 1, 1, 0, 0, 0);

    private final StatsClient statsClient;

    public void saveHit(HttpServletRequest request, String uri) {
        EndpointHitDto hit = new EndpointHitDto(
                null,
                Constants.STATS_APP,
                uri,
                request.getRemoteAddr(),
                LocalDateTime.now().format(DateTimeFormatterConst.PATTERN)
        );
        statsClient.hit(hit);
    }

    public Map<Long, Long> getViewsByEventIds(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        List<String> uris = eventIds.stream()
                .map(id -> Constants.EVENT_URI_PREFIX + id)
                .toList();
        try {
            LocalDateTime end = LocalDateTime.now().plusSeconds(1);
            List<ViewStatsDto> stats = statsClient.getStats(STATS_START, end, uris, true);
            return stats.stream()
                    .filter(s -> Constants.STATS_APP.equals(s.getApp()))
                    .collect(Collectors.toMap(
                            s -> Long.parseLong(s.getUri().replace(Constants.EVENT_URI_PREFIX, "")),
                            ViewStatsDto::getHits,
                            Long::sum
                    ));
        } catch (Exception e) {
            Map<Long, Long> result = new HashMap<>();
            eventIds.forEach(id -> result.put(id, 0L));
            return result;
        }
    }

    public long getViewsForEvent(Long eventId) {
        return getViewsByEventIds(List.of(eventId)).getOrDefault(eventId, 0L);
    }
}
