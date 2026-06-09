package com.bupt.charging.mapper;

import com.bupt.charging.entity.PricingConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PricingConfigMapper {
    @Select("SELECT * FROM pricing_config WHERE id = 1")
    PricingConfig getCurrent();

    @Update("UPDATE pricing_config SET peak_price = #{peakPrice}, normal_price = #{normalPrice}, " +
            "valley_price = #{valleyPrice}, service_fee_rate = #{serviceFeeRate}, " +
            "effective_time = #{effectiveTime}, update_time = #{updateTime} WHERE id = 1")
    int update(PricingConfig config);

    @Insert("INSERT INTO pricing_config(id, peak_price, normal_price, valley_price, service_fee_rate, effective_time, update_time) " +
            "VALUES(1, #{peakPrice}, #{normalPrice}, #{valleyPrice}, #{serviceFeeRate}, #{effectiveTime}, #{updateTime}) " +
            "ON CONFLICT(id) DO UPDATE SET peak_price = excluded.peak_price, normal_price = excluded.normal_price, " +
            "valley_price = excluded.valley_price, service_fee_rate = excluded.service_fee_rate, " +
            "effective_time = excluded.effective_time, update_time = excluded.update_time")
    int upsert(PricingConfig config);
}
