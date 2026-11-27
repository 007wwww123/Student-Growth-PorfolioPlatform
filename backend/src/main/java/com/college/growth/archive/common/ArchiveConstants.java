package com.college.growth.archive.common;

/**
 * 批量归档（S-1-11）模块常量。
 *
 * <p>归档是跨板块能力：一个学生六块全部通过后整体转 {@code LOCKED}，
 * 因此与审核、建档一样单独一组路径，不挂在某个板块下。</p>
 */
public final class ArchiveConstants {

    /** 接口基础路径。 */
    public static final String API_BASE_PATH = "/archive";

    /** 单次最多归档的学生数；毕业名单通常按班提交，200 足够一个班。 */
    public static final int MAX_STUDENT_COUNT = 200;

    /** 学生归档结果：本次已转为已归档。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 学生归档结果：本来就已归档，本次跳过。 */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** 学生归档结果：板块未全部通过，构成未完成清单。 */
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";

    /** 审计目标类型：学生。归档是学生级动作，不按板块逐条留痕。 */
    public static final String TARGET_STUDENT = "STUDENT";

    /** 审计动作：批量归档。 */
    public static final String ACTION_ARCHIVE = "ARCHIVE";

    /** 归档原因，写进变更日志，说明这次 LOCKED 不是人工逐个点的。 */
    public static final String ARCHIVE_REASON = "毕业归档：六板块全部通过，批量置为已归档";

    private ArchiveConstants() {
    }
}
