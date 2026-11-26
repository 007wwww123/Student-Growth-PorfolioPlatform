import type { UserRole } from './types'

/**
 * 演示身份（骨架期占位）。
 *
 * <p>后端目前从请求头 {@code X-User-Id / X-User-Role} 读取用户，本身不是鉴权手段：
 * 任何调用方都能伪造。这里提供一组固定身份，只是为了在接入统一认证前能把
 * 「学生填写 → 辅导员审核」的完整链路点通，不保存任何凭证。</p>
 *
 * <p>接入登录后本文件整体删除，改由 token 中的用户信息决定身份。</p>
 */

export interface DemoIdentity {
  /** 与 stu_student.id / 辅导员工号同口径 */
  userId: number
  role: UserRole
  /** 展示用说明，如「辅导员 · 软件2401/2402」 */
  label: string
  /** 管理员默认无业务数据权限，持临时授权才可查看 */
  tempGrant?: boolean
}

/** 演示身份清单，ID 与数据库种子数据一致。 */
export const DEMO_IDENTITIES: DemoIdentity[] = [
  { userId: 1001, role: 'STU', label: '学生 · 李明（软件2401）' },
  { userId: 1004, role: 'STU', label: '学生 · 赵敏（电子2401）' },
  { userId: 9001, role: 'COU', label: '辅导员 · 软件2401/2402' },
  { userId: 9002, role: 'COU', label: '辅导员 · 电子2401' },
  { userId: 9003, role: 'ADMIN', label: '管理员 · 临时授权', tempGrant: true },
]

const STORAGE_KEY = 'growth.demo.identity'

/** 默认身份：学生 1001，与 SectionShell 的演示学生一致。 */
export const DEFAULT_IDENTITY: DemoIdentity = DEMO_IDENTITIES[0]!

let current: DemoIdentity = load()

function load(): DemoIdentity {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return DEFAULT_IDENTITY
    }
    const parsed = JSON.parse(raw) as Partial<DemoIdentity>
    return (
      DEMO_IDENTITIES.find(
        (item) => item.userId === parsed.userId && item.role === parsed.role,
      ) ?? DEFAULT_IDENTITY
    )
  } catch {
    return DEFAULT_IDENTITY
  }
}

/** 当前演示身份。 */
export function getIdentity(): DemoIdentity {
  return current
}

/** 切换演示身份，刷新页面后保持。 */
export function setIdentity(identity: DemoIdentity): void {
  current = identity
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(identity))
  } catch {
    // 隐私模式下 localStorage 不可写，忽略即可，切换仍在本次会话内生效
  }
}

/** 当前身份是否为指定角色。 */
export function isRole(role: UserRole): boolean {
  return current.role === role
}
