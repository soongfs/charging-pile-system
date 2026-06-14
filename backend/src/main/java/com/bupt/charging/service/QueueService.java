package com.bupt.charging.service;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.RequestState;
import com.bupt.charging.mapper.ChargingPileMapper;
import com.bupt.charging.mapper.ChargingRecordMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import com.bupt.charging.service.scheduling.SchedulingStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class QueueService {
    private final ChargingRequestMapper requestMapper;
    private final ChargingPileMapper pileMapper;
    private final ChargingRecordMapper recordMapper;
    private final SchedulingStrategy schedulingStrategy;

    public QueueService(ChargingRequestMapper requestMapper,
                        ChargingPileMapper pileMapper,
                        ChargingRecordMapper recordMapper,
                        @Qualifier("timeOrderSchedulingStrategy") SchedulingStrategy schedulingStrategy) {
        this.requestMapper = requestMapper;
        this.pileMapper = pileMapper;
        this.recordMapper = recordMapper;
        this.schedulingStrategy = schedulingStrategy;
    }

    /**
     * Enqueue a request into the waiting queue.
     * Assigns queue_num based on existing waiting requests of the same mode.
     */
    public void enqueue(ChargingRequest request) {
        request.setCarState(RequestState.WAITING);
        request.setPileId(null);
        request.setUpdateTime(LocalDateTime.now());
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
            request.setUpdateTime(LocalDateTime.now());
            requestMapper.update(request);
        }
    }

    /**
     * Dispatch the next waiting vehicle to an available charging pile.
     * Called when a pile becomes free.
     */
    public ChargingRequest dispatchNext(ChargingMode mode) {
        List<ChargingPile> available = pileMapper.findAvailablePiles(mode.getPileType());
        List<ChargingPile> freePiles = new ArrayList<>();
        for (ChargingPile pile : available) {
            if (requestMapper.findActiveAssignedByPileId(pile.getId()).isEmpty()
                    && recordMapper.findActiveByPileId(pile.getId()) == null) {
                freePiles.add(pile);
            }
        }
        if (freePiles.isEmpty()) return null;

        List<ChargingRequest> waiting = requestMapper.findWaitingByMode(mode);
        if (waiting.isEmpty()) return null;

        // 故障再调度的车带 priority>0, 绝对优先于普通等候区(等候区冻结)。
        // 只在当前最高优先级分组内, 再交由调度策略按其规则选车。
        int topPriority = waiting.stream()
                .map(r -> r.getPriority() == null ? 0 : r.getPriority())
                .max(Integer::compareTo)
                .orElse(0);
        List<ChargingRequest> candidates = new ArrayList<>();
        for (ChargingRequest r : waiting) {
            int p = r.getPriority() == null ? 0 : r.getPriority();
            if (p == topPriority) {
                candidates.add(r);
            }
        }

        ChargingRequest next = schedulingStrategy.selectNextCar(candidates);
        if (next == null) return null;

        ChargingPile bestPile = schedulingStrategy.selectPile(next, freePiles);
        if (bestPile == null) return null;

        int rows = requestMapper.assignPileIfWaiting(next.getId(), bestPile.getId());
        if (rows <= 0) {
            refreshQueueNumbers(mode);
            return null;
        }
        next = requestMapper.findById(next.getId());
        refreshQueueNumbers(mode);
        return next;
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
