package com.college.growth.common.web;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestVersion {
    private RequestVersion() {}

    public static long required() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        String header = attrs.getRequest().getHeader("If-Match");
        if (header == null) throw new BusinessException(ResultCode.PARAM_MISSING, "缺少档案版本 If-Match");
        try {
            long version = Long.parseLong(header);
            if (version >= 0) return version;
        } catch (NumberFormatException ignored) {
            // Handled as an invalid precondition below.
        }
        throw new BusinessException(ResultCode.PARAM_INVALID, "档案版本格式错误");
    }
}
