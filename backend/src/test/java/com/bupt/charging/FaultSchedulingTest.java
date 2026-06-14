package com.bupt.charging;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRecord;
import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;
import com.bupt.charging.enums.RequestState;
import com.bupt.charging.mapper.ChargingPileMapper;
import com.bupt.charging.mapper.ChargingRecordMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import com.bupt.charging.mapper.UserMapper;
import com.bupt.charging.service.ChargingService;
import com.bupt.charging.service.PileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证充电桩故障调度(方案甲):
 * 1. 故障桩强制置 FAULT(即使正在充电);
 * 2. 正在充电的车头按已充电量结算出账单, 剩余需求转高优先级申请重调度;
 * 3. 桩内排队车以 priority=1 重新入队(等候区冻结)。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:sqlite:file:fault-flow-test?mode=memory&cache=shared",
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.sql.init.mode=always"
        }
)
class FaultSchedulingTest {
    @Autowired ChargingService chargingService;
    @Autowired PileService pileService;
    @Autowired ChargingRequestMapper requestMapper;
    @Autowired ChargingRecordMapper recordMapper;
    @Autowired ChargingPileMapper pileMapper;
    @Autowired UserMapper userMapper;

    @Test
    void faultForcesPileFaultAndMigratesChargingHead() {
        // 准备: 慢充桩 3 充电, 桩 4 可用(故障迁移目标), 桩 5 关机。
        pileMapper.updatePowerAndWorkingState(3, PilePowerState.ON, PileWorkingState.RUNNING);
        pileMapper.updatePowerAndWorkingState(4, PilePowerState.ON, PileWorkingState.RUNNING);
        pileMapper.updatePowerAndWorkingState(5, PilePowerState.OFF, PileWorkingState.OFF);

        // 一辆慢充车在桩 3 充电(用真实下单流程, 时钟为系统真实时间)。
        String car = "fault-head-" + System.nanoTime();
        userMapper.insert(makeUser(car));
        chargingService.submitRequest(new com.bupt.charging.dto.SubmitChargingRequest(
                car, BigDecimal.valueOf(20), 0));
        chargingService.startCharging(new com.bupt.charging.dto.ChargingCommandRequest(car, 3));
        assertThat(recordMapper.findActiveByPileId(3)).isNotNull();

        // 触发桩 3 故障。
        int rc = chargingService.faultPile(3);
        assertThat(rc).isEqualTo(0);

        // 桩 3 应为 FAULT。
        ChargingPile p3 = pileMapper.findById(3);
        assertThat(p3.getWorkingState()).isEqualTo(PileWorkingState.FAULT);

        // 原充电记录已结算关闭(end_time 不为空)。
        ChargingRecord active = recordMapper.findActiveByPileId(3);
        assertThat(active).isNull();

        // 该车应有一条进行中的高优先级续充申请(剩余需求)。
        ChargingRequest cont = requestMapper.findByQueueType(ChargingMode.SLOW).stream()
                .filter(r -> r.getCarId().equals(car))
                .findFirst().orElse(null);
        assertThat(cont).isNotNull();
        assertThat(cont.getPriority()).isEqualTo(1);
        assertThat(cont.getRequestAmount()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    private com.bupt.charging.entity.User makeUser(String carId) {
        com.bupt.charging.entity.User u = new com.bupt.charging.entity.User(carId, carId, BigDecimal.valueOf(100));
        u.setState(com.bupt.charging.enums.UserState.ACTIVE);
        u.setPassword("pw");
        return u;
    }
}
