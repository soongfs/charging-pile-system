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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 8a 单次调度总时长最短策略（SINGLE_SHORTEST）单测。
 *
 * <p>验证：当多辆同类型车在等候区、仅一个充电桩位时，选车按「最短作业优先(SPT)」
 * 而非到达先后(FCFS)——自身充电时长最短(电量最小)的车先进入充电区。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:sqlite:file:single-shortest-test?mode=memory&cache=shared",
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.sql.init.mode=always",
                "charging.scheduling.mode=SINGLE_SHORTEST"
        }
)
class SingleShortestSchedulingTest {
    @Autowired QueueService queueService;
    @Autowired ChargingRequestMapper requestMapper;
    @Autowired ChargingPileMapper pileMapper;

    private ChargingRequest insertWaiting(String carId, ChargingMode mode, int amount, LocalDateTime time) {
        ChargingRequest r = new ChargingRequest(carId, BigDecimal.valueOf(amount), mode);
        r.setRequestTime(time);
        r.setUpdateTime(time);
        r.setCarState(RequestState.WAITING);
        r.setQueueNum(0);
        r.setPriority(0);
        requestMapper.insert(r);
        return r;
    }

    /** 预占一个桩内位置：插一辆已分配(dispatched)到指定桩的车，占用 M 容量。 */
    private void preoccupy(String carId, ChargingMode mode, int amount, Integer pileId, LocalDateTime time) {
        ChargingRequest r = insertWaiting(carId, mode, amount, time);
        requestMapper.assignPileIfWaiting(r.getId(), pileId);
    }

    @Test
    void shortestJobFirstWinsOverArrivalOrder() {
        // 仅快充桩 1 可用；先预占 2 个桩内位置，使桩 1 只剩 1 个空位(M=3)。
        pileMapper.updatePowerAndWorkingState(1, PilePowerState.ON, PileWorkingState.RUNNING);
        pileMapper.updatePowerAndWorkingState(2, PilePowerState.OFF, PileWorkingState.OFF);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 6, 0);
        preoccupy("ss-fill1", ChargingMode.FAST, 30, 1, base.minusSeconds(2));
        preoccupy("ss-fill2", ChargingMode.FAST, 30, 1, base.minusSeconds(1));

        // 剩 1 空位。先到大电量(60度)，后到小电量(15度)。FCFS 会选 big，SPT 应选 small。
        ChargingRequest big = insertWaiting("ss-big", ChargingMode.FAST, 60, base.plusSeconds(1));
        ChargingRequest small = insertWaiting("ss-small", ChargingMode.FAST, 15, base.plusSeconds(2));

        queueService.dispatchNext(ChargingMode.FAST);

        // 只剩 1 空位，SPT 应把 small 调进桩，big 仍留等候区。
        ChargingRequest reSmall = requestMapper.findById(small.getId());
        ChargingRequest reBig = requestMapper.findById(big.getId());
        assertThat(reSmall.getCarState()).isEqualTo(RequestState.DISPATCHED);
        assertThat(reBig.getCarState()).isEqualTo(RequestState.WAITING);
    }
}
