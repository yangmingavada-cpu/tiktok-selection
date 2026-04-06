package com.tiktok.selection.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * PostgreSQL TIMESTAMPTZ → LocalDateTime 类型处理器
 * <p>
 * PostgreSQL JDBC 42.x 起不允许将 TIMESTAMPTZ 列直接映射为 LocalDateTime，
 * 须先读取为 OffsetDateTime 再转换。统一以 UTC 偏移量处理。
 *
 * @author system
 * @date 2026/03/24
 */
@MappedTypes(LocalDateTime.class)
@MappedJdbcTypes(value = JdbcType.TIMESTAMP_WITH_TIMEZONE, includeNullJdbcType = true)
public class TimestamptzTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter.atOffset(ZoneOffset.UTC));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime odt = rs.getObject(columnName, OffsetDateTime.class);
        return odt == null ? null : odt.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        OffsetDateTime odt = rs.getObject(columnIndex, OffsetDateTime.class);
        return odt == null ? null : odt.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        OffsetDateTime odt = cs.getObject(columnIndex, OffsetDateTime.class);
        return odt == null ? null : odt.toLocalDateTime();
    }
}
