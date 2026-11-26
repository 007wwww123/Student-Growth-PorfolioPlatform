package com.college.growth.common.result;

import com.college.growth.common.constant.CommonConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.MDC;

/**
 * 统一响应体。
 *
 * <p>结构固定为 {@code code / message / traceId / timestamp / data / details}
 * （需求文档 6.1「错误响应体固定结构」），所有接口一律返回该包装，
 * 前端只需按 {@code code} 分支即可。</p>
 *
 * @param <T> 业务数据类型
 */
@Getter
@Setter
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码，成功为 "0"，失败为 EC-xxxxx。 */
    private String code;

    /** 提示文案，可直接展示。 */
    private String message;

    /** 链路追踪号，同一请求的日志可据此串联。 */
    private String traceId;

    /** 服务端时间，带时区偏移，格式与需求文档 6.1 示例一致。 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime timestamp;

    /** 业务数据。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    /** 字段级错误明细，仅字段校验失败时出现。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FieldErrorDetail> details;

    public Result() {
        this.timestamp = OffsetDateTime.now();
        this.traceId = MDC.get(CommonConstants.TRACE_ID);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> failure(ResultCode resultCode) {
        return failure(resultCode, resultCode.getMessage(), null);
    }

    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return failure(resultCode, message, null);
    }

    public static <T> Result<T> failure(ResultCode resultCode, String message, List<FieldErrorDetail> details) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(message);
        result.setDetails(details);
        return result;
    }
}
