package com.bupt.charging;

import com.bupt.charging.dto.ChargingCommandRequest;
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
 * 验证桩内队列 M=3 两级排队模型:
 * 同类型多辆车被分配到同一个桩的桩内队列(1 充 + 最多 2 排队),
 * 而非每桩只放 1 辆把其余全堆等候区。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:sqlite:file:pilequeue-test?mode=memory&cache=shared",
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.sql.init.mode=always"
        }
)
class PileQueueCapacityTest {
    @Autowired QueueService queueService;
    @Autowired ChargingRequestMapper requestMapper;
    @Autowired ChargingPileMapper pileMapper;

    private void insertWaiting(String carId, ChargingMode mode, int amount, LocalDateTime time) {
        ChargingRequest r = new ChargingRequest(carId, BigDecimal.valueOf(amount), mode);
        r.setRequestTime(time);
        r.setUpdateTime(time);
        r.setCarState(RequestState.WAITING);
        r.setQueueNum(0);
        r.setPriority(0);
        requestMapper.insert(r);
    }

    @Test
    void threeCarsFillSinglePileQueueWhenOnlyOnePileAvailable() {
        // 仅快充桩 1 可用, 桩 2 关机 -> 快充总容量 = 单桩 M=3。
        pileMapper.updatePowerAndWorkingState(1, PilePowerState.ON, PileWorkingState.RUNNING);
        pileMapper.updatePowerAndWorkingState(2, PilePowerState.OFF, PileWorkingState.OFF);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 6, 0);
        for (int i = 1; i <= 4; i++) {
            insertWaiting("pq-" + i + "-" + System.nanoTime(), ChargingMode.FAST, 30, base.plusSeconds(i));
        }

        queueService.dispatchNext(ChargingMode.FAST);

        // 桩 1 应被分配满 M=3 辆, 第 4 辆留在等候区。
        List<ChargingRequest> assigned = requestMapper.findActiveAssignedByPileId(1);
        assertThat(assigned).hasSize(3);
        List<ChargingRequest> stillWaiting = requestMapper.findWaitingByMode(ChargingMode.FAST);
        assertThat(stillWaiting).hasSize(1);
    }
}
