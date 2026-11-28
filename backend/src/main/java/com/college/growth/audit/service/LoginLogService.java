package com.college.growth.audit.service;

import com.college.growth.audit.entity.SysLoginLog;
import com.college.growth.audit.mapper.SysLoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录日志写入（F-14 / 7.4 事件 1–3）。
 *
 * <p>登录失败时账号可能不存在，因此这个入口不依赖 UserContext；
 * 只记账号、结果、原因、IP 与 UA，绝不记录密码或令牌。</p>
 */
@Service
public class LoginLogService {

    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);

    private final SysLoginLogMapper loginLogMapper;

    public LoginLogService(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    public void success(Long userId, String username) {
        record(userId, username, 1, "LOGIN_SUCCESS");
    }

    public void failure(Long userId, String username, String reason) {
        record(userId, username, 0, reason);
    }

    public void logout(Long userId, String username) {
        record(userId, username, 1, "LOGOUT");
    }

    private void record(Long userId, String username, int success, String reason) {
        try {
            SysLoginLog entry = new SysLoginLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setSuccess(success);
            entry.setReason(reason);
            HttpServletRequest request = currentRequest();
            if (request != null) {
                entry.setIp(clientIp(request));
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            }
            loginLogMapper.insert(entry);
        } catch (Exception ex) {
            // 日志失败不应阻断登录
            log.warn("写登录日志失败 username={} success={}", username, success, ex);
        }
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
