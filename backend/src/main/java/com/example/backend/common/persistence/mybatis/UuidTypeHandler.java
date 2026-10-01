package com.example.backend.common.persistence.mybatis;

import java.sql.*;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

// PostgreSQL UUID를 VARCHAR 대신 네이티브 UUID로 바인딩한다.
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType jdbcType) throws SQLException {
        statement.setObject(index, value);
    }
    @Override
    public UUID getNullableResult(ResultSet result, String column) throws SQLException { return result.getObject(column, UUID.class); }
    @Override
    public UUID getNullableResult(ResultSet result, int column) throws SQLException { return result.getObject(column, UUID.class); }
    @Override
    public UUID getNullableResult(CallableStatement statement, int column) throws SQLException {
        return statement.getObject(column, UUID.class);
    }
}
