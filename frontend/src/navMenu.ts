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
  | 'settings'

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

/**
 * 毕业归档（S-1-11）：仅辅导员可见。
 *
 * <p>归档同样是业务写操作，管理员持的是一次性只读授权，服务端会拒绝，
 * 因此不出现在管理员菜单里。</p>
 */
const ARCHIVE_ITEM: NavItem = {
  key: 'archive',
  label: '毕业归档',
  icon: 'review',
  to: '/archive',
  matchNames: ['archive'],
}

/**
 * 字段权限配置：仅管理员可见。
 *
 * <p>这是管理员模式的系统能力，不属于档案内容编辑，因此与六个板块菜单分开，
 * 放在「系统管理」分组；学生与辅导员没有该项，路由守卫也会拦截。
 * 标签写全「字段权限配置」，与页面标题一致，避免被误读成账号/菜单权限。</p>
 */
const PERMISSION_ITEM: NavItem = {
  key: 'permissions',
  label: '字段权限配置',
  icon: 'settings',
  to: '/admin/permissions',
  matchNames: ['admin-permissions'],
}

/** 学生导入：辅导员导入本班、管理员导入全部班级。 */
const IMPORT_ITEM: NavItem = {
  key: 'import',
  label: '学生导入',
  icon: 'profile',
  to: '/admin/import',
  matchNames: ['admin-import'],
}

/** 临时授权：管理员申请/审批只读权限（F-17）。 */
const TEMP_GRANT_ITEM: NavItem = {
  key: 'temp-grant',
  label: '临时授权',
  icon: 'settings',
  to: '/admin/temp-grants',
  matchNames: ['admin-temp-grants'],
}

/** 变更记录与申诉：学生本人、辅导员本班、管理员全部（F-18）。 */
const CHANGE_ITEM: NavItem = {
  key: 'changes',
  label: '变更记录',
  icon: 'review',
  to: '/changes',
  matchNames: ['changes'],
}

/** 班级汇总 Excel：COU 导出本班，管理员审批敏感列（F-13）。 */
const CLASS_EXPORT_ITEM: NavItem = {
  key: 'class-export',
  label: '班级导出',
  icon: 'profile',
  to: '/class-export',
  matchNames: ['class-export'],
}

/** 审计日志：管理员专属，审计与登录日志查询。 */
const AUDIT_ITEM: NavItem = {
  key: 'audit',
  label: '审计日志',
  icon: 'settings',
  to: '/admin/audit',
  matchNames: ['admin-audit'],
}

/** 字段定义：管理员专属，维护字段类型/必填/校验器/选项/权限位。 */
const FIELD_DEF_ITEM: NavItem = {
  key: 'field-defs',
  label: '字段定义',
  icon: 'settings',
  to: '/admin/fields',
  matchNames: ['admin-fields'],
}

/** 组织架构：管理员专属，维护学院/专业/班级与辅导员绑定（不接触档案内容）。 */
const ORG_ITEM: NavItem = {
  key: 'org',
  label: '组织架构',
  icon: 'settings',
  to: '/admin/org',
  matchNames: ['admin-org'],
}

/** 账号管理：管理员专属，账号开通/停用/重置/角色分配（不接触档案内容）。 */
const ACCOUNT_ITEM: NavItem = {
  key: 'accounts',
  label: '账号管理',
  icon: 'profile',
  to: '/admin/accounts',
  matchNames: ['admin-accounts'],
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
 * <p><b>口径（已与需求方确认，勿按"bug"改回）</b>：教师/管理员为 <b>总览 + 六大模块 + 审核工作台</b>
 * ——需求文案写的是「六大模块及总览」共 7 项，但删掉审核入口后辅导员没有任何可点路径进入
 * /review（页面仍在，只是摸不到），因此保留审核项。若将来产品坚持 7 项，应同时给出另一个入口
 * （例如挂到总览页），不要只删这一行。</p>
 *
 * <p>管理员在此之上多一项「权限配置」（字段级编辑权限），属于系统管理能力；
 * 该页不含任何档案内容，管理员仍不能借它修改学生数据。</p>
 *
 * <p><b>管理员按「临时授权」而非「角色」收敛菜单</b>：管理员默认无业务数据权限
 * （需求 2.1），无授权时看板与待审列表返回 EC-40302、板块详情只回空视图，
 * 这些入口点进去必然被拒。因此只有持授权（{@code tempGrant}）时才出现
 * 总览、六大板块与审核工作台；无授权时只留唯一可用的「字段权限配置」，
 * 避免菜单里 9 项有 8 项是死路。</p>
 */
export function navGroupsOf(role: UserRole | undefined, tempGrant = false): NavGroup[] {
  if (role === 'ADMIN' && !tempGrant) {
    // 无业务数据权限：总览/板块/审核都会 403 或空视图，菜单里不出现；
    // 系统管理（字段权限、组织架构、账号管理）不接触档案内容，保留。
    return [{ key: 'system', title: '系统管理', items: [PERMISSION_ITEM, FIELD_DEF_ITEM, IMPORT_ITEM, ORG_ITEM, ACCOUNT_ITEM, TEMP_GRANT_ITEM, AUDIT_ITEM, CHANGE_ITEM, CLASS_EXPORT_ITEM] }]
  }
  if (isStaff(role)) {
    const groups: NavGroup[] = [
      { key: 'overview', items: [OVERVIEW_ITEM] },
      { key: 'sections', title: '档案模块', items: SECTION_ITEMS },
      { key: 'review', title: '审核', items: [REVIEW_ITEM, CHANGE_ITEM] },
    ]
    // 档案管理只有辅导员有写权限；管理员保持只读，不出现建档/归档入口
    if (role === 'COU') {
      groups.push({ key: 'manage', title: '档案管理', items: [ENROLL_ITEM, ARCHIVE_ITEM, IMPORT_ITEM, CLASS_EXPORT_ITEM] })
    }
    // 字段权限、组织架构、账号管理是管理员专属；辅导员不出现，路由守卫也会拦截
    if (role === 'ADMIN') {
      groups.push({ key: 'system', title: '系统管理', items: [PERMISSION_ITEM, FIELD_DEF_ITEM, IMPORT_ITEM, ORG_ITEM, ACCOUNT_ITEM, TEMP_GRANT_ITEM, AUDIT_ITEM, CLASS_EXPORT_ITEM] })
    }
    return groups
  }
  return [{ key: 'sections', title: '档案板块', items: [...SECTION_ITEMS, CHANGE_ITEM] }]
}
