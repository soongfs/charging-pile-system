package com.bupt.charging.service;

import com.bupt.charging.dto.RegisterUserRequest;
import com.bupt.charging.entity.User;
import com.bupt.charging.enums.UserState;
import com.bupt.charging.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder;

    public UserService(UserMapper userMapper, BCryptPasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    public int createAccount(RegisterUserRequest request) {
        if (request == null) return 1;
        return createAccount(request.carId(), request.userName(), request.carCapacity());
    }

    public int createAccount(String carId, String userName, BigDecimal carCapacity) {
        if (isBlank(carId) || isBlank(userName) || isInvalidCapacity(carCapacity)) return 1;
        User existing = userMapper.findByCarId(carId);
        if (existing != null) return 1; // already exists

        User user = new User(carId.trim(), userName.trim(), carCapacity);
        int rows = userMapper.insert(user);
        return rows > 0 ? 0 : 1;
    }

    public int setPassword(String carId, String password) {
        if (isBlank(carId) || isBlank(password)) return 1;
        User user = userMapper.findByCarId(carId);
        if (user == null || user.getState() != UserState.INACTIVE) return 1;

        String encoded = encoder.encode(password);
        int rows = userMapper.activateWithPassword(carId, encoded);
        return rows > 0 ? 0 : 1;
    }

    public boolean login(String carId, String password) {
        if (isBlank(carId) || isBlank(password)) return false;
        User user = userMapper.findByCarId(carId);
        if (user == null || user.getPassword() == null || user.getState() != UserState.ACTIVE) return false;
        return encoder.matches(password, user.getPassword());
    }

    public User findByCarId(String carId) {
        return userMapper.findByCarId(carId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isInvalidCapacity(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }
}
