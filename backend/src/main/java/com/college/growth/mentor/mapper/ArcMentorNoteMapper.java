package com.college.growth.mentor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.college.growth.mentor.entity.ArcMentorNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导师谈话记录数据访问。
 *
 * <p>只在本板块的 Service 中使用；条件一律显式带 {@code profile_id} 或
 * {@code student_id + academic_year}，禁止全表扫。</p>
 *
 * <p>没有 update / delete 的业务入口：本表只追加（BR-S07），
 * 唯一的例外是学生反馈列，由 Service 显式写入。</p>
 */
@Mapper
public interface ArcMentorNoteMapper extends BaseMapper<ArcMentorNote> {
}
