import type { UserRole } from './api/types'
import { SECTIONS } from './sectionMeta'

/**
 * 公共菜单模型。
 *
 * <p>菜单按角色计算，不放在组件模板里写死：新增板块或调整角色可见范围只改这里，
 * 侧边栏只负责渲染。菜单项的 {@code matchNames} 是「哪些路由命中后高亮」，
 * 详情页（如审核详情）与列表页共用一个菜单项，避免详情页菜单全灭。</p>
 */

export type NavIconName =
  | 'overview'
  | 'profile'
  | 'book'
  | 'star'
  | 'heart'
  | 'briefcase'
  | 'chat'
  | 'review'

export interface NavItem {
  /** 稳定标识，仅用于 key */
  key: string
  label: string
  icon: NavIconName
  to: string
  /** 命中即高亮的路由名 */
  matchNames: string[]
}

export interface NavGroup {
  key: string
  /** 分组标题；为空时只渲染间距，不渲染文字 */
  title?: string
  items: NavItem[]
}

/** 板块与图标的对应关系，与 sectionMeta 的 code 一致。 */
const SECTION_ICONS: Record<string, NavIconName> = {
  basic: 'profile',
  academic: 'book',
  interest: 'star',
  life: 'heart',
  career: 'briefcase',
  mentor: 'chat',
}

/** 六大板块，学生与教师/管理员共用同一份，顺序取自 sectionMeta。 */
const SECTION_ITEMS: NavItem[] = SECTIONS.map((section) => ({
  key: `section-${section.code}`,
  label: section.name,
  icon: SECTION_ICONS[section.code] ?? 'profile',
  to: `/sections/${section.code}`,
  matchNames: [`section-${section.code}`],
}))

/** 总览：教师/管理员的数据看板入口，当前先占位，后续接可视化。 */
const OVERVIEW_ITEM: NavItem = {
  key: 'overview',
  label: '总览',
  icon: 'overview',
  to: '/',
  matchNames: ['home'],
}

/** 审核工作台：仅有审核权限的角色可见（服务端仍会二次校验权限）。 */
const REVIEW_ITEM: NavItem = {
  key: 'review',
  label: '审核工作台',
  icon: 'review',
  to: '/review',
  matchNames: ['review-list', 'review-detail'],
}

/**
 * 批量建档（S-1-01）：仅辅导员可见。
 *
 * <p>建档是业务写操作，管理员为「临时只读授权」，服务端会返回 EC-40302，
 * 因此不在管理员菜单里出现，避免点进去才被拒。</p>
 */
const ENROLL_ITEM: NavItem = {
  key: 'enroll',
  label: '批量建档',
  icon: 'profile',
  to: '/enroll',
  matchNames: ['enroll'],
}

/** 教师、管理员共用一类菜单：总览 + 六大模块 + 审核工作台。 */
export function isStaff(role: UserRole | undefined): boolean {
  return role === 'COU' || role === 'ADMIN'
}

/**
 * 按角色返回左侧菜单分组。
 *
 * <p>学生只有六大板块；教师与管理员多「总览」（数据可视化入口）与审核工作台。
 * 顶部栏不区分角色，因此不在这个模型里。</p>
 *
 * <p><b>口径（已与需求方确认，勿按"bug"改回）</b>：教师/管理员为 <b>8 项</b>
 * ——总览 + 六大模块 + 审核工作台。需求文案写的是「六大模块及总览」共 7 项，
 * 但删掉审核入口后辅导员没有任何可点路径进入 /review（页面仍在，只是摸不到），
 * 因此保留为第 8 项。若将来产品坚持 7 项，应同时给出另一个入口
 * （例如挂到总览页），不要只删这一行。</p>
 */
export function navGroupsOf(role: UserRole | undefined): NavGroup[] {
  if (isStaff(role)) {
    const groups: NavGroup[] = [
      { key: 'overview', items: [OVERVIEW_ITEM] },
      { key: 'sections', title: '档案模块', items: SECTION_ITEMS },
      { key: 'review', title: '审核', items: [REVIEW_ITEM] },
    ]
    // 档案管理只有辅导员有写权限；管理员保持 8 项不变（总览 + 六模块 + 审核）
    if (role === 'COU') {
      groups.push({ key: 'manage', title: '档案管理', items: [ENROLL_ITEM] })
    }
    return groups
  }
  return [{ key: 'sections', title: '档案板块', items: SECTION_ITEMS }]
}
