package com.bupt.charging.controller;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.dto.PileCommandRequest;
import com.bupt.charging.dto.PricingUpdateRequest;
import com.bupt.charging.service.AdminMonitorService;
import com.bupt.charging.service.PileService;
import com.bupt.charging.service.PricingService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final PileService pileService;
    private final PricingService pricingService;
    private final AdminMonitorService monitorService;

    public AdminController(PileService pileService,
                           PricingService pricingService,
                           AdminMonitorService monitorService) {
        this.pileService = pileService;
        this.pricingService = pricingService;
        this.monitorService = monitorService;
    }

    @PostMapping("/pile/power-on")
    public Map<String, Object> powerOn(@RequestBody PileCommandRequest body) {
        int result = pileService.powerOn(body.pileId());
        return ApiResponse.fromResult(result, "充电桩不存在或已经开机");
    }

    @PutMapping("/pricing")
    public Map<String, Object> setParameters(@RequestBody PricingUpdateRequest body) {
        int result = pricingService.setParameters(body);
        return ApiResponse.fromResult(result, "计费参数无效");
    }

    @PostMapping("/pile/start")
    public Map<String, Object> startChargingPile(@RequestBody PileCommandRequest body) {
        int result = pileService.startChargingPile(body.pileId());
        return ApiResponse.fromResult(result, "充电桩不存在、未开机或处于故障/充电状态");
    }

    @PostMapping("/pile/power-off")
    public Map<String, Object> powerOff(@RequestBody PileCommandRequest body) {
        int result = pileService.powerOff(body.pileId());
        return ApiResponse.fromResult(result, "充电桩不存在或正在充电");
    }

    @GetMapping("/pile/state")
    public Map<String, Object> queryPileState(@RequestParam(required = false) Integer pileId) {
        List<ChargingPile> piles = monitorService.queryPileState(pileId);
        return ApiResponse.success(piles);
    }

    @GetMapping("/queue/state")
    public Map<String, Object> queryQueueState(@RequestParam(defaultValue = "fast") String type) {
        return ApiResponse.success(monitorService.queryQueueState(type));
    }
}
