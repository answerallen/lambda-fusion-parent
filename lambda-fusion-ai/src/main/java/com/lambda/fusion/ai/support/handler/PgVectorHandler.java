package com.lambda.fusion.ai.support.handler;

import com.pgvector.PGvector;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis TypeHandler for PgVector
 * 处理 List<Double> 与 PostgreSQL vector 类型的转换
 *
 * @author Jin
 */
@MappedTypes(List.class)
public class PgVectorHandler extends BaseTypeHandler<List<Double>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Double> parameter, JdbcType jdbcType)
            throws SQLException {
        if (parameter == null || parameter.isEmpty()) {
            ps.setNull(i, Types.OTHER);
            return;
        }
        float[] floatArray = new float[parameter.size()];
        for (int j = 0; j < parameter.size(); j++) {
            floatArray[j] = parameter.get(j).floatValue();
        }
        ps.setObject(i, new PGvector(floatArray));
    }

    @Override
    public List<Double> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseVector(rs.getString(columnName));
    }

    @Override
    public List<Double> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseVector(rs.getString(columnIndex));
    }

    @Override
    public List<Double> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseVector(cs.getString(columnIndex));
    }

    private List<Double> parseVector(String vectorStr) throws SQLException {
        if (vectorStr == null) {
            return null;
        }
        // pgvector returns string like "[1.1,2.2,3.3]"
        PGvector pgVector = new PGvector(vectorStr);
        float[] floats = pgVector.toArray();
        List<Double> list = new ArrayList<>(floats.length);
        for (float f : floats) {
            list.add((double) f);
        }
        return list;
    }
}
