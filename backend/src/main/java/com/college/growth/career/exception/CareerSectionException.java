package com.college.growth.career.exception;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;

/**
 * 职业规划板块业务异常。
 *
 * <p>用于标识异常来源板块；业务码仍复用全局 {@link ResultCode}，避免各板块自定义错误码造成口径分裂。</p>
 */
public class CareerSectionException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public CareerSectionException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }
}
