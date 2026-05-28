package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.user.UserShortDto;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.UserMapper;
import ru.practicum.ewm.model.Subscription;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;
    private final UserMapper userMapper;

    @Transactional
    public UserShortDto subscribe(long userId, long targetId) {
        if (userId == targetId) {
            throw new BadRequestException("Нельзя подписаться на самого себя");
        }

        User follower = userService.getUserOrThrow(userId);
        User followee = userService.getUserOrThrow(targetId);

        if (subscriptionRepository.existsByFollowerIdAndFolloweeId(userId, targetId)) {
            throw new ConflictException("Подписка уже существует");
        }

        Subscription subscription = Subscription.builder()
                .follower(follower)
                .followee(followee)
                .created(LocalDateTime.now())
                .build();
        subscriptionRepository.save(subscription);

        return userMapper.toShortDto(followee);
    }

    @Transactional
    public void unsubscribe(long userId, long targetId) {
        userService.getUserOrThrow(userId);
        userService.getUserOrThrow(targetId);

        Subscription subscription = subscriptionRepository.findByFollowerIdAndFolloweeId(userId, targetId)
                .orElseThrow(() -> new NotFoundException(
                        "Подписка пользователя=" + userId + " на =" + targetId + " не была найдена"));

        subscriptionRepository.delete(subscription);
    }

    @Transactional(readOnly = true)
    public List<UserShortDto> getSubscriptions(long userId) {
        userService.getUserOrThrow(userId);
        return subscriptionRepository.findAllByFollowerId(userId).stream()
                .map(Subscription::getFollowee)
                .map(userMapper::toShortDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserShortDto> getFollowers(long userId) {
        userService.getUserOrThrow(userId);
        return subscriptionRepository.findAllByFolloweeId(userId).stream()
                .map(Subscription::getFollower)
                .map(userMapper::toShortDto)
                .toList();
    }
}
