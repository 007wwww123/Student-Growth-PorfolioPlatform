package com.college.growth.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置。
 *
 * <p>Mapper 接口使用 {@code @Mapper} 注解，由自动配置按启动类所在包扫描，
 * 因此这里不再声明 {@code @MapperScan}，避免把 Service 接口误注册为 Mapper。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁：arc_profile.version 由 @Version 维护，并发保存/审核冲突时返回 EC-40907
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页：班级台账、审计日志等列表接口使用
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 阻断全表 update / delete，防止误操作导致档案数据被整体覆盖
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
