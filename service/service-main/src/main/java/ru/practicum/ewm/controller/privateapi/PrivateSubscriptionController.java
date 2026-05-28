package ru.practicum.ewm.controller.privateapi;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.dto.user.UserShortDto;
import ru.practicum.ewm.service.SubscriptionService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}")
@RequiredArgsConstructor
public class PrivateSubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/subscriptions/{targetId}")
    @ResponseStatus(HttpStatus.CREATED)
    public UserShortDto subscribe(@PathVariable long userId, @PathVariable long targetId) {
        return subscriptionService.subscribe(userId, targetId);
    }

    @DeleteMapping("/subscriptions/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable long userId, @PathVariable long targetId) {
        subscriptionService.unsubscribe(userId, targetId);
    }

    @GetMapping("/subscriptions")
    public List<UserShortDto> getSubscriptions(@PathVariable long userId) {
        return subscriptionService.getSubscriptions(userId);
    }

    @GetMapping("/followers")
    public List<UserShortDto> getFollowers(@PathVariable long userId) {
        return subscriptionService.getFollowers(userId);
    }
}
