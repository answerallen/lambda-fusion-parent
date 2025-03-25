package com.lamuda.cloud.scaffold.dict.support.resolve;

import com.lamuda.cloud.scaffold.dict.mapper.DictSqlMapper;
import com.lamuda.cloud.scaffold.dict.support.model.DictValueType;
import com.lamuda.cloud.scaffold.dict.support.model.DynamicDict;
import com.lamuda.cloud.fx.dict.entity.DictType;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * @author Jin
 */
@Slf4j
@Service
public class SqlDictResolve implements IDynamicDictResolve {

    @Resource
    protected DictSqlMapper dictSqlMapper;

    private static final String KEY = "k";

    private static final String VAL = "v";

    private static final String SEL = "sel";

    private static final String PID = "pid";

    private static final String ID = "id";

    private static final String RANK = "rank2";

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
            log.error("JSQLParserException ", e);
        }
        return result;
    }

    @SuppressWarnings("squid:S3776")
    private void addResult(List<LinkedHashMap<String, Object>> list, List<DynamicDict> result) {
        for (LinkedHashMap<String, Object> map : list) {
            DynamicDict dict = new DynamicDict();
            if (map.containsKey(KEY) && map.containsKey(VAL)) {
                dict.setKey(map.get(KEY).toString());
                dict.setVal(map.get(VAL));
                dict.setSelectable((Integer) map.getOrDefault(SEL, 1));
                Optional.ofNullable(map.get(PID)).ifPresent(pid -> dict.setPid(pid.toString()));
                Optional.ofNullable(map.get(ID)).ifPresent(id -> dict.setId(id.toString()));
                Optional.ofNullable(map.get(RANK)).ifPresent(rank -> dict.setLevel(Math.toIntExact((Long) rank)));
            } else {
                List<Object> collect = new ArrayList<>(map.values());
                if(collect.size() == 1){
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
                        dict.setSelectable(1);
                    }

                }
            }
            result.add(dict);
        }
    }

}
