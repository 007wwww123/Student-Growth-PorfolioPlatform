package com.college.growth.ledger.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 台账 mapper XML 装载测试。
 *
 * <p>台账明细的手写 SQL 里有 UNION ALL、嵌套 include 与多段过滤片段，
 * 这类结构错误（标签不闭合、include 指向不存在的片段、resultType 拼错）
 * 只在应用启动或真实查询时才暴露，单测又不需要连库。这里直接用
 * MyBatis 的 {@link XMLMapperBuilder} 解析一遍，把「能不能启动」提前到构建阶段。
 *
 * <p>SQL 语义（联表、计数口径）仍由真库联调覆盖，本测试不连数据库。</p>
 */
class LedgerMapperXmlTest {

    private static final String RESOURCE = "mapper/ledger/LedgerMapper.xml";

    private static final String NAMESPACE = "com.college.growth.ledger.mapper.LedgerMapper";

    @Test
    @DisplayName("台账 mapper XML 可被 MyBatis 解析，三条语句齐全")
    void mapperXmlLoads() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertTrue(input != null, "找不到 " + RESOURCE);
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }

        assertTrue(configuration.hasStatement(NAMESPACE + ".selectClassStats"),
                "缺少班级汇总语句");
        assertTrue(configuration.hasStatement(NAMESPACE + ".countTasks"),
                "缺少明细计数语句");
        assertTrue(configuration.hasStatement(NAMESPACE + ".selectTaskPage"),
                "缺少明细列表语句");
    }
}
