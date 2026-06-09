package com.bupt.charging.service;

import com.bupt.charging.dto.ChargingStateResponse;
import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.mapper.ChargingRequestMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminMonitorService {
    private final PileService pileService;
    private final ChargingRequestMapper requestMapper;
    private final QueueService queueService;

    public AdminMonitorService(PileService pileService,
                               ChargingRequestMapper requestMapper,
                               QueueService queueService) {
        this.pileService = pileService;
        this.requestMapper = requestMapper;
        this.queueService = queueService;
    }

    public List<ChargingPile> queryPileState(Integer pileId) {
        return pileService.queryPileState(pileId);
    }

    public List<ChargingStateResponse> queryQueueState(String type) {
        ChargingMode mode = ChargingMode.fromValue(type == null ? "fast" : type);
        return requestMapper.findByQueueType(mode).stream()
                .map(request -> ChargingStateResponse.from(request, carsAhead(request)))
                .toList();
    }

    private int carsAhead(ChargingRequest request) {
        return request.isWaiting() ? queueService.getPosition(request) : 0;
    }
}
