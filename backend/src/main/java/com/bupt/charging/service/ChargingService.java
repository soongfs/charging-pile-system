package com.bupt.charging.service;

import com.bupt.charging.dto.ChargingCommandRequest;
import com.bupt.charging.dto.ChargingProgressResponse;
import com.bupt.charging.dto.ChargingStateResponse;
import com.bupt.charging.dto.ModifyAmountRequest;
import com.bupt.charging.dto.ModifyModeRequest;
import com.bupt.charging.dto.SubmitChargingRequest;
import com.bupt.charging.entity.ChargingRecord;
import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.entity.User;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.PileWorkingState;
import com.bupt.charging.enums.RequestState;
import com.bupt.charging.enums.UserState;
import com.bupt.charging.mapper.ChargingRecordMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import com.bupt.charging.mapper.UserMapper;
import com.bupt.charging.time.TimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ChargingService {
    private final ChargingRequestMapper requestMapper;
    private final ChargingRecordMapper recordMapper;
    private final UserMapper userMapper;
    private final QueueService queueService;
    private final BillingService billingService;
    private final PileService pileService;
    private final ChargingStateMachine stateMachine;
    private final TimeProvider timeProvider;

    public ChargingService(ChargingRequestMapper requestMapper,
                           ChargingRecordMapper recordMapper,
                           UserMapper userMapper,
                           QueueService queueService,
                           BillingService billingService,
                           PileService pileService,
                           ChargingStateMachine stateMachine,
                           TimeProvider timeProvider) {
        this.requestMapper = requestMapper;
        this.recordMapper = recordMapper;
        this.userMapper = userMapper;
        this.queueService = queueService;
        this.billingService = billingService;
        this.pileService = pileService;
        this.stateMachine = stateMachine;
        this.timeProvider = timeProvider;
    }

    /**
     * UC2: E_chargingRequest — Submit a charging request.
     */
    @Transactional
    public ChargingStateResponse submitRequest(SubmitChargingRequest input) {
        validateRequestInput(input);
        ChargingMode mode = ChargingMode.fromCode(input.requestMode());
        String carId = input.carId().trim();

        User user = userMapper.findByCarId(carId);
        if (user == null || user.getState() != UserState.ACTIVE) {
            throw new IllegalArgumentException("车辆账号不存在或未激活");
        }
        if (input.requestAmount().compareTo(user.getCarCapacity()) > 0) {
            throw new IllegalArgumentException("申请电量不能超过车辆电池容量");
        }
        if (requestMapper.findActiveByCarId(carId) != null || recordMapper.findActiveByCarId(carId) != null) {
            throw new IllegalArgumentException("该车辆已有进行中的充电申请");
        }

        ChargingRequest request = new ChargingRequest(carId, input.requestAmount(), mode);
        LocalDateTime requestTime = timeProvider.now();
        request.setRequestTime(requestTime);
        request.setUpdateTime(requestTime);
        requestMapper.insert(request);
        // 正常请求的请求组根 = 自身, 用于账单按"充电请求生命周期"聚合。
        request.setRootRequestId(request.getId());
        requestMapper.setRootRequestId(request.getId(), request.getId());
        queueService.enqueue(request);
        request.setUpdateTime(timeProvider.now());
        requestMapper.update(request);

        ChargingRequest dispatched = queueService.dispatchNext(mode);
        if (dispatched != null && Objects.equals(dispatched.getId(), request.getId())) {
            request = dispatched;
        } else {
            request = requestMapper.findById(request.getId());
        }

        return toRequestState(request);
    }

    /**
     * UC2: Modify_Amount — Modify requested charge amount.
     */
    @Transactional
    public int modifyAmount(ModifyAmountRequest input) {
        if (input == null || isBlank(input.carId()) || input.amount() == null || input.amount().signum() <= 0) {
            return 1;
        }
        String carId = input.carId().trim();
        ChargingRequest request = requestMapper.findActiveByCarId(carId);
        if (request == null || request.getCarState() == RequestState.CHARGING) return 1;
        if (request.getCarState() != RequestState.WAITING && request.getCarState() != RequestState.DISPATCHED) return 1;

        User user = userMapper.findByCarId(carId);
        if (user == null || input.amount().compareTo(user.getCarCapacity()) > 0) return 1;

        request.setRequestAmount(input.amount());
        request.setUpdateTime(timeProvider.now());
        int rows = requestMapper.update(request);
        return rows > 0 ? 0 : 1;
    }

    /**
     * UC2: Modify_Mode — Modify charging mode (cross-queue).
     */
    @Transactional
    public int modifyMode(ModifyModeRequest input) {
        if (input == null || isBlank(input.carId()) || input.mode() == null) return 1;
        ChargingMode newMode;
        try {
            newMode = ChargingMode.fromCode(input.mode());
        } catch (IllegalArgumentException ex) {
            return 1;
        }

        ChargingRequest request = requestMapper.findActiveByCarId(input.carId().trim());
        if (request == null || request.getCarState() != RequestState.WAITING) return 1;
        ChargingMode oldMode = request.getRequestMode();
        request.setRequestMode(newMode);
        request.setPileId(null);
        queueService.enqueue(request);
        request.setUpdateTime(timeProvider.now());
        int rows = requestMapper.update(request);
        queueService.refreshQueueNumbers(oldMode);
        queueService.refreshQueueNumbers(newMode);
        queueService.dispatchNext(oldMode);
        queueService.dispatchNext(newMode);
        return rows > 0 ? 0 : 1;
    }

    /**
     * UC2: Query_Car_State — Query queue position.
     */
    public ChargingStateResponse queryCarState(String carId) {
        if (isBlank(carId)) return null;
        ChargingRequest request = requestMapper.findActiveByCarId(carId.trim());
        if (request == null) return null;

        return toRequestState(request);
    }

    /**
     * UC2: Start_Charging — Start charging on assigned pile.
     */
    @Transactional
    public int startCharging(ChargingCommandRequest command) {
        if (command == null || isBlank(command.carId())) return 1;
        ChargingRequest request = requestMapper.findActiveByCarId(command.carId().trim());
        if (request == null) return 1;
        if (request.isCharging() || recordMapper.findActiveByCarId(command.carId().trim()) != null) return 1;

        if (request.isWaiting()) {
            queueService.dispatchNext(request.getRequestMode());
            request = requestMapper.findActiveByCarId(command.carId().trim());
            if (request == null) return 1;
        }

        if (!request.isDispatched()) return 1;
        Integer assignedPileId = request.getPileId();
        if (assignedPileId == null) return 1;
        if (command.pileId() != null && !Objects.equals(command.pileId(), assignedPileId)) return 1;
        if (!pileService.canStartCharging(assignedPileId, request.getRequestMode())) return 1;
        if (recordMapper.findActiveByPileId(assignedPileId) != null) return 1;

        ChargingRecord record = new ChargingRecord(command.carId().trim(), request.getId(), assignedPileId);
        record.setStartTime(timeProvider.now());
        recordMapper.insert(record);

        pileService.updateWorkingState(assignedPileId, PileWorkingState.CHARGING);

        stateMachine.applyRequestState(request, RequestState.CHARGING);
        request.setPileId(assignedPileId);
        request.setQueueNum(0);
        request.setUpdateTime(timeProvider.now());
        requestMapper.update(request);

        return 0;
    }

    /**
     * 8b 批量调度专用：将等候车强制分配到指定桩并直接启动充电，<b>不校验请求模式与桩类型一致</b>。
     *
     * <p>仅由 {@link BatchSchedulingService} 在 BATCH_SHORTEST 模式下调用，实现详细需求 8b
     * 「任意车可分配任意类型充电桩」。普通用户充电主路径仍走 {@link #startCharging} 的类型校验，
     * 不受影响。要求车辆处于 waiting/dispatched、桩开机未故障且当前无车在充。</p>
     *
     * @return 0 成功；1 前置条件不满足
     */
    @Transactional
    public int forceAssignAndStart(Long requestId, Integer pileId) {
        if (requestId == null || pileId == null) return 1;
        ChargingRequest request = requestMapper.findById(requestId);
        if (request == null) return 1;
        if (request.getCarState() != RequestState.WAITING && request.getCarState() != RequestState.DISPATCHED) return 1;
        if (recordMapper.findActiveByPileId(pileId) != null) return 1;
        if (recordMapper.findActiveByCarId(request.getCarId()) != null) return 1;

        // 置 dispatched（若已 dispatched 则保持），再转 charging。
        if (request.getCarState() == RequestState.WAITING) {
            stateMachine.applyRequestState(request, RequestState.DISPATCHED);
        }
        request.setPileId(pileId);

        ChargingRecord record = new ChargingRecord(request.getCarId(), request.getId(), pileId);
        record.setStartTime(timeProvider.now());
        recordMapper.insert(record);

        pileService.updateWorkingState(pileId, PileWorkingState.CHARGING);
        stateMachine.applyRequestState(request, RequestState.CHARGING);
        request.setQueueNum(0);
        request.setUpdateTime(timeProvider.now());
        requestMapper.update(request);
        return 0;
    }

    /**
     * UC2: Query_Charging_State — Query real-time charging progress.
     */
    public ChargingProgressResponse queryChargingState(String carId) {
        if (isBlank(carId)) return null;
        ChargingRecord record = recordMapper.findActiveByCarId(carId.trim());
        if (record == null) return null;

        BillingService.ChargingMetrics metrics = billingService.calculateMetrics(record);
        ChargingRequest request = requestMapper.findById(record.getRequestId());

        return new ChargingProgressResponse(
                record.getPileId(),
                record.getRequestId(),
                metrics.targetAmount(),
                request == null ? null : request.getRequestMode().getCode(),
                request == null ? "" : request.getRequestMode().getLabel(),
                metrics.chargedAmount(),
                metrics.elapsedSeconds(),
                record.getStartTime(),
                metrics.chargeFee(),
                metrics.serviceFee(),
                metrics.totalFee(),
                metrics.powerKw(),
                progressPercent(metrics.chargedAmount(), metrics.targetAmount()),
                metrics.completed()
        );
    }

    /**
     * UC2: End_Charging — End charging and generate bill.
     */
    @Transactional
    public int endCharging(ChargingCommandRequest command) {
        if (command == null || isBlank(command.carId())) return 1;
        ChargingRecord record = recordMapper.findActiveByCarId(command.carId().trim());
        if (record == null) return 1;
        if (command.pileId() != null && !Objects.equals(record.getPileId(), command.pileId())) return 1;

        LocalDateTime endTime = timeProvider.now();
        completeCharging(record, endTime);
        return 0;
    }

    /**
     * 结算一条充电记录并离桩：计算计费、写回记录、生成账单、释放充电桩、
     * 将申请置为完成态并触发再调度。
     *
     * <p>由用户主动结束充电（{@link #endCharging}）和系统检测到充满后自动离桩
     * （ChargingMonitorService）共同调用，保证两条路径结算逻辑一致。</p>
     */
    @Transactional
    public void completeCharging(ChargingRecord record, LocalDateTime endTime) {
        BillingService.ChargingMetrics metrics = billingService.calculateMetrics(record, endTime);

        record.setEndTime(endTime);
        record.setChargeAmount(metrics.chargedAmount());
        record.setChargeFee(metrics.chargeFee());
        record.setServiceFee(metrics.serviceFee());
        recordMapper.update(record);

        billingService.generateBill(record);
        pileService.addCompletedCharging(record.getPileId(), metrics.elapsedSeconds(), metrics.chargedAmount());

        ChargingRequest request = requestMapper.findById(record.getRequestId());
        if (request != null) {
            stateMachine.applyRequestState(request, RequestState.DONE);
            request.setQueueNum(0);
            request.setUpdateTime(timeProvider.now());
            requestMapper.update(request);
            queueService.dispatchNext(request.getRequestMode());
        }
    }

    /**
     * UC: 充电桩故障。强制将桩置为 FAULT(无论是否在充电), 并按"故障优先 + 等候区冻结"
     * 重调度该桩上的所有车辆。
     *
     * <p>方案甲(与中途结束计费一致): 正在充电的车头先按已充电量结算出账单, 其未充够的
     * 剩余需求作为一条新的高优先级(priority=1)申请重新入队, 去同类型其他桩续充; 桩内
     * 尚未开始充电的排队车(dispatched)整体以 priority=1 重新入队。随后触发再调度,
     * 故障车绝对优先于普通等候区(冻结), 直至全部进入充电区。</p>
     *
     * @return 0 成功; 1 桩不存在
     */
    @Transactional
    public int faultPile(Integer pileId) {
        if (pileId == null) return 1;

        // 1) 正在充电的车头: 按已充电量结算(出账单), 剩余需求转为新的高优先级申请。
        ChargingRecord head = recordMapper.findActiveByPileId(pileId);
        if (head != null) {
            ChargingRequest origin = requestMapper.findById(head.getRequestId());
            LocalDateTime now = timeProvider.now();
            BillingService.ChargingMetrics metrics = billingService.calculateMetrics(head, now);
            completeCharging(head, now);   // 结算已充部分并置原申请为 DONE

            if (origin != null) {
                BigDecimal remaining = origin.getRequestAmount().subtract(metrics.chargedAmount());
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    ChargingRequest cont = new ChargingRequest(origin.getCarId(), remaining, origin.getRequestMode());
                    LocalDateTime t = timeProvider.now();
                    cont.setRequestTime(origin.getRequestTime());   // 保留原到达时刻, 保证再调度顺序
                    cont.setUpdateTime(t);
                    cont.setPriority(1);                            // 故障优先队列
                    // 续充申请继承原请求组根, 使两段详单归并到同一张账单(一次账单≥2详单)。
                    Long root = origin.getRootRequestId() != null ? origin.getRootRequestId() : origin.getId();
                    cont.setRootRequestId(root);
                    requestMapper.insert(cont);
                    requestMapper.setRootRequestId(cont.getId(), root);
                }
            }
        }

        // 2) 桩内尚未开始充电的排队车: 整体以 priority=1 重新入队(releaseDispatchedForPile 已实现)。
        //    3) 标记桩 FAULT 并触发再调度也在其内完成。
        pileService.faultPile(pileId);
        return 0;
    }

    private void validateRequestInput(SubmitChargingRequest input) {
        if (input == null || isBlank(input.carId())) {
            throw new IllegalArgumentException("车辆 ID 不能为空");
        }
        if (input.requestAmount() == null || input.requestAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("申请电量必须大于 0");
        }
        if (input.requestMode() == null) {
            throw new IllegalArgumentException("充电模式不能为空");
        }
        ChargingMode.fromCode(input.requestMode());
    }

    private ChargingStateResponse toRequestState(ChargingRequest request) {
        int carsAhead = request.isWaiting() ? queueService.getPosition(request) : 0;
        return ChargingStateResponse.from(request, carsAhead);
    }

    private BigDecimal progressPercent(BigDecimal chargedAmount, BigDecimal targetAmount) {
        if (targetAmount == null || targetAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal percent = chargedAmount
                .divide(targetAmount, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return percent.min(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
