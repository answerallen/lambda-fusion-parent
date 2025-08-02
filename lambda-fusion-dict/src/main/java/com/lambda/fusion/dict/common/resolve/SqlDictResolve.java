package com.lambda.fusion.dict.common.resolve;

import static com.lambda.fusion.dict.common.constants.DictConstants.*;

import com.lambda.fusion.dict.common.model.DictValueType;
import com.lambda.fusion.dict.common.model.DynamicDict;
import com.lambda.fusion.dict.dao.entity.DictType;
import com.lambda.fusion.dict.dao.mapper.DictSqlMapper;
import jakarta.annotation.Resource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Service;

/**
 * @author Jin
 */
@Slf4j
@Service
public class SqlDictResolve implements IDynamicDictResolve {

    @Resource
    protected DictSqlMapper dictSqlMapper;

    // SQL解析常量已移至 DictConstants 类中

    private static final CCJSqlParserManager PARSER_MANAGER = new CCJSqlParserManager();

    @Override
    public boolean isSupport(Integer valueType) {
        return DictValueType.SQL_DICT.getValueType().equals(valueType);
    }

    @Override
    public List<DynamicDict> doResolve(DictType dictType) {
        final String sql = dictType.getDataTypeValue();
        List<DynamicDict> result = new ArrayList<>();
        try {
            final Statement parse = PARSER_MANAGER.parse(new StringReader(sql));
            if (parse instanceof Select) {
                final List<LinkedHashMap<String, Object>> list = dictSqlMapper.applySql(sql);
                this.addResult(list, result);
            } else {
                log.warn("{} Illegal SQL", dictType.getId());
            }
        } catch (JSQLParserException e) {
            log.error(ERROR_JSQL_PARSER_EXCEPTION, e);
        }
        return result;
    }

    @SuppressWarnings("squid:S3776")
    private void addResult(List<LinkedHashMap<String, Object>> list, List<DynamicDict> result) {
        for (LinkedHashMap<String, Object> map : list) {
            DynamicDict dict = new DynamicDict();
            if (map.containsKey(SQL_KEY) && map.containsKey(SQL_VAL)) {
                dict.setKey(map.get(SQL_KEY).toString());
                dict.setVal(map.get(SQL_VAL));
                dict.setSelectable((Integer) map.getOrDefault(SQL_SEL, SELECTABLE_ENABLED));
                Optional.ofNullable(map.get(SQL_PID)).ifPresent(pid -> dict.setPid(pid.toString()));
                Optional.ofNullable(map.get(SQL_ID)).ifPresent(id -> dict.setId(id.toString()));
                Optional.ofNullable(map.get(SQL_RANK)).ifPresent(rank -> dict.setLevel(Math.toIntExact((Long) rank)));
            } else {
                List<Object> collect = new ArrayList<>(map.values());
                if (collect.size() == 1) {
                    continue;
                }
                final Object key = collect.get(0);
                final Object val = collect.get(1);
                dict.setKey(key.toString());
                dict.setVal(val);
                if (collect.size() > 2) {
                    Object pid = collect.get(2);
                    dict.setPid(pid.toString());
                }
                if (collect.size() > 3) {
                    Object id = collect.get(3);
                    dict.setId(id.toString());
                }
                if (collect.size() > 4) {
                    String sel = collect.get(4).toString();
                    try {
                        dict.setSelectable(Integer.parseInt(sel));
                    } catch (NumberFormatException e) {
                        dict.setSelectable(SELECTABLE_ENABLED);
                    }
                }
            }
            result.add(dict);
        }
    }
}
