package com.college.growth.common.exception;

import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import java.util.List;

/**
 * 业务异常。
 *
 * <p>业务规则（BR-xxx）被违反时抛出，由 {@link GlobalExceptionHandler} 统一转换为
 * 带业务码的响应体。禁止用该异常表达程序缺陷。</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ResultCode resultCode;

    private final transient List<FieldErrorDetail> details;

    public BusinessException(ResultCode resultCode) {
        this(resultCode, resultCode.getMessage(), null);
    }

    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode, message, null);
    }

    public BusinessException(ResultCode resultCode, String message, List<FieldErrorDetail> details) {
        super(message);
        this.resultCode = resultCode;
        this.details = details;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }

    public List<FieldErrorDetail> getDetails() {
        return details;
    }
}
