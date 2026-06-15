package com.bupt.charging;

import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;
import com.bupt.charging.enums.RequestState;
import com.bupt.charging.mapper.ChargingPileMapper;
import com.bupt.charging.mapper.ChargingRecordMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import com.bupt.charging.service.BatchSchedulingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 8b 批量调度总时长最短策略（BATCH_SHORTEST）单测。
 *
 * <p>验证：force 触发批量调度时，不区分快慢充模式、任意车可分配任意类型桩，
 * 贪心代价矩阵把所有等候车分配到空闲桩并启动充电。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:sqlite:file:batch-shortest-test?mode=memory&cache=shared",
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.sql.init.mode=always",
                "charging.scheduling.mode=BATCH_SHORTEST"
        }
)
class BatchShortestSchedulingTest {
    @Autowired BatchSchedulingService batchSchedulingService;
    @Autowired ChargingRequestMapper requestMapper;
    @Autowired ChargingRecordMapper recordMapper;
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

    @Test
    void batchDispatchAssignsAnyCarToAnyPileType() {
        // 全部 5 个桩开机空闲（默认 data.sql：桩1/2 快充, 桩3/4/5 慢充，依实际种子而定）。
        for (int i = 1; i <= 5; i++) {
            pileMapper.updatePowerAndWorkingState(i, PilePowerState.ON, PileWorkingState.IDLE);
        }

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 6, 0);
        // 放入慢充请求若干。8b 允许把慢充车分配到快充桩。
        ChargingRequest c1 = insertWaiting("batch-1", ChargingMode.SLOW, 10, base.plusSeconds(1));
        ChargingRequest c2 = insertWaiting("batch-2", ChargingMode.SLOW, 20, base.plusSeconds(2));
        ChargingRequest c3 = insertWaiting("batch-3", ChargingMode.FAST, 30, base.plusSeconds(3));

        // force=true 跳过满站校验，直接演示批量分配。
        BatchSchedulingService.BatchResult result = batchSchedulingService.dispatchBatch(true);

        assertThat(result.code()).isEqualTo(0);
        assertThat(result.assignments()).hasSize(3);

        // 三辆车都应进入 charging 状态，且都生成了进行中的充电记录。
        for (ChargingRequest c : List.of(c1, c2, c3)) {
            ChargingRequest re = requestMapper.findById(c.getId());
            assertThat(re.getCarState()).isEqualTo(RequestState.CHARGING);
            assertThat(recordMapper.findActiveByCarId(c.getCarId())).isNotNull();
        }

        // 至少有一辆慢充车被分到了快充桩(跨类型)，证明 8b「任意车任意桩」生效。
        // 慢充车 c1/c2 中若有 pileId 指向快充桩(type=FAST)即满足。
        boolean crossType = List.of(c1, c2).stream().anyMatch(c -> {
            ChargingRequest re = requestMapper.findById(c.getId());
            return pileMapper.findById(re.getPileId()).getType() == ChargingMode.FAST;
        });
        assertThat(crossType).isTrue();
    }
}
