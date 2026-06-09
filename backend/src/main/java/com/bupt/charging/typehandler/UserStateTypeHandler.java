package com.bupt.charging.typehandler;

import com.bupt.charging.enums.UserState;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(UserState.class)
@MappedJdbcTypes(value = JdbcType.VARCHAR, includeNullJdbcType = true)
public class UserStateTypeHandler extends BaseTypeHandler<UserState> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UserState parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getValue());
    }

    @Override
    public UserState getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return UserState.fromValue(rs.getString(columnName));
    }

    @Override
    public UserState getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return UserState.fromValue(rs.getString(columnIndex));
    }

    @Override
    public UserState getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return UserState.fromValue(cs.getString(columnIndex));
    }
}
