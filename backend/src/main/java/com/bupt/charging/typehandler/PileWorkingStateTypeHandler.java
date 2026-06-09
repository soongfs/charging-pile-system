package com.bupt.charging.typehandler;

import com.bupt.charging.enums.PileWorkingState;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(PileWorkingState.class)
@MappedJdbcTypes(value = JdbcType.VARCHAR, includeNullJdbcType = true)
public class PileWorkingStateTypeHandler extends BaseTypeHandler<PileWorkingState> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PileWorkingState parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getValue());
    }

    @Override
    public PileWorkingState getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return PileWorkingState.fromValue(rs.getString(columnName));
    }

    @Override
    public PileWorkingState getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return PileWorkingState.fromValue(rs.getString(columnIndex));
    }

    @Override
    public PileWorkingState getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return PileWorkingState.fromValue(cs.getString(columnIndex));
    }
}
