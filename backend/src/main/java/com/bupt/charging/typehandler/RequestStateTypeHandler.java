package com.bupt.charging.typehandler;

import com.bupt.charging.enums.RequestState;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(RequestState.class)
@MappedJdbcTypes(value = JdbcType.VARCHAR, includeNullJdbcType = true)
public class RequestStateTypeHandler extends BaseTypeHandler<RequestState> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, RequestState parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getValue());
    }

    @Override
    public RequestState getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return RequestState.fromValue(rs.getString(columnName));
    }

    @Override
    public RequestState getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return RequestState.fromValue(rs.getString(columnIndex));
    }

    @Override
    public RequestState getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return RequestState.fromValue(cs.getString(columnIndex));
    }
}
