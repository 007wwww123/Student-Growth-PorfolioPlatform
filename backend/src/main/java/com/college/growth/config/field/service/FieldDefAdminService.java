package com.college.growth.config.field.service;

import com.college.growth.config.field.dto.FieldDefSaveDTO;
import com.college.growth.config.field.vo.FieldConfigMetaVO;
import com.college.growth.config.field.vo.FieldDefVO;
import java.util.List;

/** 字段定义配置（F-04 / AC-007）：增删改、必填开关、启停与元数据。 */
public interface FieldDefAdminService {

    List<FieldDefVO> list(String sectionCode);

    FieldDefVO create(FieldDefSaveDTO dto);

    FieldDefVO update(Long id, FieldDefSaveDTO dto);

    void updateStatus(Long id, int status);

    void updateRequired(Long id, int required);

    FieldConfigMetaVO meta();
}
