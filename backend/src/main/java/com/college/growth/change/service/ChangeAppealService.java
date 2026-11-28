package com.college.growth.change.service;

import com.college.growth.change.vo.AppealVO;
import com.college.growth.change.vo.ChangeLogVO;
import com.college.growth.common.result.PageResult;

/** 变更记录查询与申诉（F-18 / AC-070）。 */
public interface ChangeAppealService {

    PageResult<ChangeLogVO> list(Long studentId, String sectionCode, String bizType, String fieldCode,
                                 String from, String to, long pageNum, long pageSize);

    AppealVO appeal(Long changeId, String reason);

    PageResult<AppealVO> appeals(String status, long pageNum, long pageSize);

    AppealVO approve(Long appealId, String remark);

    AppealVO reject(Long appealId, String remark);
}
