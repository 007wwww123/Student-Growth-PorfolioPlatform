package com.college.growth.completion.vo;

import lombok.Data;

/**
 * 单个板块的完成情况：完成标记卡片里的一格。
 *
 * <p>{@code sectionCode} 用小写（与接口路径、前端 {@code SectionCode} 一致），
 * 数据库侧的大写枚举名不透出到前端。</p>
 */
@Data
public class CompletionSectionVO {

    /** 板块编码，小写，如 basic。 */
    private String sectionCode;

    /** 板块中文名，如 基本信息。 */
    private String sectionName;

    /** 状态枚举名，未建档时为空。 */
    private String status;

    /** 状态中文名，未建档时固定为「未建档」。 */
    private String statusLabel;

    /** 本学期是否已算完成（APPROVED / LOCKED）。 */
    private boolean completed;
}
