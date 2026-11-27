package com.college.growth.archive.vo;

import lombok.Data;

/**
 * 板块状态行，用在归档候选人的「未完成板块」清单里。
 *
 * <p>只带编码、名称与状态，不展开字段值：归档判断只看状态，不需要把档案内容搬到列表页。</p>
 */
@Data
public class ArchiveSectionStatusVO {

    /** 板块编码，大写，如 BASIC。 */
    private String sectionCode;

    /** 板块中文名。 */
    private String sectionName;

    /** 当前状态；未建档时为 null。 */
    private String status;

    /** 状态中文名；未建档时为「未建档」。 */
    private String statusLabel;
}
