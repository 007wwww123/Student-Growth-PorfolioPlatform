package com.college.growth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务总开关。
 *
 * <p>本应用只有一个周期性任务（完成标记补偿重算），但它属于「派生数据自愈」能力，
 * 后续的统计快照、过期授权清理都会挂在这里，因此单独开一个配置类，
 * 而不是把 {@code @EnableScheduling} 塞到启动类上——启动类只负责启动。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
