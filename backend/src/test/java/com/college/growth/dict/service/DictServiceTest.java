package com.college.growth.dict.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.mapper.ArcFieldDefMapper;
import com.college.growth.dict.entity.SysDict;
import com.college.growth.dict.entity.SysDictItem;
import com.college.growth.dict.mapper.SysDictItemMapper;
import com.college.growth.dict.mapper.SysDictMapper;
import com.college.growth.dict.service.impl.DictServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 字典停用：同步字段选项并保留历史。 */
@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDict.class);
        TableInfoHelper.initTableInfo(assistant, SysDictItem.class);
        TableInfoHelper.initTableInfo(assistant, ArcFieldDef.class);
    }

    @Mock private SysDictMapper dictMapper;
    @Mock private SysDictItemMapper itemMapper;
    @Mock private ArcFieldDefMapper fieldDefMapper;

    private DictServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DictServiceImpl(dictMapper, itemMapper, fieldDefMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("disable item syncs disabled flag into field options")
    void disableSyncsOptions() throws Exception {
        SysDict dict = dict();
        SysDictItem item = item();
        ArcFieldDef def = new ArcFieldDef();
        def.setId(1L);
        def.setFieldCode("interest.hobbies");
        when(dictMapper.selectOne(any())).thenReturn(dict);
        when(itemMapper.selectById(11L)).thenReturn(item);
        when(fieldDefMapper.selectOne(any())).thenReturn(def);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        service.setStatus("hobby", 11L, 0);

        ArgumentCaptor<ArcFieldDef> captor = ArgumentCaptor.forClass(ArcFieldDef.class);
        verify(fieldDefMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getOptionsJson().contains("\"disabled\":true"));
        assertTrue(captor.getValue().getOptionsJson().contains("摄影"));
    }

    @Test
    @DisplayName("disabled item remains readable")
    void readIncludesDisabled() {
        when(dictMapper.selectOne(any())).thenReturn(dict());
        when(itemMapper.selectList(any())).thenReturn(List.of(item()));

        var view = service.get("hobby");

        assertEquals(1, view.getItems().size());
        assertEquals(true, view.getItems().get(0).isDisabled());
        assertEquals("摄影", view.getItems().get(0).getLabel());
    }

    private SysDict dict() {
        SysDict dict = new SysDict();
        dict.setId(5L);
        dict.setDictCode("hobby");
        dict.setDictName("兴趣爱好");
        dict.setFieldCode("interest.hobbies");
        dict.setStatus(1);
        return dict;
    }

    private SysDictItem item() {
        SysDictItem item = new SysDictItem();
        item.setId(11L);
        item.setDictId(5L);
        item.setItemValue("摄影");
        item.setItemLabel("摄影");
        item.setSortNo(10);
        item.setStatus(0);
        return item;
    }
}
