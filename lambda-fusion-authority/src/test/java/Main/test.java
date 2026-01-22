package Main;

import cn.hutool.core.util.IdUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.db.Entity;
import cn.hutool.db.ds.simple.SimpleDataSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class test {

    @Data
    public static class Region {
        @JsonProperty("code")
        private String areaCode;
        @JsonProperty("name")
        private String areaName;
        @JsonProperty("parent_code")
        private String parentCode;
        private Integer level;
        private Integer depth;
        private String type;

    }

    public static void main(String[] args) throws Exception {
        SimpleDataSource simpleDataSource = new SimpleDataSource(
                "jdbc:mysql://rm-m5eow36sp34v0k5w5fo.mysql.rds.aliyuncs.com:3306/lambda_cloud?useSSL=false&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true"
                , "lambda_fusion",
                "001Asd11!@#"
        );

        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        JsonFactory factory = mapper.getFactory();

        try (InputStream is = Files.newInputStream(new File("F:\\developer\\git\\lambda-fusion-parent\\docs\\regions_20251224_142640.json").toPath());
             JsonParser parser = factory.createParser(is)) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("JSON 不是数组");
            }

            List<Entity> batch1 = new ArrayList<>(1000);
            List<Entity> batch2 = new ArrayList<>(1000);
            List<Entity> batch3 = new ArrayList<>(1000);
            List<Entity> batch4 = new ArrayList<>(1000);
            long totalCount = 0;
            long batchCount = 0;
            long startTime = System.currentTimeMillis();

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                Region region = mapper.readValue(parser, Region.class);
                Entity laArea = Entity.create("la_area").parseBean(region, true, true);
                laArea.set("id", IdUtil.getSnowflakeNextId());
                if (region.getLevel() == 1) {
                    batch1.add(laArea);
                }
                if (region.getLevel() == 2) {
                    batch2.add(laArea);
                }
                if (region.getLevel() == 3) {
                    batch3.add(laArea);
                }
                if (region.getLevel() == 4) {
                    batch4.add(laArea);
                }
                totalCount++;
                if (batch1.size() == 1000) {
                    long currentTimeMillis = System.currentTimeMillis();
                    DbUtil.use(simpleDataSource).insert(batch1);
                    batch1.clear();
                    batchCount++;
                    long cost = System.currentTimeMillis() - currentTimeMillis;
                    log.info("导入LEVEL - 1 第{}批次完成，耗时={} ms", batchCount, cost);
                }

                if (batch2.size() == 1000) {
                    long currentTimeMillis = System.currentTimeMillis();
                    DbUtil.use(simpleDataSource).insert(batch2);
                    batch2.clear();
                    batchCount++;
                    long cost = System.currentTimeMillis() - currentTimeMillis;
                    log.info("导入LEVEL - 2 第{}批次完成，耗时={} ms", batchCount, cost);
                }

                if (batch3.size() == 1000) {
                    long currentTimeMillis = System.currentTimeMillis();
                    DbUtil.use(simpleDataSource).insert(batch3);
                    batch3.clear();
                    batchCount++;
                    long cost = System.currentTimeMillis() - currentTimeMillis;
                    log.info("导入LEVEL - 3 第{}批次完成，耗时={} ms", batchCount, cost);
                }

                if (batch4.size() == 1000) {
                    long currentTimeMillis = System.currentTimeMillis();
                    DbUtil.use(simpleDataSource).insert(batch4);
                    batch4.clear();
                    batchCount++;
                    long cost = System.currentTimeMillis() - currentTimeMillis;
                    log.info("导入LEVEL - 4 第{}批次完成，耗时={} ms", batchCount, cost);
                }
            }

            if (!batch1.isEmpty()) {
                DbUtil.use(simpleDataSource).tx(db -> db.insert(batch1));
            }
            if (!batch2.isEmpty()) {
                DbUtil.use(simpleDataSource).tx(db -> db.insert(batch2));
            }
            if (!batch3.isEmpty()) {
                DbUtil.use(simpleDataSource).tx(db -> db.insert(batch3));
            }
            if (!batch4.isEmpty()) {
                DbUtil.use(simpleDataSource).tx(db -> db.insert(batch4));
            }

            long cost = System.currentTimeMillis() - startTime;
            log.info("行政区数据导入完成，总条数={}，耗时={} ms", totalCount, cost);

        }
    }
}
