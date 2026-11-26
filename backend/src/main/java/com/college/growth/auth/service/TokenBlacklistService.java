package com.college.growth.auth.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 令牌吊销名单（登出即时失效）。
 *
 * <p>JWT 本身无状态，登出后若不记录，旧令牌在过期前仍然可用。这里把 {@code jti}
 * 写进 Redis 并设置剩余有效期作为 TTL，过滤器每次校验时查一次。</p>
 *
 * <p>Redis 不可用时选择 <b>放行</b>（fail-open）并记警告：档案平台的核心链路是填档与审核，
 * 缓存故障不应该让所有人登不进去；代价是故障窗口内登出的令牌仍有效，已在此说明。</p>
 */
@Component
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final String KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 登出时写入，TTL 取令牌剩余时间，过期后自动清理。 */
    public void revoke(String jti, long remainingSeconds) {
        if (jti == null || jti.isBlank() || remainingSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
        } catch (Exception ex) {
            log.warn("写入令牌吊销名单失败，jti={}：{}", jti, ex.getMessage());
        }
    }

    /** 是否已吊销；Redis 异常时返回 false（放行），避免缓存故障放大成登录故障。 */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception ex) {
            log.warn("查询令牌吊销名单失败，按未吊销处理：{}", ex.getMessage());
            return false;
        }
    }
}
