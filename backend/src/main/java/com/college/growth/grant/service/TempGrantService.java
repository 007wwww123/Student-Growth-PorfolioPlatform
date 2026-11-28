package com.college.growth.grant.service;

import com.college.growth.grant.dto.TempGrantApplyDTO;
import com.college.growth.grant.vo.TempGrantVO;
import java.util.List;

/** 临时授权（F-17 / AC-053）。 */
public interface TempGrantService {

    TempGrantVO apply(TempGrantApplyDTO dto);

    List<TempGrantVO> list();

    List<TempGrantVO> active();

    TempGrantVO approve(Long id, String remark);

    TempGrantVO reject(Long id, String remark);

    TempGrantVO revoke(Long id);
}
