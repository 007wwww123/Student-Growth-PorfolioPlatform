package com.college.growth.academic.vo;

import com.college.growth.basic.vo.FieldMetaVO;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 板块详情视图：学业发展（ACADEMIC）。
 */
@Data
public class AcademicSectionVO {

    /** 学生 ID。 */
    private Long studentId;

    /** 学年。 */
    private String academicYear;

    /** 板块编码，固定为 ACADEMIC。 */
    private String sectionCode;

    /** 板块名称。 */
    private String sectionName;

    /** 板块状态，取值见 ProfileStatusEnum。 */
    private String status;

    /** 字段值集合，key 为字段短名（如 {@code goalShort}）。 */
    private Map<String, Object> fields;

    private List<FieldMetaVO> fieldMeta;

    /** 是否可编辑：由来源标记、角色、状态三层同时判定（BR-F01）。 */
    private Boolean editable;
}
