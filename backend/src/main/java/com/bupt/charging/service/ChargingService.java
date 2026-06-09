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

    public ChargingService(ChargingRequestMapper requestMapper,
                           ChargingRecordMapper recordMapper,
                           UserMapper userMapper,
                           QueueService queueService,
                           BillingService billingService,
                           PileService pileService,
                           ChargingStateMachine stateMachine) {
        this.requestMapper = requestMapper;
        this.recordMapper = recordMapper;
        this.userMapper = userMapper;
        this.queueService = queueService;
        this.billingService = billingService;
        this.pileService = pileService;
        this.stateMachine = stateMachine;
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
        requestMapper.insert(request);
        queueService.enqueue(request);
        request.setUpdateTime(LocalDateTime.now());
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
        request.setUpdateTime(LocalDateTime.now());
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
        request.setUpdateTime(LocalDateTime.now());
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
            ChargingRequest dispatched = queueService.dispatchNext(request.getRequestMode());
            if (dispatched == null || !Objects.equals(dispatched.getId(), request.getId())) return 1;
            request = dispatched;
        }

        if (!request.isDispatched()) return 1;
        Integer assignedPileId = request.getPileId();
        if (assignedPileId == null) return 1;
        if (command.pileId() != null && !Objects.equals(command.pileId(), assignedPileId)) return 1;
        if (!pileService.canStartCharging(assignedPileId, request.getRequestMode())) return 1;
        if (recordMapper.findActiveByPileId(assignedPileId) != null) return 1;

        ChargingRecord record = new ChargingRecord(command.carId().trim(), request.getId(), assignedPileId);
        recordMapper.insert(record);

        pileService.updateWorkingState(assignedPileId, PileWorkingState.CHARGING);

        stateMachine.applyRequestState(request, RequestState.CHARGING);
        request.setPileId(assignedPileId);
        request.setQueueNum(0);
        request.setUpdateTime(LocalDateTime.now());
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

        LocalDateTime endTime = LocalDateTime.now();
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
            request.setUpdateTime(LocalDateTime.now());
            requestMapper.update(request);
            queueService.dispatchNext(request.getRequestMode());
        }

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
