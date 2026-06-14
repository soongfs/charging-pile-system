package com.bupt.charging.controller;

import com.bupt.charging.time.SimTimeProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 仿真时钟控制端点，仅在 sim profile 下存在。
 *
 * <p>用于验收用例回放：脚本按时间线把仿真钟设置/推进到指定时刻，配合
 * ChargingMonitorService 的定时轮询复现“充满自动离桩”等时间相关行为。
 * 生产环境（非 sim profile）不加载本控制器，外部无法篡改系统时间。</p>
 */
@RestController
@RequestMapping("/api/admin/sim")
@Profile("sim")
public class SimClockController {
    private final SimTimeProvider clock;

    public SimClockController(SimTimeProvider clock) {
        this.clock = clock;
    }

    /** 查询当前仿真时间。 */
    @GetMapping("/clock")
    public Map<String, Object> getClock() {
        return ApiResponse.success(Map.of("now", clock.now().toString()));
    }

    /**
     * 设置仿真时间。body: {"time":"2026-06-15T06:00:00"} 绝对时刻，
     * 或 {"advanceMinutes": 5} 相对推进。两者可二选一。
     */
    @PostMapping("/clock")
    public Map<String, Object> setClock(@RequestBody Map<String, Object> body) {
        Object time = body.get("time");
        Object advance = body.get("advanceMinutes");
        if (time != null) {
            clock.set(LocalDateTime.parse(time.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else if (advance != null) {
            clock.advance(Long.parseLong(advance.toString()));
        } else {
            return ApiResponse.failure("缺少 time 或 advanceMinutes 参数");
        }
        return ApiResponse.success(Map.of("now", clock.now().toString()));
    }
}
