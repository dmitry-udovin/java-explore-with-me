package ru.practicum.ewm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.model.Subscription;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByFollowerIdAndFolloweeId(long followerId, long followeeId);

    Optional<Subscription> findByFollowerIdAndFolloweeId(long followerId, long followeeId);

    List<Subscription> findAllByFollowerId(long followerId);

    List<Subscription> findAllByFolloweeId(long followeeId);
}
