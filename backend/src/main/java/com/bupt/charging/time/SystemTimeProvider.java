package com.bupt.charging.time;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 生产环境默认时间源：返回真实墙钟时间。
 *
 * <p>在非 sim profile 下作为 {@link TimeProvider} 的唯一实现激活，
 * 系统行为与未引入时间源抽象前完全一致。</p>
 */
@Component
@Primary
@Profile("!sim")
public class SystemTimeProvider implements TimeProvider {
    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
