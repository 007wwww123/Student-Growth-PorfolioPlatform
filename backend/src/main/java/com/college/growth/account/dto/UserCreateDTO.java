package com.college.growth.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新建账号入参。 */
@Data
public class UserCreateDTO {

    /** 登录名；学生账号由学号决定，此处会被忽略。 */
    @Size(max = 64, message = "登录名不超过 64 字符")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不超过 64 字符")
    private String displayName;

    /** STU / COU / ADMIN。 */
    @NotBlank(message = "角色不能为空")
    private String role;

    /** 学生账号必填：stu_student.id。 */
    private Long studentId;

    /** 初始密码；为空时学生按身份证后六位、其他角色随机生成。 */
    private String password;
}
