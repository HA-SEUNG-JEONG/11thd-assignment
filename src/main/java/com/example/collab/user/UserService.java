package com.example.collab.user;

import com.example.collab.common.exception.ConflictException;
import com.example.collab.common.exception.NotFoundException;
import com.example.collab.user.dto.UserCreateRequest;
import com.example.collab.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /** 이메일 중복은 리소스의 현재 상태와의 충돌이므로 409. */
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        return UserResponse.from(userRepository.save(new User(request.name(), request.email())));
    }

    public UserResponse get(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
