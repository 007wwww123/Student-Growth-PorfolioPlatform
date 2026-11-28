package com.college.growth.dict.service;

import com.college.growth.dict.dto.DictItemSaveDTO;
import com.college.growth.dict.vo.DictVO;
import java.util.List;

/** 数据字典（F-15 / AC-008）。 */
public interface DictService {

    List<DictVO> list();

    DictVO get(String code);

    DictVO addItem(String code, DictItemSaveDTO dto);

    DictVO updateItem(String code, Long itemId, DictItemSaveDTO dto);

    DictVO setStatus(String code, Long itemId, Integer status);
}
