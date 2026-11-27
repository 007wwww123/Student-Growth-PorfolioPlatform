package com.college.growth.common.profile.service;

import java.util.Map;
import java.util.List;
import com.college.growth.basic.vo.FieldMetaVO;

public record SectionFormView(String status, Map<String, Object> fields, boolean editable, List<FieldMetaVO> fieldMeta) {}
