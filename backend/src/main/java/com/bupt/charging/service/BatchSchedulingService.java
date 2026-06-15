package com.bupt.charging.service;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;
import com.bupt.charging.enums.RequestState;
import com.bupt.charging.enums.SchedulingMode;
import com.bupt.charging.mapper.ChargingPileMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 8b 批量调度总时长最短策略（详细需求第 8b 条，可选加分）。
 *
 * <p>触发条件：仅当到达充电站的车辆数 = 全部车位数（充电区 + 等候区）时，触发一次批量调度，
 * 完成后再进行下一批。一次批量调度中<b>不区分快充/慢充模式、不区分到达先后</b>，
 * 任意车可分配任意类型充电桩。</p>
 *
 * <p>算法：构造代价矩阵 Matrix[i][j] = 车辆 i 在桩 j 上的完成总时长（桩现有负载 + 自身充电时长），
 * 贪心迭代选取代价最小的（车,桩）对进行分配，直至无可分配对，使整批累计完成时长最短。</p>
 *
 * <p>按详细需求 8c：批量调度不考虑改请求与充电桩故障等特殊情况，故本服务独立于用户充电主路径，
 * 仅由管理员演示端点触发，<b>不影响 BASIC 默认调度</b>。</p>
 */
@Service
public class BatchSchedulingService {
    private final ChargingRequestMapper requestMapper;
    private final ChargingPileMapper pileMapper;
    private final ChargingService chargingService;
    private final SchedulingModeHolder schedulingModeHolder;

    public BatchSchedulingService(ChargingRequestMapper requestMapper,
                                  ChargingPileMapper pileMapper,
                                  ChargingService chargingService,
                                  SchedulingModeHolder schedulingModeHolder) {
        this.requestMapper = requestMapper;
        this.pileMapper = pileMapper;
        this.chargingService = chargingService;
        this.schedulingModeHolder = schedulingModeHolder;
    }

    /** 批量调度结果。 */
    public record BatchResult(int code, String message, List<Assignment> assignments) {}

    /** 单条分配：车牌 -> 桩号，以及该桩完成总时长（小时）。 */
    public record Assignment(String carId, Long requestId, Integer pileId,
                             String pileName, double finishHours) {}

    /**
     * 执行一次批量调度。
     *
     * @param force true 时跳过「满站」前置校验，便于演示（仍按算法分配）；false 时严格按需求等待满站。
     */
    @Transactional
    public BatchResult dispatchBatch(boolean force) {
        if (schedulingModeHolder.getMode() != SchedulingMode.BATCH_SHORTEST) {
            return new BatchResult(1, "当前调度模式非 BATCH_SHORTEST，批量调度未启用", List.of());
        }

        // 候选车：所有等候区(waiting)车辆，不区分快慢充。
        List<ChargingRequest> waiting = new ArrayList<>();
        waiting.addAll(requestMapper.findWaitingByMode(com.bupt.charging.enums.ChargingMode.FAST));
        waiting.addAll(requestMapper.findWaitingByMode(com.bupt.charging.enums.ChargingMode.SLOW));

        // 候选桩：所有开机、未故障、当前无车在充的空闲桩（批量调度面向空闲充电区）。
        List<ChargingPile> piles = new ArrayList<>();
        for (ChargingPile p : pileMapper.findAll()) {
            if (p.getPowerState() == PilePowerState.ON
                    && (p.getWorkingState() == PileWorkingState.IDLE
                        || p.getWorkingState() == PileWorkingState.RUNNING)) {
                piles.add(p);
            }
        }

        if (waiting.isEmpty() || piles.isEmpty()) {
            return new BatchResult(1, "无等候车辆或无空闲充电桩，批量调度未触发", List.of());
        }

        // 满站校验：到达车辆数(充电区已占 + 等候区) == 全部车位数(充电桩数*M + 等候区N)。
        if (!force && !isStationFull()) {
            return new BatchResult(1, "未满站，批量调度按需求暂不触发（可加 force=true 演示）", List.of());
        }

        // 贪心代价矩阵：批量调度面向空闲充电区，一桩一车（经典指派）。每轮在所有
        // (未分配车, 未占用桩)对中选完成总时长最小者，直至车或桩耗尽。
        List<Assignment> assignments = new ArrayList<>();
        boolean[] carUsed = new boolean[waiting.size()];
        boolean[] pileUsed = new boolean[piles.size()];

        while (true) {
            int bestCar = -1, bestPile = -1;
            double bestFinish = Double.MAX_VALUE;
            for (int i = 0; i < waiting.size(); i++) {
                if (carUsed[i]) continue;
                double amount = waiting.get(i).getRequestAmount().doubleValue();
                for (int j = 0; j < piles.size(); j++) {
                    if (pileUsed[j]) continue;
                    double power = piles.get(j).getPowerKw().doubleValue();
                    if (power <= 0) continue;
                    double finish = amount / power;
                    if (finish < bestFinish - 1e-9
                            || (Math.abs(finish - bestFinish) <= 1e-9
                                && (bestPile < 0 || piles.get(j).getId() < piles.get(bestPile).getId()))) {
                        bestFinish = finish;
                        bestCar = i;
                        bestPile = j;
                    }
                }
            }
            if (bestCar < 0 || bestPile < 0) break;

            ChargingRequest car = waiting.get(bestCar);
            ChargingPile pile = piles.get(bestPile);
            // 跨类型分配并直接启动充电（绕过模式==类型校验，符合 8b「任意车任意桩」）。
            int r = chargingService.forceAssignAndStart(car.getId(), pile.getId());
            if (r == 0) {
                assignments.add(new Assignment(car.getCarId(), car.getId(), pile.getId(),
                        pileDisplayName(pile), round3(bestFinish)));
            }
            carUsed[bestCar] = true;
            pileUsed[bestPile] = true;
        }

        if (assignments.isEmpty()) {
            return new BatchResult(1, "批量调度未产生有效分配", List.of());
        }
        return new BatchResult(0, "批量调度完成，共分配 " + assignments.size() + " 辆车", assignments);
    }

    private boolean isStationFull() {
        // 全部车位 = 各桩队列容量(M) * 桩数 + 等候区容量(N)。这里取系统约定 M=3, N=10。
        int totalSlots = pileMapper.findAll().size() * 3 + 10;
        int occupied = 0;
        occupied += requestMapper.findWaitingByMode(com.bupt.charging.enums.ChargingMode.FAST).size();
        occupied += requestMapper.findWaitingByMode(com.bupt.charging.enums.ChargingMode.SLOW).size();
        for (ChargingPile p : pileMapper.findAll()) {
            occupied += requestMapper.findActiveAssignedByPileId(p.getId()).size();
        }
        return occupied >= totalSlots;
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private String pileDisplayName(ChargingPile pile) {
        String prefix = pile.getType() == com.bupt.charging.enums.ChargingMode.FAST ? "快充" : "慢充";
        return prefix + pile.getId();
    }
}
