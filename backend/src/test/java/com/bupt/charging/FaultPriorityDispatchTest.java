package com.bupt.charging;

import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;
import com.bupt.charging.enums.RequestState;
import com.bupt.charging.mapper.ChargingPileMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import com.bupt.charging.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证故障再调度的"故障优先队列冻结普通等候区"语义:
 * 故障桩释放的车(priority=1)必须优先于普通等候区车辆被调度,
 * 即便普通等候区车辆的 request_time 更早。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:sqlite:file:fault-test?mode=memory&cache=shared",
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.sql.init.mode=always"
        }
)
class FaultPriorityDispatchTest {
    @Autowired QueueService queueService;
    @Autowired ChargingRequestMapper requestMapper;
    @Autowired ChargingPileMapper pileMapper;

    private ChargingRequest insertWaiting(String carId, ChargingMode mode, int amount,
                                          LocalDateTime time, int priority) {
        ChargingRequest r = new ChargingRequest(carId, BigDecimal.valueOf(amount), mode);
        r.setRequestTime(time);
        r.setUpdateTime(time);
        r.setCarState(RequestState.WAITING);
        r.setQueueNum(0);
        r.setPriority(priority);
        requestMapper.insert(r);
        return r;
    }

    @Test
    void faultPriorityCarOutranksEarlierNormalWaitingCar() {
        // 使用 data.sql 预置的快充桩 1, 确保其空闲可分配
        pileMapper.updatePowerAndWorkingState(1, PilePowerState.ON, PileWorkingState.RUNNING);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 6, 0);
        // 普通等候区车: 到达更早(06:00), priority=0
        String earlyCar = "fault-early-" + System.nanoTime();
        insertWaiting(earlyCar, ChargingMode.FAST, 30, base, 0);
        // 故障再调度车: 到达更晚(06:30), priority=1 (模拟刚从故障桩释放)
        String faultCar = "fault-prio-" + System.nanoTime();
        insertWaiting(faultCar, ChargingMode.FAST, 30, base.plusMinutes(30), 1);

        // 调度一次: 应选中故障车(priority 高), 而非到达更早的普通车
        ChargingRequest dispatched = queueService.dispatchNext(ChargingMode.FAST);

        assertThat(dispatched).isNotNull();
        assertThat(dispatched.getCarId()).isEqualTo(faultCar);
        assertThat(dispatched.getCarState()).isEqualTo(RequestState.DISPATCHED);

        // 普通早到车仍在等候(被冻结)
        ChargingRequest stillWaiting = requestMapper.findActiveByCarId(earlyCar);
        assertThat(stillWaiting.getCarState()).isEqualTo(RequestState.WAITING);
    }
}
