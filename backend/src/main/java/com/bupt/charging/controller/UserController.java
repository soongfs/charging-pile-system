package com.bupt.charging.controller;

import com.bupt.charging.dto.LoginRequest;
import com.bupt.charging.dto.RegisterUserRequest;
import com.bupt.charging.dto.SetPasswordRequest;
import com.bupt.charging.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Map<String, Object> createNewAccount(@RequestBody RegisterUserRequest body) {
        int result = userService.createAccount(body);
        return ApiResponse.fromResult(result, "车辆账号已存在或参数无效");
    }

    @PostMapping("/set-password")
    public Map<String, Object> setPassword(@RequestBody SetPasswordRequest body) {
        int result = userService.setPassword(body.carId(), body.password());
        return ApiResponse.fromResult(result, "账号不存在、已激活或密码无效");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body) {
        boolean ok = userService.login(body.carId(), body.password());
        return ok ? ApiResponse.success(Map.of("carId", body.carId()))
                  : ApiResponse.failure("密码错误或账号不存在");
    }
}
