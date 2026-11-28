import { request } from './http'
import type { MentorNoteVO, MentorSectionVO } from './types'

/**
 * 板块六 导师寄语（MENTOR）接口封装。
 *
 * <p>与其余五板块共用 GET / PUT / submit / withdraw 四个端点（见 sections.ts），
 * 这里只封装需求 4.7 特有的子表能力：辅导员追加谈话、学生反馈一次。
 * 详情返回的是 MentorSectionVO（统一信封 + notes 时间线），不是普通表单，
 * 所以单独走一个 fetch 而不是复用 fetchSection。</p>
 */

/** 查询详情：统一信封 + 按 talkDate 倒序的谈话时间线。 */
export function fetchMentorSection(
  studentId: number,
  academicYear?: string,
): Promise<MentorSectionVO> {
  return request<MentorSectionVO>({
    url: `/sections/mentor/${studentId}`,
    method: 'GET',
    params: academicYear ? { academicYear } : undefined,
  })
}

/** 追加一条谈话记录（仅本班辅导员；只追加，不修改不删除）。 */
export function appendMentorNote(
  studentId: number,
  payload: {
    academicYear: string
    talkDate: string
    topic: string
    advice: string
    starLevel?: number
    internalRemark?: string
  },
): Promise<MentorNoteVO> {
  return request<MentorNoteVO>({
    url: `/sections/mentor/${studentId}/notes`,
    method: 'POST',
    data: payload,
  })
}

/** 对一条谈话提交学生反馈（仅本人，每条仅一次，提交后锁定）。 */
export function submitMentorFeedback(
  studentId: number,
  noteId: number,
  payload: { academicYear: string; feedback: string },
): Promise<MentorNoteVO> {
  return request<MentorNoteVO>({
    url: `/sections/mentor/${studentId}/notes/${noteId}/feedback`,
    method: 'POST',
    data: payload,
  })
}
