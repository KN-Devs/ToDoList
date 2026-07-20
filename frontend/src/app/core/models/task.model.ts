export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'À faire',
  IN_PROGRESS: 'En cours',
  DONE: 'Terminée',
};

export interface Task {
  id: number;
  nom: string;
  description: string;
  status: TaskStatus;
  email: string;
  dueDate?: string | null;
}

export interface TaskRequest {
  nom: string;
  description: string;
  status: TaskStatus;
  dueDate?: string | null;
}
