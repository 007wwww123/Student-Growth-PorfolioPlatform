package com.college.growth.account.service;

import com.college.growth.account.dto.BatchOpenDTO;
import com.college.growth.account.dto.UserCreateDTO;
import com.college.growth.account.vo.AccountUserVO;
import com.college.growth.account.vo.BatchOpenResultVO;
import com.college.growth.account.vo.CreateAccountResultVO;
import com.college.growth.account.vo.ResetPasswordResultVO;
import com.college.growth.common.result.PageResult;

/** 账号管理（F-03）：列表、开通、停用、重置密码、角色分配、批量开通。 */
public interface AccountService {

    PageResult<AccountUserVO> list(String role, Integer status, String keyword,
                                   long pageNum, long pageSize);

    CreateAccountResultVO create(UserCreateDTO dto);

    ResetPasswordResultVO resetPassword(Long userId, String newPassword);

    void updateStatus(Long userId, int status);

    void assignRole(Long userId, String role);

    BatchOpenResultVO batchOpen(BatchOpenDTO dto);
}
