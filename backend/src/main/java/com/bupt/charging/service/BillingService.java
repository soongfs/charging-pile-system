package com.bupt.charging.service;

import com.bupt.charging.entity.*;
import com.bupt.charging.mapper.BillMapper;
import com.bupt.charging.mapper.ChargingRequestMapper;
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

    public BillingService(PricingService pricingService, BillMapper billMapper,
                          ChargingRequestMapper requestMapper, PileService pileService) {
        this.pricingService = pricingService;
        this.billMapper = billMapper;
        this.requestMapper = requestMapper;
        this.pileService = pileService;
    }

    public BigDecimal[] calculateFee(ChargingRecord record) {
        ChargingMetrics metrics = calculateMetrics(record, LocalDateTime.now());
        return new BigDecimal[]{metrics.chargeFee(), metrics.serviceFee()};
    }

    public ChargingMetrics calculateMetrics(ChargingRecord record) {
        return calculateMetrics(record, LocalDateTime.now());
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
     * Generate bill after charging ends.
     */
    public Bill generateBill(ChargingRecord record) {
        Bill bill = new Bill();
        bill.setRecordId(record.getId());
        bill.setRequestId(record.getRequestId());
        bill.setCarId(record.getCarId());
        bill.setDate(record.getEndTime() == null ? LocalDateTime.now().toLocalDate() : record.getEndTime().toLocalDate());
        bill.setPileId(record.getPileId());
        bill.setChargeAmount(record.getChargeAmount());
        bill.setTotalChargeFee(record.getChargeFee());
        bill.setTotalServiceFee(record.getServiceFee());
        bill.setTotalFee(record.getChargeFee().add(record.getServiceFee()));

        // Duration
        LocalDateTime start = record.getStartTime();
        LocalDateTime end = record.getEndTime();
        bill.setChargeDuration((int) ChronoUnit.SECONDS.between(start, end));

        billMapper.insert(bill);
        return bill;
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
        if (!time.isBefore(LocalTime.of(8, 0)) && time.isBefore(LocalTime.of(11, 0))) {
            return config.getNormalPrice();
        }
        if (!time.isBefore(LocalTime.of(11, 0)) && time.isBefore(LocalTime.of(15, 0))) {
            return config.getPeakPrice();
        }
        if (!time.isBefore(LocalTime.of(15, 0)) && time.isBefore(LocalTime.of(18, 0))) {
            return config.getNormalPrice();
        }
        if (!time.isBefore(LocalTime.of(18, 0)) && time.isBefore(LocalTime.of(22, 0))) {
            return config.getPeakPrice();
        }
        return config.getValleyPrice();
    }

    private LocalDateTime nextPriceBoundary(LocalDateTime time) {
        LocalTime current = time.toLocalTime();
        LocalTime[] boundaries = {
                LocalTime.of(8, 0),
                LocalTime.of(11, 0),
                LocalTime.of(15, 0),
                LocalTime.of(18, 0),
                LocalTime.of(22, 0)
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
