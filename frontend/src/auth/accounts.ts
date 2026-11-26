/**
 * 开发环境演示账号（与 db/sql/09_insert_user_seed.sql 一致）。
 *
 * <p>只用于本地联调与验收：登录页可以一键填入，方便评审时切换视角。
 * 生产环境不加载这份清单（种子脚本本身也要求由管理员初始化并改密）。</p>
 */
export interface DemoAccount {
  username: string
  password: string
  label: string
}

const DEMO_PASSWORD = 'Passw0rd!'

export const DEMO_ACCOUNTS: DemoAccount[] = [
  { username: '2024010101', password: DEMO_PASSWORD, label: '学生 · 李明（软件 2401）' },
  { username: '2024010301', password: DEMO_PASSWORD, label: '学生 · 赵敏（电子 2401）' },
  { username: 'cou9001', password: DEMO_PASSWORD, label: '辅导员 · 软件 2401/2402' },
  { username: 'cou9002', password: DEMO_PASSWORD, label: '辅导员 · 电子 2401' },
  { username: 'admin', password: DEMO_PASSWORD, label: '管理员 · 临时只读授权' },
]
