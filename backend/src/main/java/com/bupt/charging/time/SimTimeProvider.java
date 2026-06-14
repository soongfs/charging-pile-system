package com.bupt.charging.time;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验收回放时间源：可设置 / 可推进的仿真时钟。
 *
 * <p>仅在 sim profile 下激活。初始值为当天 06:00（验收用例时间线起点）。
 * 通过 {@link #set(LocalDateTime)} 跳转到指定时刻，或 {@link #advance(long)}
 * 按分钟推进。生产环境（非 sim profile）不存在该 Bean，时钟无法被外部篡改。</p>
 */
@Component
@Primary
@Profile("sim")
public class SimTimeProvider implements TimeProvider {

    private final AtomicReference<LocalDateTime> current =
            new AtomicReference<>(LocalDateTime.now().toLocalDate().atTime(6, 0));

    @Override
    public LocalDateTime now() {
        return current.get();
    }

    /** 将仿真时钟设置为指定时刻。 */
    public void set(LocalDateTime time) {
        if (time != null) {
            current.set(time);
        }
    }

    /** 按分钟推进仿真时钟。 */
    public void advance(long minutes) {
        current.updateAndGet(t -> t.plusMinutes(minutes));
    }
}
