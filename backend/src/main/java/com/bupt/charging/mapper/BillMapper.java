package com.bupt.charging.mapper;

import com.bupt.charging.entity.Bill;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BillMapper {
    @Insert("INSERT INTO bill(record_id, request_id, root_request_id, car_id, date, pile_id, charge_amount, charge_duration, " +
            "total_charge_fee, total_service_fee, total_fee, detail_count) " +
            "VALUES(#{recordId}, #{requestId}, #{rootRequestId}, #{carId}, #{date}, #{pileId}, #{chargeAmount}, #{chargeDuration}, " +
            "#{totalChargeFee}, #{totalServiceFee}, #{totalFee}, #{detailCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Bill bill);

    @Update("UPDATE bill SET charge_amount = #{chargeAmount}, charge_duration = #{chargeDuration}, " +
            "total_charge_fee = #{totalChargeFee}, total_service_fee = #{totalServiceFee}, " +
            "total_fee = #{totalFee}, detail_count = #{detailCount}, pile_id = #{pileId} WHERE id = #{id}")
    int update(Bill bill);

    @Select("SELECT * FROM bill WHERE id = #{id}")
    Bill findById(@Param("id") Long id);

    @Select("SELECT * FROM bill WHERE record_id = #{recordId}")
    Bill findByRecordId(@Param("recordId") Long recordId);

    @Select("SELECT * FROM bill WHERE root_request_id = #{rootRequestId} LIMIT 1")
    Bill findByRootRequestId(@Param("rootRequestId") Long rootRequestId);

    @Select("SELECT * FROM bill WHERE request_id = #{requestId} ORDER BY id ASC")
    List<Bill> findByRequestId(@Param("requestId") Long requestId);

    @Select("SELECT * FROM bill WHERE car_id = #{carId} AND date = #{date} ORDER BY id DESC")
    List<Bill> findByCarIdAndDate(@Param("carId") String carId, @Param("date") LocalDate date);
}
