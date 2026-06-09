package com.bupt.charging.service;

import com.bupt.charging.entity.*;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;
import com.bupt.charging.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class PileService {
    private final ChargingPileMapper pileMapper;
    private final QueueService queueService;
    private final ChargingStateMachine stateMachine;

    public PileService(ChargingPileMapper pileMapper,
                       QueueService queueService,
                       ChargingStateMachine stateMachine) {
        this.pileMapper = pileMapper;
        this.queueService = queueService;
        this.stateMachine = stateMachine;
    }

    /**
     * UC4: powerOn — Turn on a charging pile.
     */
    @Transactional
    public int powerOn(Integer pileId) {
        ChargingPile pile = pileMapper.findById(pileId);
        if (pile == null || pile.getPowerState() == PilePowerState.ON) return 1;
        int rows = pileMapper.updatePowerAndWorkingState(pileId, PilePowerState.ON, PileWorkingState.IDLE);
        if (rows > 0) {
            queueService.dispatchNext(pile.getType());
        }
        return rows > 0 ? 0 : 1;
    }

    /**
     * UC4: Start_ChargingPile — Set pile to running state.
     */
    @Transactional
    public int startChargingPile(Integer pileId) {
        ChargingPile pile = pileMapper.findById(pileId);
        if (!stateMachine.canStartPile(pile)) return 1;
        int rows = pileMapper.updateWorkingState(pileId, PileWorkingState.RUNNING);
        if (rows > 0) {
            queueService.dispatchNext(pile.getType());
        }
        return rows > 0 ? 0 : 1;
    }

    /**
     * UC4: powerOff — Turn off a charging pile.
     */
    @Transactional
    public int powerOff(Integer pileId) {
        ChargingPile pile = pileMapper.findById(pileId);
        if (!stateMachine.canPowerOff(pile)) return 1;
        int rows = pileMapper.updatePowerAndWorkingState(pileId, PilePowerState.OFF, PileWorkingState.OFF);
        if (rows > 0) {
            queueService.releaseDispatchedForPile(pileId);
        }
        return rows > 0 ? 0 : 1;
    }

    /**
     * UC5: Query_PileState — Query pile status.
     */
    public List<ChargingPile> queryPileState(Integer pileId) {
        if (pileId != null) {
            ChargingPile pile = pileMapper.findById(pileId);
            return pile != null ? List.of(pile) : List.of();
        }
        return pileMapper.findAll();
    }

    /**
     * Update working state (used internally by ChargingService).
     */
    public void updateWorkingState(Integer pileId, PileWorkingState state) {
        pileMapper.updateWorkingState(pileId, state);
    }

    public boolean canStartCharging(Integer pileId, ChargingMode requestMode) {
        ChargingPile pile = pileMapper.findById(pileId);
        return stateMachine.canAcceptCharging(pile, requestMode);
    }

    public void addCompletedCharging(Integer pileId, long seconds, BigDecimal amount) {
        BigDecimal safeAmount = amount == null || amount.signum() < 0 ? BigDecimal.ZERO : amount;
        pileMapper.incrementStatistics(pileId, Math.max(0, seconds), safeAmount);
    }

    public BigDecimal getPowerKw(Integer pileId) {
        ChargingPile pile = pileMapper.findById(pileId);
        if (pile == null) return ChargingMode.SLOW.getDefaultPowerKw();
        if (pile.getPowerKw() != null && pile.getPowerKw().signum() > 0) {
            return pile.getPowerKw();
        }
        return pile.getType() == null ? ChargingMode.SLOW.getDefaultPowerKw() : pile.getType().getDefaultPowerKw();
    }

    public ChargingMode getMode(ChargingPile pile) {
        return pile == null ? null : pile.getType();
    }
}
