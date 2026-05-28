package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.user.NewUserRequest;
import ru.practicum.ewm.dto.user.UserDto;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.UserMapper;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.repository.UserRepository;
import ru.practicum.ewm.util.PaginationUtil;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserDto register(NewUserRequest request) {
        validateEmailLocalPart(request.getEmail());
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .build();
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public void delete(long userId) {
        getUserOrThrow(userId);
        userRepository.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        List<User> users;
        if (ids == null || ids.isEmpty()) {
            users = userRepository.findAll();
        } else {
            users = userRepository.findAllById(ids);
        }
        return PaginationUtil.paginate(
                        users.stream().sorted(Comparator.comparing(User::getId)).toList(),
                        from,
                        size
                ).stream()
                .map(userMapper::toDto)
                .toList();
    }

    public User getUserOrThrow(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private void validateEmailLocalPart(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 64) {
            throw new BadRequestException(
                    "email: длина основной части не должна превышать 64 символа. Value: " + email);
        }
    }
}
