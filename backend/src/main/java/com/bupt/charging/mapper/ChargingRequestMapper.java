package com.bupt.charging.mapper;

import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.RequestState;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ChargingRequestMapper {
    @Insert("INSERT INTO charging_request(car_id, request_amount, request_mode, request_time, car_state, pile_id, queue_num, priority, root_request_id, update_time, version) " +
            "VALUES(#{carId}, #{requestAmount}, #{requestMode}, #{requestTime}, #{carState}, #{pileId}, #{queueNum}, COALESCE(#{priority}, 0), #{rootRequestId}, #{updateTime}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChargingRequest request);

    @Select("SELECT * FROM charging_request WHERE car_id = #{carId} AND car_state = #{state}")
    ChargingRequest findByCarIdAndState(@Param("carId") String carId, @Param("state") RequestState state);

    @Select("SELECT * FROM charging_request WHERE id = #{id}")
    ChargingRequest findById(@Param("id") Long id);

    @Update("UPDATE charging_request SET root_request_id = #{rootRequestId} WHERE id = #{id}")
    int setRootRequestId(@Param("id") Long id, @Param("rootRequestId") Long rootRequestId);

    @Select("SELECT * FROM charging_request WHERE car_id = #{carId} AND car_state != 'done' AND car_state != 'cancelled' ORDER BY id DESC LIMIT 1")
    ChargingRequest findActiveByCarId(@Param("carId") String carId);

    @Update("UPDATE charging_request SET request_amount = #{requestAmount}, request_mode = #{requestMode}, " +
            "car_state = #{carState}, pile_id = #{pileId}, queue_num = #{queueNum}, priority = COALESCE(#{priority}, 0), " +
            "update_time = strftime('%Y-%m-%dT%H:%M:%f','now'), version = COALESCE(version, 0) + 1 WHERE id = #{id}")
    int update(ChargingRequest request);

    @Select("SELECT * FROM charging_request WHERE request_mode = #{mode} AND car_state IN ('waiting', 'dispatched', 'charging') " +
            "ORDER BY CASE car_state WHEN 'charging' THEN 0 WHEN 'dispatched' THEN 1 ELSE 2 END, priority DESC, request_time ASC")
    List<ChargingRequest> findByQueueType(@Param("mode") ChargingMode mode);

    @Select("SELECT * FROM charging_request WHERE car_state = 'waiting' AND request_mode = #{mode} ORDER BY priority DESC, request_time ASC")
    List<ChargingRequest> findWaitingByMode(@Param("mode") ChargingMode mode);

    @Select("SELECT * FROM charging_request WHERE pile_id = #{pileId} AND car_state IN ('dispatched', 'charging') ORDER BY request_time ASC")
    List<ChargingRequest> findActiveAssignedByPileId(@Param("pileId") Integer pileId);

    @Select("SELECT * FROM charging_request WHERE pile_id = #{pileId} AND car_state = 'dispatched' ORDER BY request_time ASC")
    List<ChargingRequest> findDispatchedByPileId(@Param("pileId") Integer pileId);

    @Update("UPDATE charging_request SET car_state = 'dispatched', pile_id = #{pileId}, queue_num = 0, " +
            "update_time = strftime('%Y-%m-%dT%H:%M:%f','now'), version = COALESCE(version, 0) + 1 " +
            "WHERE id = #{requestId} AND car_state = 'waiting' AND pile_id IS NULL")
    int assignPileIfWaiting(@Param("requestId") Long requestId, @Param("pileId") Integer pileId);

    @Update("UPDATE charging_request SET car_state = #{targetState}, pile_id = #{pileId}, queue_num = #{queueNum}, " +
            "update_time = strftime('%Y-%m-%dT%H:%M:%f','now'), version = COALESCE(version, 0) + 1 " +
            "WHERE id = #{requestId} AND car_state = #{currentState}")
    int updateStateIfCurrent(@Param("requestId") Long requestId,
                             @Param("currentState") RequestState currentState,
                             @Param("targetState") RequestState targetState,
                             @Param("pileId") Integer pileId,
                             @Param("queueNum") Integer queueNum);
}
