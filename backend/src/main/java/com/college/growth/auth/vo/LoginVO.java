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
}
