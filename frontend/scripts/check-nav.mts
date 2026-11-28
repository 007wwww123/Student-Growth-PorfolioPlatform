// 菜单口径回归：navGroupsOf 是纯函数，直接断言，不需要浏览器或后端
// 运行：npm run check:nav（tsx 已在 devDependencies，与 smoke:mock 共用）
import assert from 'node:assert/strict'
import { navGroupsOf } from '../src/navMenu'

const flat = (role: Parameters<typeof navGroupsOf>[0], grant = false) =>
  navGroupsOf(role, grant).flatMap((group) => group.items)

const labels = (role: Parameters<typeof navGroupsOf>[0], grant = false) =>
  flat(role, grant).map((item) => item.label)

// 学生：只有六大板块，没有总览/审核/权限配置
const stu = labels('STU')
assert.deepEqual(stu, ['基本信息', '学业发展', '兴趣特长', '生活心理', '职业规划', '导师寄语', '变更记录'], '学生菜单应为六大板块 + 变更记录')
assert.ok(!stu.includes('总览'), '学生不应有总览')
assert.ok(!stu.includes('字段权限配置'), '学生不应有权限配置')

// 辅导员：总览 + 六板块 + 审核 + 建档/归档，没有权限配置
const cou = labels('COU')
assert.ok(cou.includes('总览') && cou.includes('审核工作台'), '辅导员应有总览与审核')
assert.ok(cou.includes('批量建档') && cou.includes('毕业归档') && cou.includes('学生导入'), '辅导员应有档案管理与导入入口')
assert.ok(!cou.includes('字段权限配置'), '辅导员不应有权限配置')
assert.equal(cou.length, 13, `辅导员菜单应为 13 项，实际 ${cou.length}`)

// 持授权管理员：总览 + 六板块 + 审核 + 四项系统管理，没有建档/归档（写操作会被拒）
const adminGranted = labels('ADMIN', true)
assert.ok(adminGranted.includes('总览') && adminGranted.includes('审核工作台'), '持授权管理员应有总览与审核')
assert.ok(adminGranted.includes('字段权限配置'), '持授权管理员应有权限配置')
assert.ok(adminGranted.includes('组织架构') && adminGranted.includes('账号管理') && adminGranted.includes('字段定义') && adminGranted.includes('学生导入'), '持授权管理员应有字段定义、导入与组织账号管理')
assert.ok(!adminGranted.includes('批量建档') && !adminGranted.includes('毕业归档'), '管理员不应有建档/归档')
assert.equal(adminGranted.length, 19, `持授权管理员菜单应为 19 项，实际 ${adminGranted.length}`)

// 无授权管理员：只剩四项系统管理（其余入口必然 403 或空视图，不能出现死链）
const adminNoGrant = labels('ADMIN', false)
assert.deepEqual(adminNoGrant, ['字段权限配置', '字段定义', '学生导入', '组织架构', '账号管理', '临时授权', '审计日志', '变更记录', '班级导出', '数据字典', '解冻审批'], '无授权管理员应只剩系统管理')
assert.equal(navGroupsOf('ADMIN', false).length, 1, '无授权管理员应只有一组')
assert.equal(navGroupsOf('ADMIN', false)[0].title, '系统管理', '分组标题应为系统管理')

// 所有菜单项路径唯一且以 / 开头，避免两个入口指向同一页导致高亮冲突
for (const [role, grant] of [['STU', false], ['COU', false], ['ADMIN', true], ['ADMIN', false]] as const) {
  const paths = flat(role, grant).map((item) => item.to)
  assert.equal(new Set(paths).size, paths.length, `${role}(grant=${grant}) 菜单路径重复：${paths.join(',')}`)
  for (const path of paths) {
    assert.ok(path.startsWith('/'), `${role} 菜单路径应以 / 开头：${path}`)
  }
}

console.log('PASS  学生菜单：六大板块 + 变更记录')
console.log('PASS  辅导员菜单：总览 + 六板块 + 审核 + 档案管理，共 13 项')
console.log('PASS  持授权管理员：总览 + 六板块 + 审核 + 十一项系统管理，共 19 项')
console.log('PASS  无授权管理员：仅十一项系统管理，无死链')
console.log('PASS  菜单路径唯一且为绝对路径')
console.log('\n合计 5 项，失败 0 项')
