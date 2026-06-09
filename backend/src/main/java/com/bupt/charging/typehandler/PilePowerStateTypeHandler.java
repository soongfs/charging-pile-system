package com.bupt.charging.typehandler;

import com.bupt.charging.enums.PilePowerState;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(PilePowerState.class)
@MappedJdbcTypes(value = JdbcType.VARCHAR, includeNullJdbcType = true)
public class PilePowerStateTypeHandler extends BaseTypeHandler<PilePowerState> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PilePowerState parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getValue());
    }

    @Override
    public PilePowerState getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return PilePowerState.fromValue(rs.getString(columnName));
    }

    @Override
    public PilePowerState getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return PilePowerState.fromValue(rs.getString(columnIndex));
    }

    @Override
    public PilePowerState getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return PilePowerState.fromValue(cs.getString(columnIndex));
    }
}
