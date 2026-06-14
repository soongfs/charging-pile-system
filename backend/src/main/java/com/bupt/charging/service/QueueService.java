package com.bupt.charging.service;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRecord;
import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.RequestState;
import com.bupt.charging.mapper.ChargingPileMapper;
import com.bupt.charging.mapper.ChargingRecordMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import com.bupt.charging.service.scheduling.SchedulingStrategy;
import com.bupt.charging.time.TimeProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class QueueService {
    private final ChargingRequestMapper requestMapper;
    private final ChargingPileMapper pileMapper;
    private final ChargingRecordMapper recordMapper;
    private final SchedulingStrategy schedulingStrategy;
    private final TimeProvider timeProvider;

    public QueueService(ChargingRequestMapper requestMapper,
                        ChargingPileMapper pileMapper,
                        ChargingRecordMapper recordMapper,
                        @Qualifier("timeOrderSchedulingStrategy") SchedulingStrategy schedulingStrategy,
                        TimeProvider timeProvider) {
        this.requestMapper = requestMapper;
        this.pileMapper = pileMapper;
        this.recordMapper = recordMapper;
        this.schedulingStrategy = schedulingStrategy;
        this.timeProvider = timeProvider;
    }

    /**
     * Enqueue a request into the waiting queue.
     * Assigns queue_num based on existing waiting requests of the same mode.
     */
    public void enqueue(ChargingRequest request) {
        request.setCarState(RequestState.WAITING);
        request.setPileId(null);
        request.setUpdateTime(timeProvider.now());
        List<ChargingRequest> waiting = requestMapper.findWaitingByMode(request.getRequestMode());
        int queueNum = 1;
        for (ChargingRequest item : waiting) {
            if (!Objects.equals(item.getId(), request.getId())) {
                queueNum++;
            }
        }
        request.setQueueNum(queueNum);
    }

    /**
     * Get queue position (number of cars ahead).
     */
    public int getPosition(ChargingRequest request) {
        List<ChargingRequest> waiting = requestMapper.findWaitingByMode(request.getRequestMode());
        int count = 0;
        for (ChargingRequest r : waiting) {
            if (!Objects.equals(r.getId(), request.getId())
                    && r.getRequestTime().compareTo(request.getRequestTime()) < 0) {
                count++;
            }
        }
        return count;
    }

    public void refreshQueueNumbers(ChargingMode mode) {
        List<ChargingRequest> waiting = requestMapper.findWaitingByMode(mode);
        for (int i = 0; i < waiting.size(); i++) {
            ChargingRequest request = waiting.get(i);
            request.setQueueNum(i + 1);
            request.setUpdateTime(timeProvider.now());
            requestMapper.update(request);
        }
    }

    /**
     * 每个充电桩的队列长度上限 M：1 个充电中车头 + 最多 (M-1) 个桩内排队。
     */
    private static final int PILE_QUEUE_CAP = 3;

    /**
     * 将等候区车辆调度进充电桩的桩内队列。
     *
     * <p>两级排队模型：等候区(N=10) -> 桩内队列(M=3)。每个同类型且未满(已分配车数<M)
     * 的桩都是候选；按"完成充电所需时间最短"为待调度车选桩(并列取桩号小)。一次调用会
     * 持续铺满所有可用桩内空位，直至无车可放。</p>
     *
     * <p>故障再调度的车带 priority>0，绝对优先于普通等候区(等候区冻结)，
     * 只在最高优先级分组内再按到达先后(FCFS)选车。</p>
     *
     * @return 本次最后一辆成功进入桩内队列的车；无调度发生返回 null
     */
    public ChargingRequest dispatchNext(ChargingMode mode) {
        ChargingRequest lastDispatched = null;
        while (true) {
            List<ChargingPile> candidates = candidatePiles(mode);
            if (candidates.isEmpty()) return lastDispatched;

            List<ChargingRequest> waiting = requestMapper.findWaitingByMode(mode);
            if (waiting.isEmpty()) return lastDispatched;

            // 故障优先: 只在最高优先级分组内选车, 冻结普通等候区。
            int topPriority = waiting.stream()
                    .map(r -> r.getPriority() == null ? 0 : r.getPriority())
                    .max(Integer::compareTo)
                    .orElse(0);
            List<ChargingRequest> group = new ArrayList<>();
            for (ChargingRequest r : waiting) {
                int p = r.getPriority() == null ? 0 : r.getPriority();
                if (p == topPriority) group.add(r);
            }

            ChargingRequest next = schedulingStrategy.selectNextCar(group);
            if (next == null) return lastDispatched;

            ChargingPile bestPile = selectPileByFinishTime(next, candidates);
            if (bestPile == null) return lastDispatched;

            int rows = requestMapper.assignPileIfWaiting(next.getId(), bestPile.getId());
            if (rows <= 0) {
                refreshQueueNumbers(mode);
                return lastDispatched;
            }
            lastDispatched = requestMapper.findById(next.getId());
            refreshQueueNumbers(mode);
        }
    }

    /**
     * 候选桩: 同类型、已开机、未故障、桩内已分配车数 < M。
     */
    private List<ChargingPile> candidatePiles(ChargingMode mode) {
        List<ChargingPile> available = pileMapper.findAvailablePiles(mode.getPileType());
        List<ChargingPile> result = new ArrayList<>();
        for (ChargingPile pile : available) {
            int assigned = requestMapper.findActiveAssignedByPileId(pile.getId()).size();
            if (assigned < PILE_QUEUE_CAP) {
                result.add(pile);
            }
        }
        return result;
    }

    /**
     * 按"完成充电所需时间最短"选桩: 该桩现有车的剩余充电时间之和 + 本车自身充电时间, 取最小;
     * 并列时取桩号小者。充电中车头按已充电量折算剩余, 桩内排队车按申请量全量计。
     */
    private ChargingPile selectPileByFinishTime(ChargingRequest request, List<ChargingPile> candidates) {
        ChargingPile best = null;
        double bestFinish = Double.MAX_VALUE;
        for (ChargingPile pile : candidates) {
            double finish = pileLoadHours(pile) + chargeHoursOnPile(request.getRequestAmount(), pile);
            if (finish < bestFinish - 1e-9
                    || (Math.abs(finish - bestFinish) <= 1e-9
                        && (best == null || pile.getId() < best.getId()))) {
                bestFinish = finish;
                best = pile;
            }
        }
        return best;
    }

    /** 桩内现有所有车清空所需的总小时数。 */
    private double pileLoadHours(ChargingPile pile) {
        double power = pile.getPowerKw().doubleValue();
        if (power <= 0) return 0;
        double total = 0;
        List<ChargingRequest> assigned = requestMapper.findActiveAssignedByPileId(pile.getId());
        ChargingRecord head = recordMapper.findActiveByPileId(pile.getId());
        for (ChargingRequest r : assigned) {
            double amount = r.getRequestAmount().doubleValue();
            if (head != null && r.getId().equals(head.getRequestId())) {
                long elapsed = Math.max(0, ChronoUnit.SECONDS.between(head.getStartTime(), timeProvider.now()));
                double charged = Math.min(amount, power * elapsed / 3600.0);
                total += (amount - charged) / power;
            } else {
                total += amount / power;
            }
        }
        return total;
    }

    private double chargeHoursOnPile(java.math.BigDecimal amount, ChargingPile pile) {
        double power = pile.getPowerKw().doubleValue();
        return power <= 0 ? 0 : amount.doubleValue() / power;
    }

    /**
     * 充电桩故障/关闭时, 释放该桩上已分配但尚未完成的车辆并执行再调度。
     *
     * <p>按验收规则, 故障桩上的车进入"故障优先队列": 给予 priority=1, 使其在 dispatchNext
     * 中绝对优先于普通等候区车辆(普通等候区被冻结), 直至故障车全部进入充电区后, 才恢复
     * 对普通等候区的调度。再调度仅在同类型充电桩内进行, 不跨快慢充类型。</p>
     */
    public void releaseDispatchedForPile(Integer pileId) {
        List<ChargingRequest> assigned = requestMapper.findDispatchedByPileId(pileId);
        Set<ChargingMode> affectedModes = EnumSet.noneOf(ChargingMode.class);
        for (ChargingRequest request : assigned) {
            affectedModes.add(request.getRequestMode());
            request.setPileId(null);
            request.setCarState(RequestState.WAITING);
            request.setPriority(1);   // 故障优先队列: 冻结普通等候区
            enqueue(request);
            requestMapper.update(request);
        }
        for (ChargingMode mode : affectedModes) {
            refreshQueueNumbers(mode);
            dispatchNext(mode);
        }
    }
}
