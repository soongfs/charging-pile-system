package com.bupt.charging.mapper;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ChargingPileMapper {
    @Select("SELECT * FROM charging_pile WHERE id = #{id}")
    ChargingPile findById(@Param("id") Integer id);

    @Select("SELECT * FROM charging_pile")
    List<ChargingPile> findAll();

    @Update("UPDATE charging_pile SET power_state = #{powerState}, working_state = #{workingState}, " +
            "total_charge_num = #{totalChargeNum}, total_charge_time = #{totalChargeTime}, " +
            "total_capacity = #{totalCapacity} WHERE id = #{id}")
    int update(ChargingPile pile);

    @Update("UPDATE charging_pile SET power_state = #{powerState}, working_state = #{workingState} WHERE id = #{pileId}")
    int updatePowerAndWorkingState(@Param("pileId") Integer pileId,
                                   @Param("powerState") PilePowerState powerState,
                                   @Param("workingState") PileWorkingState workingState);

    @Update("UPDATE charging_pile SET working_state = #{workingState} WHERE id = #{pileId}")
    int updateWorkingState(@Param("pileId") Integer pileId,
                           @Param("workingState") PileWorkingState workingState);

    @Update("UPDATE charging_pile SET working_state = 'idle', total_charge_num = total_charge_num + 1, " +
            "total_charge_time = total_charge_time + #{seconds}, total_capacity = total_capacity + #{amount} " +
            "WHERE id = #{pileId}")
    int incrementStatistics(@Param("pileId") Integer pileId,
                            @Param("seconds") long seconds,
                            @Param("amount") BigDecimal amount);

    @Select("SELECT * FROM charging_pile WHERE type = #{type} AND power_state = 'on' " +
            "AND working_state IN ('idle', 'running', 'charging') ORDER BY id ASC")
    List<ChargingPile> findAvailablePiles(@Param("type") String type);
}
