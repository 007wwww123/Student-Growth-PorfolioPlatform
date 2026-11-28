import { request } from './http'
import type { PageResult } from './types'

/** 站内通知。 */

export interface Notification {
  id: number
  type: string
  title: string
  content?: string
  bizType?: string
  bizId?: number
  read: boolean
  readAt?: string
  createdAt: string
}

export function fetchNotifications(unreadOnly = false, pageSize = 20): Promise<PageResult<Notification>> {
  return request<PageResult<Notification>>({
    url: '/notifications',
    method: 'GET',
    params: { unreadOnly, pageNum: 1, pageSize },
  })
}

export function fetchUnreadCount(): Promise<number> {
  return request<number>({ url: '/notifications/unread-count', method: 'GET' })
}

export function markNotificationRead(id: number): Promise<void> {
  return request<void>({ url: `/notifications/${id}/read`, method: 'POST' })
}

export function markAllNotificationsRead(): Promise<void> {
  return request<void>({ url: '/notifications/read-all', method: 'POST' })
}
