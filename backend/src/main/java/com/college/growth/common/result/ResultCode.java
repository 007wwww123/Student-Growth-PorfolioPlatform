package com.college.growth.common.result;

/**
 * 业务错误码。
 *
 * <p>分段规则见需求文档 6.1：{@code EC-} 加五位数字，前三位表示类别，后两位为序号。
 * HTTP 状态码只表达传输层结果，业务码表达具体原因，前端按业务码决定提示文案与后续动作。</p>
 */
public enum ResultCode {

    /** 成功，业务码固定为 0。 */
    SUCCESS("0", 200, "操作成功"),

    REQUEST_BODY_INVALID("EC-40001", 400, "请求格式不正确，请刷新页面重试"),
    PARAM_MISSING("EC-40002", 400, "缺少必要参数"),
    PARAM_INVALID("EC-40003", 400, "参数超出允许范围"),

    UNAUTHORIZED("EC-40101", 401, "登录已失效，请重新登录"),
    TOKEN_EXPIRED("EC-40102", 401, "登录已过期，请重新登录"),
    ACCOUNT_LOCKED("EC-40103", 401, "账号已锁定，请联系管理员"),
    LOGIN_FAILED("EC-40104", 401, "账号或密码错误"),
    ACCOUNT_DISABLED("EC-40105", 401, "账号已停用，请联系管理员"),

    FIELD_NOT_EDITABLE("EC-40301", 403, "该字段由学校维护，无法修改"),
    DATA_SCOPE_DENIED("EC-40302", 403, "无权访问该学生档案"),
    PERMISSION_DENIED("EC-40303", 403, "当前角色无权执行该操作"),

    STATE_CONFLICT("EC-40902", 409, "当前状态不允许该操作"),
    WITHDRAW_EXCEEDED("EC-40903", 409, "撤回次数已用完"),
    VERSION_CONFLICT("EC-40907", 409, "内容已被他人修改，请刷新后重试"),

    FIELD_VALIDATION_FAILED("EC-42201", 422, "字段校验未通过"),
    REQUIRED_FIELD_MISSING("EC-42202", 422, "必填内容未填写"),
    RETURN_REASON_TOO_SHORT("EC-42203", 422, "退回理由不少于 10 字"),

    NOT_IMPLEMENTED("EC-50000", 501, "该功能尚未实现"),
    SYSTEM_ERROR("EC-50001", 500, "系统繁忙，请稍后重试"),
    STORAGE_UNAVAILABLE("EC-50002", 500, "文件服务暂不可用，请稍后重试"),
    PDF_RENDER_TIMEOUT("EC-50003", 500, "档案生成失败，请联系管理员");

    private final String code;
    private final int httpStatus;
    private final String message;

    ResultCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
