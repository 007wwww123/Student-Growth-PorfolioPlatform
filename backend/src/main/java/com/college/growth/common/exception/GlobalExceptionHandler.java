package com.college.growth.common.exception;

import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.Result;
import com.college.growth.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 *
 * <p>统一把异常映射为「HTTP 状态码 + 业务码」的组合（需求文档 6.1）：
 * 参数类异常返回 400，字段校验返回 422，业务规则冲突返回 409，未捕获异常返回 500 并记录 traceId。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务规则异常：按异常自带的业务码返回。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        ResultCode resultCode = ex.getResultCode();
        log.warn("业务异常 code={} message={}", resultCode.getCode(), ex.getMessage());
        return ResponseEntity.status(resultCode.getHttpStatus())
                .body(Result.failure(resultCode, ex.getMessage(), ex.getDetails()));
    }

    /** 请求体校验失败（@Valid）：汇总字段级明细，最多 20 条。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .limit(20)
                .map(this::toFieldErrorDetail)
                .collect(Collectors.toList());
        return ResponseEntity.status(ResultCode.FIELD_VALIDATION_FAILED.getHttpStatus())
                .body(Result.failure(ResultCode.FIELD_VALIDATION_FAILED, "字段校验未通过", details));
    }

    /** 方法参数上的约束校验失败（@RequestParam / @PathVariable）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .limit(20)
                .map(this::toFieldErrorDetail)
                .collect(Collectors.toList());
        return ResponseEntity.status(ResultCode.FIELD_VALIDATION_FAILED.getHttpStatus())
                .body(Result.failure(ResultCode.FIELD_VALIDATION_FAILED, "字段校验未通过", details));
    }

    /** 请求体缺失或 JSON 解析失败。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败");
        return ResponseEntity.status(ResultCode.REQUEST_BODY_INVALID.getHttpStatus())
                .body(Result.failure(ResultCode.REQUEST_BODY_INVALID));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleArgumentTypeMismatch() {
        return ResponseEntity.badRequest().body(Result.failure(ResultCode.PARAM_INVALID));
    }

    /** 必填请求参数缺失。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(ResultCode.PARAM_MISSING.getHttpStatus())
                .body(Result.failure(ResultCode.PARAM_MISSING, "缺少必要参数：" + ex.getParameterName()));
    }

    /** 兜底：未捕获异常统一返回 EC-50001，并记录堆栈供按 traceId 排查。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("未捕获异常", ex);
        return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.failure(ResultCode.SYSTEM_ERROR));
    }

    private FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
        return new FieldErrorDetail(fieldError.getField(), "INVALID", fieldError.getDefaultMessage());
    }

    private FieldErrorDetail toFieldErrorDetail(ConstraintViolation<?> violation) {
        return new FieldErrorDetail(violation.getPropertyPath().toString(), "INVALID", violation.getMessage());
    }
}
