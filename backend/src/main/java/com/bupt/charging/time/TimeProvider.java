package com.bupt.charging.time;

import java.time.LocalDateTime;

/**
 * 系统时间源抽象。
 *
 * <p>所有业务代码通过该接口获取“当前时间”，不直接调用 {@link LocalDateTime#now()}。
 * 生产环境注入 {@link SystemTimeProvider}（真实墙钟）；验收回放环境（sim profile）
 * 注入 {@link SimTimeProvider}，可由 /api/admin/sim/clock 端点设置/推进仿真时间。</p>
 */
public interface TimeProvider {
    LocalDateTime now();
}
