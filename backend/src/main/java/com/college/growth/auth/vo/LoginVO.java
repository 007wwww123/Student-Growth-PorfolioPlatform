package com.college.growth.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 登录结果：令牌 + 过期时间 + 用户信息，前端一次拿全，避免登录后再发一次 /me。 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT，前端放在 Authorization: Bearer 里。 */
    private String token;

    /** 固定 Bearer，便于前端拼接头。 */
    private String tokenType;

    /** 有效期秒数。 */
    private long expiresIn;

    private UserVO user;

    /**
     * 是否必须先改密（BR-A04 / AC-004）。
     *
     * <p>登录本身成功、令牌也照发：不改密就只能访问改密接口，
     * 否则「先登录才能改密」这条链路走不通。</p>
     */
    private boolean mustChangePassword;
}
