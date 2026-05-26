package ru.practicum.ewm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.model.ParticipationRequest;
import ru.practicum.ewm.model.enums.RequestStatus;

import java.util.Collection;
import java.util.List;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    boolean existsByEventIdAndRequesterId(Long eventId, Long requesterId);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    List<ParticipationRequest> findAllByIdInAndEventId(Collection<Long> ids, Long eventId);

    @Query("""
            SELECT r.event.id, COUNT(r)
            FROM ParticipationRequest r
            WHERE r.event.id IN :eventIds AND r.status = :status
            GROUP BY r.event.id
            """)
    List<Object[]> countConfirmedByEventIds(@Param("eventIds") Collection<Long> eventIds,
                                            @Param("status") RequestStatus status);
}
