package com.bupt.charging.mapper;

import com.bupt.charging.entity.User;
import com.bupt.charging.enums.UserState;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Insert("INSERT INTO user(car_id, user_name, car_capacity, password, state, create_time) " +
            "VALUES(#{carId}, #{userName}, #{carCapacity}, #{password}, #{state}, #{createTime})")
    int insert(User user);

    @Select("SELECT * FROM user WHERE car_id = #{carId}")
    User findByCarId(@Param("carId") String carId);

    @Update("UPDATE user SET password = #{password} WHERE car_id = #{carId}")
    int updatePassword(@Param("carId") String carId, @Param("password") String password);

    @Update("UPDATE user SET state = #{state} WHERE car_id = #{carId}")
    int updateState(@Param("carId") String carId, @Param("state") UserState state);

    @Update("UPDATE user SET password = #{password}, state = 'active' " +
            "WHERE car_id = #{carId} AND state = 'inactive'")
    int activateWithPassword(@Param("carId") String carId, @Param("password") String password);
}
