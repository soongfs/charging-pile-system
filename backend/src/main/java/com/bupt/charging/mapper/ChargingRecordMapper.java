package com.bupt.charging.mapper;

import com.bupt.charging.entity.ChargingRecord;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ChargingRecordMapper {
    @Insert("INSERT INTO charging_record(car_id, request_id, pile_id, start_time, end_time, charge_amount, charge_fee, service_fee) " +
            "VALUES(#{carId}, #{requestId}, #{pileId}, #{startTime}, #{endTime}, #{chargeAmount}, #{chargeFee}, #{serviceFee})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChargingRecord record);

    @Select("SELECT * FROM charging_record WHERE car_id = #{carId} AND end_time IS NULL ORDER BY id DESC LIMIT 1")
    ChargingRecord findActiveByCarId(@Param("carId") String carId);

    @Select("SELECT * FROM charging_record WHERE end_time IS NULL ORDER BY id ASC")
    List<ChargingRecord> findAllActive();

    @Select("SELECT * FROM charging_record WHERE pile_id = #{pileId} AND end_time IS NULL ORDER BY id DESC LIMIT 1")
    ChargingRecord findActiveByPileId(@Param("pileId") Integer pileId);

    @Update("UPDATE charging_record SET end_time = #{endTime}, charge_amount = #{chargeAmount}, " +
            "charge_fee = #{chargeFee}, service_fee = #{serviceFee} WHERE id = #{id}")
    int update(ChargingRecord record);

    @Select("SELECT * FROM charging_record WHERE id = #{id}")
    ChargingRecord findById(@Param("id") Long id);

    @Select("SELECT * FROM charging_record WHERE car_id = #{carId} AND date(start_time) = #{date}")
    List<ChargingRecord> findByCarIdAndDate(@Param("carId") String carId, @Param("date") LocalDate date);

    @Select("SELECT * FROM charging_record WHERE car_id = #{carId} AND date(start_time) = #{date} " +
            "AND pile_id = #{pileId} ORDER BY start_time ASC")
    List<ChargingRecord> findByCarIdDateAndPile(@Param("carId") String carId,
                                                @Param("date") LocalDate date,
                                                @Param("pileId") Integer pileId);

    @Select("SELECT * FROM charging_record WHERE request_id = #{requestId} ORDER BY id ASC")
    List<ChargingRecord> findByRequestId(@Param("requestId") Long requestId);

    @Select("SELECT cr.* FROM charging_record cr " +
            "JOIN charging_request rq ON cr.request_id = rq.id " +
            "WHERE rq.root_request_id = #{rootRequestId} ORDER BY cr.id ASC")
    List<ChargingRecord> findByRootRequestId(@Param("rootRequestId") Long rootRequestId);

    @Select("SELECT * FROM charging_record WHERE car_id = #{carId}")
    List<ChargingRecord> findByCarId(@Param("carId") String carId);
}
