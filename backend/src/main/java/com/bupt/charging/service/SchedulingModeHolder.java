package com.bupt.charging.service;

import com.bupt.charging.enums.SchedulingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 调度模式的运行期唯一真相源（单例）。
 *
 * <p>初始值取自配置项 {@code charging.scheduling.mode}（默认 BASIC），但允许通过管理员端点
 * 在运行期切换。QueueService 与 BatchSchedulingService 都从本 holder 读取当前模式，
 * 保证两处状态一致，不会各持一份 final 字段而失同步。</p>
 *
 * <p>用 volatile 保证可见性：写入（管理员切换）后，其他线程（充电请求调度）能立即读到新值。
 * 调度模式切换是低频管理操作，不需要更重的锁。</p>
 */
@Component
public class SchedulingModeHolder {
    private volatile SchedulingMode mode;

    public SchedulingModeHolder(@Value("${charging.scheduling.mode:BASIC}") SchedulingMode initial) {
        this.mode = initial == null ? SchedulingMode.BASIC : initial;
    }

    public SchedulingMode getMode() {
        return mode;
    }

    public void setMode(SchedulingMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }
}
