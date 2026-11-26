package com.college.growth.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化配置。
 *
 * <p>时间统一使用东八区；日期与日期时间以字符串输出，避免前端拿到时间戳后再做时区换算。
 * 响应体中的 {@code timestamp} 字段为带偏移量的 ISO-8601 格式（需求文档 6.1 示例）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        return builder -> builder
                .timeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializers(new LocalDateTimeSerializer(dateTimeFormatter))
                .serializers(new LocalDateSerializer(dateFormatter))
                .deserializers(new LocalDateTimeDeserializer(dateTimeFormatter))
                .deserializers(new LocalDateDeserializer(dateFormatter));
    }
}
