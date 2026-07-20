export type NotificationType = 'PROJECT_INVITATION' | 'TASK_COMMENT' | 'TASK_DUE_SOON' | 'TASK_OVERDUE';

export interface Notification {
  id: number | null;
  type: NotificationType;
  message: string;
  projectId: number | null;
  taskId: number | null;
  createdAt: string;
  read: boolean;
}

export interface UnreadCount {
  count: number;
}
