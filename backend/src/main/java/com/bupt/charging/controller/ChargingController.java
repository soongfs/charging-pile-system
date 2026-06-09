package com.bupt.charging.controller;

import com.bupt.charging.dto.ChargingCommandRequest;
import com.bupt.charging.dto.ChargingProgressResponse;
import com.bupt.charging.dto.ChargingStateResponse;
import com.bupt.charging.dto.ModifyAmountRequest;
import com.bupt.charging.dto.ModifyModeRequest;
import com.bupt.charging.dto.SubmitChargingRequest;
import com.bupt.charging.service.ChargingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/charging")
public class ChargingController {
    private final ChargingService chargingService;

    public ChargingController(ChargingService chargingService) {
        this.chargingService = chargingService;
    }

    @PostMapping("/request")
    public Map<String, Object> submitRequest(@RequestBody SubmitChargingRequest body) {
        ChargingStateResponse data = chargingService.submitRequest(body);
        return ApiResponse.success(data);
    }

    @PutMapping("/amount")
    public Map<String, Object> modifyAmount(@RequestBody ModifyAmountRequest body) {
        int result = chargingService.modifyAmount(body);
        return ApiResponse.fromResult(result, "只能修改等待中或已分配但未开始的充电申请");
    }

    @PutMapping("/mode")
    public Map<String, Object> modifyMode(@RequestBody ModifyModeRequest body) {
        int result = chargingService.modifyMode(body);
        return ApiResponse.fromResult(result, "只能修改等待中的充电申请");
    }

    @GetMapping("/state/{carId}")
    public Map<String, Object> queryCarState(@PathVariable String carId) {
        ChargingStateResponse state = chargingService.queryCarState(carId);
        return state != null ? ApiResponse.success(state) : ApiResponse.failure("没有进行中的充电申请");
    }

    @PostMapping("/start")
    public Map<String, Object> startCharging(@RequestBody ChargingCommandRequest body) {
        int result = chargingService.startCharging(body);
        return ApiResponse.fromResult(result, "车辆未被调度、排队未到号或充电桩不可用");
    }

    @GetMapping("/progress/{carId}")
    public Map<String, Object> queryChargingState(@PathVariable String carId) {
        ChargingProgressResponse progress = chargingService.queryChargingState(carId);
        return progress != null ? ApiResponse.success(progress) : ApiResponse.failure("车辆当前未在充电");
    }

    @PostMapping("/end")
    public Map<String, Object> endCharging(@RequestBody ChargingCommandRequest body) {
        int result = chargingService.endCharging(body);
        return ApiResponse.fromResult(result, "车辆当前未在该充电桩充电");
    }
}
