package com.bupt.charging.service;

import com.bupt.charging.entity.ChargingRecord;
import com.bupt.charging.mapper.ChargingRecordMapper;
import com.bupt.charging.time.TimeProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 充满自动离桩监控器（真实功能，所有环境常驻）。
 *
 * <p>定时轮询所有进行中的充电记录，对达到申请电量的车辆自动触发结算与离桩，
 * 释放充电桩并再调度等候区。离桩时刻取“恰好充满”的精确时刻而非轮询时刻，
 * 因此计费金额与轮询频率无关，不会因轮询延迟而多计。</p>
 *
 * <p>时间来源统一走 {@link TimeProvider}：生产环境为真实墙钟；验收回放（sim profile）
 * 下为可推进的仿真钟，仿真时间被推进到/越过充满时刻时，本轮询器即触发离桩。</p>
 */
@Service
public class ChargingMonitorService {
    private final ChargingRecordMapper recordMapper;
    private final BillingService billingService;
    private final ChargingService chargingService;
    private final TimeProvider timeProvider;

    public ChargingMonitorService(ChargingRecordMapper recordMapper,
                                  BillingService billingService,
                                  ChargingService chargingService,
                                  TimeProvider timeProvider) {
        this.recordMapper = recordMapper;
        this.billingService = billingService;
        this.chargingService = chargingService;
        this.timeProvider = timeProvider;
    }

    /**
     * 周期性检查充满离桩。fixedDelay 表示上一次执行结束到下一次开始的间隔。
     */
    @Scheduled(fixedDelayString = "${app.charging.monitor-interval-ms:500}")
    public void checkCompletedCharging() {
        LocalDateTime now = timeProvider.now();
        List<ChargingRecord> active = recordMapper.findAllActive();
        for (ChargingRecord record : active) {
            BillingService.ChargingMetrics metrics = billingService.calculateMetrics(record, now);
            if (!metrics.completed()) {
                continue;
            }
            // 充满：取“恰好达到申请电量”的精确时刻作为离桩时间，使计费与轮询频率无关。
            LocalDateTime fullTime = fullTimeOf(record, metrics);
            chargingService.completeCharging(record, fullTime);
        }
    }

    /**
     * 计算从开始充电到充满申请电量所需的精确时刻。
     * secondsToFull = ceil(targetAmount / powerKw * 3600)，与 BillingService 的可计费秒数口径一致。
     */
    private LocalDateTime fullTimeOf(ChargingRecord record, BillingService.ChargingMetrics metrics) {
        double powerKw = metrics.powerKw().doubleValue();
        double target = metrics.targetAmount().doubleValue();
        if (powerKw <= 0 || target <= 0) {
            return timeProvider.now();
        }
        long secondsToFull = (long) Math.ceil(target / powerKw * 3600.0);
        return record.getStartTime().plusSeconds(secondsToFull);
    }
}
