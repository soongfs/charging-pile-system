package com.bupt.charging.service;

import com.bupt.charging.entity.*;
import com.bupt.charging.mapper.BillMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
import com.bupt.charging.time.TimeProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Service
public class BillingService {
    private final PricingService pricingService;
    private final BillMapper billMapper;
    private final ChargingRequestMapper requestMapper;
    private final PileService pileService;
    private final TimeProvider timeProvider;

    public BillingService(PricingService pricingService, BillMapper billMapper,
                          ChargingRequestMapper requestMapper, PileService pileService,
                          TimeProvider timeProvider) {
        this.pricingService = pricingService;
        this.billMapper = billMapper;
        this.requestMapper = requestMapper;
        this.pileService = pileService;
        this.timeProvider = timeProvider;
    }

    public BigDecimal[] calculateFee(ChargingRecord record) {
        ChargingMetrics metrics = calculateMetrics(record, timeProvider.now());
        return new BigDecimal[]{metrics.chargeFee(), metrics.serviceFee()};
    }

    public ChargingMetrics calculateMetrics(ChargingRecord record) {
        return calculateMetrics(record, timeProvider.now());
    }

    public ChargingMetrics calculateMetrics(ChargingRecord record, LocalDateTime endTime) {
        PricingConfig config = pricingService.getCurrentConfig();
        ChargingRequest request = requestMapper.findById(record.getRequestId());
        LocalDateTime start = record.getStartTime();
        long elapsedSeconds = Math.max(0, ChronoUnit.SECONDS.between(start, endTime));

        BigDecimal powerKw = pileService.getPowerKw(record.getPileId());
        double powerKwValue = powerKw.doubleValue();
        BigDecimal targetAmountValue = request == null ? null : positiveOrZero(request.getRequestAmount());
        double targetAmount = targetAmountValue == null ? Double.MAX_VALUE : targetAmountValue.doubleValue();
        long billableSeconds = elapsedSeconds;
        if (targetAmount != Double.MAX_VALUE && powerKwValue > 0) {
            long secondsToFull = (long) Math.ceil(targetAmount / powerKwValue * 3600.0);
            billableSeconds = Math.min(elapsedSeconds, secondsToFull);
        }

        double chargedAmountValue = Math.min(targetAmount, billableSeconds * powerKwValue / 3600.0);
        BigDecimal chargedAmount = round(BigDecimal.valueOf(chargedAmountValue));
        BigDecimal chargeFee = round(calculateEnergyFee(config, start, billableSeconds, powerKwValue, chargedAmountValue));
        BigDecimal serviceFee = round(chargedAmount.multiply(config.getServiceFeeRate()));
        boolean completed = targetAmountValue != null && chargedAmount.compareTo(targetAmountValue) >= 0;

        return new ChargingMetrics(
                chargedAmount,
                elapsedSeconds,
                chargeFee,
                serviceFee,
                round(chargeFee.add(serviceFee)),
                powerKw,
                targetAmountValue == null ? chargedAmount : targetAmountValue,
                completed
        );
    }

    /**
     * 生成/累加账单(按"充电请求生命周期"聚合)。
     *
     * <p>详细需求"一次账单对应至少 2 个详单": 每段充电过程(ChargingRecord)是一条详单,
     * 同一请求组(root_request_id 相同, 含故障迁移续充)归并为一张账单。首段创建账单,
     * 后续段累加电量/时长/费用并 detail_count+1。</p>
     */
    public Bill generateBill(ChargingRecord record) {
        // 解析该详单所属的请求组根。
        ChargingRequest req = requestMapper.findById(record.getRequestId());
        Long rootId = (req != null && req.getRootRequestId() != null)
                ? req.getRootRequestId()
                : record.getRequestId();

        LocalDateTime start = record.getStartTime();
        LocalDateTime end = record.getEndTime();
        int durationSec = (int) ChronoUnit.SECONDS.between(start, end);
        BigDecimal segFee = record.getChargeFee();
        BigDecimal segSvc = record.getServiceFee();

        Bill existing = billMapper.findByRootRequestId(rootId);
        if (existing == null) {
            // 首段: 新建账单。
            Bill bill = new Bill();
            bill.setRecordId(record.getId());
            bill.setRequestId(record.getRequestId());
            bill.setRootRequestId(rootId);
            bill.setCarId(record.getCarId());
            bill.setDate(end == null ? timeProvider.now().toLocalDate() : end.toLocalDate());
            bill.setPileId(record.getPileId());
            bill.setChargeAmount(record.getChargeAmount());
            bill.setTotalChargeFee(segFee);
            bill.setTotalServiceFee(segSvc);
            bill.setTotalFee(segFee.add(segSvc));
            bill.setChargeDuration(durationSec);
            bill.setDetailCount(1);
            billMapper.insert(bill);
            return bill;
        }

        // 后续段(故障续充): 累加到同一张账单。
        existing.setChargeAmount(existing.getChargeAmount().add(record.getChargeAmount()));
        existing.setTotalChargeFee(existing.getTotalChargeFee().add(segFee));
        existing.setTotalServiceFee(existing.getTotalServiceFee().add(segSvc));
        existing.setTotalFee(existing.getTotalChargeFee().add(existing.getTotalServiceFee()));
        existing.setChargeDuration(existing.getChargeDuration() + durationSec);
        existing.setDetailCount(existing.getDetailCount() + 1);
        existing.setPileId(record.getPileId());   // 反映最后充电的桩
        billMapper.update(existing);
        return existing;
    }

    private BigDecimal calculateEnergyFee(PricingConfig config, LocalDateTime start,
                                          long seconds, double powerKw, double chargedAmount) {
        long remainingSeconds = seconds;
        LocalDateTime cursor = start;
        double remainingAmount = chargedAmount;
        BigDecimal fee = BigDecimal.ZERO;

        while (remainingSeconds > 0 && remainingAmount > 0) {
            LocalDateTime boundary = nextPriceBoundary(cursor);
            long segmentSeconds = Math.min(remainingSeconds, ChronoUnit.SECONDS.between(cursor, boundary));
            if (segmentSeconds <= 0) {
                cursor = boundary;
                continue;
            }

            double segmentAmount = Math.min(remainingAmount, segmentSeconds * powerKw / 3600.0);
            fee = fee.add(BigDecimal.valueOf(segmentAmount).multiply(getPricePerKwh(config, cursor.toLocalTime())));
            remainingAmount -= segmentAmount;
            remainingSeconds -= segmentSeconds;
            cursor = cursor.plusSeconds(segmentSeconds);
        }

        return fee;
    }

    private BigDecimal getPricePerKwh(PricingConfig config, LocalTime time) {
        // 平时: 7:00-10:00, 15:00-18:00, 21:00-23:00
        if ((!time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(10, 0)))
                || (!time.isBefore(LocalTime.of(15, 0)) && time.isBefore(LocalTime.of(18, 0)))
                || (!time.isBefore(LocalTime.of(21, 0)) && time.isBefore(LocalTime.of(23, 0)))) {
            return config.getNormalPrice();
        }
        // 峰时: 10:00-15:00, 18:00-21:00
        if ((!time.isBefore(LocalTime.of(10, 0)) && time.isBefore(LocalTime.of(15, 0)))
                || (!time.isBefore(LocalTime.of(18, 0)) && time.isBefore(LocalTime.of(21, 0)))) {
            return config.getPeakPrice();
        }
        // 谷时: 23:00-次日7:00
        return config.getValleyPrice();
    }

    private LocalDateTime nextPriceBoundary(LocalDateTime time) {
        LocalTime current = time.toLocalTime();
        LocalTime[] boundaries = {
                LocalTime.of(7, 0),
                LocalTime.of(10, 0),
                LocalTime.of(15, 0),
                LocalTime.of(18, 0),
                LocalTime.of(21, 0),
                LocalTime.of(23, 0)
        };

        for (LocalTime boundary : boundaries) {
            if (current.isBefore(boundary)) {
                return time.toLocalDate().atTime(boundary);
            }
        }
        return time.toLocalDate().plusDays(1).atStartOfDay();
    }

    private BigDecimal round(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private BigDecimal positiveOrZero(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public record ChargingMetrics(
            BigDecimal chargedAmount,
            long elapsedSeconds,
            BigDecimal chargeFee,
            BigDecimal serviceFee,
            BigDecimal totalFee,
            BigDecimal powerKw,
            BigDecimal targetAmount,
            boolean completed
    ) {}
}
