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

    private void insertDispatchedOnPile(String carId, ChargingMode mode, int amount,
                                        LocalDateTime time, int pileId) {
        ChargingRequest r = new ChargingRequest(carId, BigDecimal.valueOf(amount), mode);
        r.setRequestTime(time);
        r.setUpdateTime(time);
        r.setCarState(RequestState.DISPATCHED);
        r.setQueueNum(0);
        r.setPriority(0);
        r.setPileId(pileId);
        requestMapper.insert(r);
    }

    @Test
    void faultPriorityCarOutranksEarlierNormalWaitingCar() {
        // 快充桩 1 可用, 桩 2 关机 -> 快充只有桩 1 一个可分配桩(容量 M=3)。
        pileMapper.updatePowerAndWorkingState(1, PilePowerState.ON, PileWorkingState.RUNNING);
        pileMapper.updatePowerAndWorkingState(2, PilePowerState.OFF, PileWorkingState.OFF);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 6, 0);
        // 预先占用桩 1 的 2 个槽位(已分配), 使桩 1 仅剩最后 1 个槽位。
        insertDispatchedOnPile("fill-1-" + System.nanoTime(), ChargingMode.FAST, 30, base, 1);
        insertDispatchedOnPile("fill-2-" + System.nanoTime(), ChargingMode.FAST, 30, base.plusSeconds(1), 1);

        // 普通等候区车: 到达较早(06:10), priority=0
        String earlyCar = "fault-early-" + System.nanoTime();
        insertWaiting(earlyCar, ChargingMode.FAST, 30, base.plusMinutes(10), 0);
        // 故障再调度车: 到达更晚(06:30), priority=1 (模拟刚从故障桩释放)
        String faultCar = "fault-prio-" + System.nanoTime();
        insertWaiting(faultCar, ChargingMode.FAST, 30, base.plusMinutes(30), 1);

        // 仅剩 1 个槽位: 调度必须给故障车(priority 高), 冻结更早到达的普通车。
        queueService.dispatchNext(ChargingMode.FAST);

        ChargingRequest faultState = requestMapper.findActiveByCarId(faultCar);
        ChargingRequest earlyState = requestMapper.findActiveByCarId(earlyCar);
        assertThat(faultState.getCarState()).isEqualTo(RequestState.DISPATCHED);
        assertThat(faultState.getPileId()).isEqualTo(1);
        // 普通早到车被冻结, 仍在等候区
        assertThat(earlyState.getCarState()).isEqualTo(RequestState.WAITING);
    }
}
