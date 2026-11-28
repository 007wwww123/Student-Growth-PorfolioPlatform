package com.college.growth.dict.vo;

import java.util.List;
import lombok.Data;

/** 字典及字典项视图。 */
@Data
public class DictVO {

    private Long id;

    private String code;

    private String name;

    private String fieldCode;

    private List<Item> items;

    @Data
    public static class Item {

        private Long id;

        private String value;

        private String label;

        private Integer sortNo;

        private Integer status;

        private boolean disabled;
    }
}
