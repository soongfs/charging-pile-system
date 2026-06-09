package com.bupt.charging.typehandler;

import com.bupt.charging.enums.ChargingMode;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(ChargingMode.class)
@MappedJdbcTypes(value = {JdbcType.INTEGER, JdbcType.VARCHAR}, includeNullJdbcType = true)
public class ChargingModeTypeHandler extends BaseTypeHandler<ChargingMode> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ChargingMode parameter, JdbcType jdbcType)
            throws SQLException {
        if (jdbcType == JdbcType.VARCHAR || jdbcType == JdbcType.CHAR) {
            ps.setString(i, parameter.getPileType());
        } else {
            ps.setInt(i, parameter.getCode());
        }
    }

    @Override
    public ChargingMode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return rs.wasNull() ? null : ChargingMode.fromValue(value);
    }

    @Override
    public ChargingMode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return rs.wasNull() ? null : ChargingMode.fromValue(value);
    }

    @Override
    public ChargingMode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return cs.wasNull() ? null : ChargingMode.fromValue(value);
    }
}
