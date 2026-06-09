package com.bupt.charging.dto;

import com.bupt.charging.entity.Bill;
import com.bupt.charging.entity.ChargingRecord;

import java.util.List;

public record BillDetailResponse(
        Bill bill,
        List<ChargingRecord> records
) {}
