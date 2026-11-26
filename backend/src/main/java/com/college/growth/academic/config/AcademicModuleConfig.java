package com.college.growth.academic.config;

import org.springframework.context.annotation.Configuration;

/**
 * 学业发展板块专属配置。
 *
 * <p>预留本板块的扩展装配点：字段校验器注册、枚举转换器、板块内异步线程池等。
 * 全局配置（数据源、MyBatis-Plus、Jackson）统一放在 {@code com.college.growth.config}。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AcademicModuleConfig {
}
