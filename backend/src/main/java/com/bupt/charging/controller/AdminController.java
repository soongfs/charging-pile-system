package com.bupt.charging.controller;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.dto.PileCommandRequest;
import com.bupt.charging.dto.PricingUpdateRequest;
import com.bupt.charging.service.AdminMonitorService;
import com.bupt.charging.service.BatchSchedulingService;
import com.bupt.charging.service.ChargingService;
import com.bupt.charging.service.PileService;
import com.bupt.charging.service.PricingService;
import com.bupt.charging.service.SchedulingModeHolder;
import com.bupt.charging.enums.SchedulingMode;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final PileService pileService;
    private final PricingService pricingService;
    private final AdminMonitorService monitorService;
    private final ChargingService chargingService;
    private final BatchSchedulingService batchSchedulingService;
    private final SchedulingModeHolder schedulingModeHolder;

    public AdminController(PileService pileService,
                           PricingService pricingService,
                           AdminMonitorService monitorService,
                           ChargingService chargingService,
                           BatchSchedulingService batchSchedulingService,
                           SchedulingModeHolder schedulingModeHolder) {
        this.pileService = pileService;
        this.pricingService = pricingService;
        this.monitorService = monitorService;
        this.chargingService = chargingService;
        this.batchSchedulingService = batchSchedulingService;
        this.schedulingModeHolder = schedulingModeHolder;
    }

    @PostMapping("/pile/power-on")
    public Map<String, Object> powerOn(@RequestBody PileCommandRequest body) {
        int result = pileService.powerOn(body.pileId());
        return ApiResponse.fromResult(result, "充电桩不存在或已经开机");
    }

    @PostMapping("/pile/fault")
    public Map<String, Object> fault(@RequestBody PileCommandRequest body) {
        int result = chargingService.faultPile(body.pileId());
        return ApiResponse.fromResult(result, "充电桩不存在");
    }

    @PostMapping("/pile/recover")
    public Map<String, Object> recover(@RequestBody PileCommandRequest body) {
        int result = pileService.recoverPile(body.pileId());
        return ApiResponse.fromResult(result, "充电桩不存在或未处于故障状态");
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

    /**
     * 8b 批量调度演示端点（可选加分）。仅在 charging.scheduling.mode=BATCH_SHORTEST 时生效。
     * force=true 跳过「满站」前置校验便于演示。
     */
    @PostMapping("/batch-dispatch")
    public Map<String, Object> batchDispatch(@RequestParam(defaultValue = "false") boolean force) {
        BatchSchedulingService.BatchResult result = batchSchedulingService.dispatchBatch(force);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.code() == 0);
        body.put("message", result.message());
        body.put("assignments", result.assignments());
        return body;
    }

    /** 查询当前调度模式（BASIC / SINGLE_SHORTEST / BATCH_SHORTEST）。 */
    @GetMapping("/scheduling/mode")
    public Map<String, Object> getSchedulingMode() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", schedulingModeHolder.getMode().name());
        return ApiResponse.success(data);
    }

    /**
     * 运行期切换调度模式（管理员）。BASIC=基础(默认必做)，
     * SINGLE_SHORTEST=8a 单次调度，BATCH_SHORTEST=8b 批量调度（可选加分）。
     */
    @PostMapping("/scheduling/mode")
    public Map<String, Object> setSchedulingMode(@RequestBody Map<String, String> body) {
        String raw = body == null ? null : body.get("mode");
        if (raw == null || raw.isBlank()) {
            return ApiResponse.fromResult(1, "缺少 mode 参数");
        }
        try {
            schedulingModeHolder.setMode(SchedulingMode.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fromResult(1, "未知调度模式: " + raw);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", schedulingModeHolder.getMode().name());
        return ApiResponse.success(data);
    }
}
