package com.college.growth.archive.service;

import com.college.growth.archive.vo.UnfreezeVO;
import java.util.List;

/** 归档解冻（B-6 / F-18 配套）。 */
public interface UnfreezeService {

    UnfreezeVO apply(Long studentId, String academicYear, String reason);

    List<UnfreezeVO> list();

    UnfreezeVO approve(Long id, String remark);

    UnfreezeVO reject(Long id, String remark);
}
