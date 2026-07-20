export interface Comment {
  id: number;
  content: string;
  authorEmail: string;
  createdAt: string;
}

export type CommentEventAction = 'CREATED' | 'DELETED';

export interface CommentEvent {
  action: CommentEventAction;
  taskId: number;
  comment: Comment;
}
