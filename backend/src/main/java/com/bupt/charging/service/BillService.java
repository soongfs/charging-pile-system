package com.bupt.charging.service;

import com.bupt.charging.dto.BillDetailResponse;
import com.bupt.charging.entity.Bill;
import com.bupt.charging.entity.ChargingRecord;
import com.bupt.charging.mapper.BillMapper;
import com.bupt.charging.mapper.ChargingRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class BillService {
    private final BillMapper billMapper;
    private final ChargingRecordMapper recordMapper;

    public BillService(BillMapper billMapper, ChargingRecordMapper recordMapper) {
        this.billMapper = billMapper;
        this.recordMapper = recordMapper;
    }

    public List<Bill> listBills(String carId, LocalDate date) {
        if (isBlank(carId) || date == null) {
            throw new IllegalArgumentException("车辆 ID 和账单日期不能为空");
        }
        return billMapper.findByCarIdAndDate(carId.trim(), date);
    }

    public BillDetailResponse getBillDetail(Long billId) {
        if (billId == null) {
            throw new IllegalArgumentException("账单 ID 不能为空");
        }
        Bill bill = billMapper.findById(billId);
        if (bill == null) {
            return null;
        }
        if (bill.getRecordId() != null) {
            ChargingRecord record = recordMapper.findById(bill.getRecordId());
            return new BillDetailResponse(bill, record == null ? List.of() : List.of(record));
        }
        if (bill.getRequestId() != null) {
            return new BillDetailResponse(bill, recordMapper.findByRequestId(bill.getRequestId()));
        }
        return new BillDetailResponse(
                bill,
                recordMapper.findByCarIdDateAndPile(bill.getCarId(), bill.getDate(), bill.getPileId())
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
