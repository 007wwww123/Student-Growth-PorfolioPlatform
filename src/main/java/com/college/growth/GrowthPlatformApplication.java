package com.college.growth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 学生成长档案平台启动入口。
 *
 * <p>模块化单体：按业务板块分包（basic / academic / interest / life / career / mentor），
 * 板块之间只允许通过 Service 接口调用，禁止跨板块直接访问他表（需求文档 9.1 工程约定）。</p>
 */
@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class GrowthPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrowthPlatformApplication.class, args);
    }
}
