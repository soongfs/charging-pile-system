package com.bupt.charging.controller;

import com.bupt.charging.entity.Bill;
import com.bupt.charging.dto.BillDetailResponse;
import com.bupt.charging.service.BillService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/bill")
public class BillController {
    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping("/{carId}")
    public Map<String, Object> requestBill(@PathVariable String carId, @RequestParam LocalDate date) {
        List<Bill> bills = billService.listBills(carId, date);
        return ApiResponse.success(bills);
    }

    @GetMapping("/detail/{billId}")
    public Map<String, Object> requestDetailedList(@PathVariable Long billId) {
        BillDetailResponse detail = billService.getBillDetail(billId);
        return detail != null ? ApiResponse.success(detail) : ApiResponse.failure("账单不存在");
    }
}
