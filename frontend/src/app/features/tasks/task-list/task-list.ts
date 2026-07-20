import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, HostListener, Input, OnChanges, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { AttachmentService } from '../../../core/services/attachment.service';
import { CommentService } from '../../../core/services/comment.service';
import { TaskService } from '../../../core/services/task.service';
import { Attachment } from '../../../core/models/attachment.model';
import { Comment } from '../../../core/models/comment.model';
import {
  TASK_STATUSES,
  TASK_STATUS_LABELS,
  Task,
  TaskRequest,
  TaskStatus,
} from '../../../core/models/task.model';

export type TasksTab = 'board' | 'create';
export type StatusFilter = TaskStatus | 'ALL';
export type DueDateUrgency = 'overdue' | 'soon';

@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [FormsModule, DragDropModule, DatePipe],
  templateUrl: './task-list.html',
  styleUrl: './task-list.scss',
})
export class TaskList implements OnChanges {
  @Input({ required: true }) projectId!: number;
  @Input() canManage = true;

  readonly tasks = signal<Task[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly statuses = TASK_STATUSES;
  readonly statusLabels = TASK_STATUS_LABELS;
  readonly dropListIds = TASK_STATUSES.map((status) => `drop-list-${status}`);

  readonly activeTab = signal<TasksTab>('board');

  readonly searchQuery = signal('');
  readonly statusFilter = signal<StatusFilter>('ALL');

  readonly visibleStatuses = computed(() => {
    const filter = this.statusFilter();
    return filter === 'ALL' ? this.statuses : [filter];
  });

  readonly tasksByStatus = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const grouped: Record<TaskStatus, Task[]> = { TODO: [], IN_PROGRESS: [], DONE: [] };

    for (const task of this.tasks()) {
      const matchesQuery =
        !query ||
        task.nom.toLowerCase().includes(query) ||
        task.description.toLowerCase().includes(query);

      if (matchesQuery) {
        grouped[task.status].push(task);
      }
    }

    return grouped;
  });

  readonly hasVisibleTasks = computed(() =>
    this.visibleStatuses().some((status) => this.tasksByStatus()[status].length > 0)
  );

  newTask: TaskRequest = { nom: '', description: '', status: 'TODO', dueDate: null };
  readonly creating = signal(false);

  readonly editingId = signal<number | null>(null);
  editForm: TaskRequest = { nom: '', description: '', status: 'TODO', dueDate: null };

  readonly selectedTask = signal<Task | null>(null);

  readonly comments = signal<Comment[]>([]);
  readonly loadingComments = signal(false);
  readonly commentError = signal<string | null>(null);
  newCommentContent = '';
  readonly postingComment = signal(false);

  readonly attachments = signal<Attachment[]>([]);
  readonly loadingAttachments = signal(false);
  readonly attachmentError = signal<string | null>(null);
  readonly uploadingAttachment = signal(false);

  constructor(
    private readonly taskService: TaskService,
    private readonly commentService: CommentService,
    private readonly attachmentService: AttachmentService,
    protected readonly authService: AuthService
  ) {}

  ngOnChanges(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.taskService.getAllForProject(this.projectId).subscribe({
      next: (tasks) => {
        this.tasks.set(tasks);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les tâches');
        this.loading.set(false);
      },
    });
  }

  createTask(): void {
    if (!this.newTask.nom || !this.newTask.description) {
      return;
    }

    this.creating.set(true);

    this.taskService.create(this.projectId, this.newTask).subscribe({
      next: (task) => {
        this.tasks.update((tasks) => [...tasks, task]);
        this.newTask = { nom: '', description: '', status: 'TODO', dueDate: null };
        this.creating.set(false);
        this.activeTab.set('board');
      },
      error: () => {
        this.errorMessage.set('Impossible de créer la tâche');
        this.creating.set(false);
      },
    });
  }

  startEdit(task: Task): void {
    this.editingId.set(task.id);
    this.editForm = {
      nom: task.nom,
      description: task.description,
      status: task.status,
      dueDate: task.dueDate ?? null,
    };
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(task: Task): void {
    this.taskService.update(task.id, this.editForm).subscribe({
      next: (updated) => {
        this.tasks.update((tasks) => tasks.map((t) => (t.id === updated.id ? updated : t)));
        this.editingId.set(null);
      },
      error: () => this.errorMessage.set('Impossible de mettre à jour la tâche'),
    });
  }

  deleteTask(task: Task): void {
    if (!confirm(`Supprimer la tâche "${task.nom}" ?`)) {
      return;
    }

    this.taskService.delete(task.id).subscribe({
      next: () => this.tasks.update((tasks) => tasks.filter((t) => t.id !== task.id)),
      error: () => this.errorMessage.set('Impossible de supprimer la tâche'),
    });
  }

  changeStatus(task: Task, newStatus: TaskStatus): void {
    if (task.status === newStatus) {
      return;
    }

    this.taskService
      .update(task.id, {
        nom: task.nom,
        description: task.description,
        status: newStatus,
        dueDate: task.dueDate ?? null,
      })
      .subscribe({
        next: (updated) => this.tasks.update((tasks) => tasks.map((t) => (t.id === updated.id ? updated : t))),
        error: () => this.errorMessage.set('Impossible de déplacer la tâche'),
      });
  }

  onDrop(event: CdkDragDrop<Task[]>, newStatus: TaskStatus): void {
    if (event.previousContainer === event.container) {
      return;
    }

    this.changeStatus(event.item.data as Task, newStatus);
  }

  openDetail(task: Task): void {
    if (this.editingId() === task.id) {
      return;
    }

    this.selectedTask.set(task);
    this.loadComments(task.id);
    this.loadAttachments(task.id);
  }

  closeDetail(): void {
    this.selectedTask.set(null);
    this.comments.set([]);
    this.attachments.set([]);
    this.newCommentContent = '';
    this.commentError.set(null);
    this.attachmentError.set(null);
  }

  loadComments(taskId: number): void {
    this.loadingComments.set(true);
    this.commentService.getForTask(taskId).subscribe({
      next: (comments) => {
        this.comments.set(comments);
        this.loadingComments.set(false);
      },
      error: () => {
        this.commentError.set('Impossible de charger les commentaires');
        this.loadingComments.set(false);
      },
    });
  }

  postComment(taskId: number): void {
    if (!this.newCommentContent.trim()) {
      return;
    }

    this.postingComment.set(true);
    this.commentError.set(null);

    this.commentService.create(taskId, this.newCommentContent).subscribe({
      next: (comment) => {
        this.comments.update((comments) => [...comments, comment]);
        this.newCommentContent = '';
        this.postingComment.set(false);
      },
      error: () => {
        this.commentError.set("Impossible d'ajouter le commentaire");
        this.postingComment.set(false);
      },
    });
  }

  deleteComment(taskId: number, commentId: number): void {
    this.commentError.set(null);

    this.commentService.delete(taskId, commentId).subscribe({
      next: () => this.comments.update((comments) => comments.filter((c) => c.id !== commentId)),
      error: () => this.commentError.set('Impossible de supprimer ce commentaire'),
    });
  }

  isOwnComment(comment: Comment): boolean {
    return comment.authorEmail === this.authService.currentUser()?.email;
  }

  loadAttachments(taskId: number): void {
    this.loadingAttachments.set(true);
    this.attachmentService.getForTask(taskId).subscribe({
      next: (attachments) => {
        this.attachments.set(attachments);
        this.loadingAttachments.set(false);
      },
      error: () => {
        this.attachmentError.set('Impossible de charger les pièces jointes');
        this.loadingAttachments.set(false);
      },
    });
  }

  onFileSelected(event: Event, taskId: number): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.uploadingAttachment.set(true);
    this.attachmentError.set(null);

    this.attachmentService.upload(taskId, file).subscribe({
      next: (attachment) => {
        this.attachments.update((attachments) => [...attachments, attachment]);
        this.uploadingAttachment.set(false);
        input.value = '';
      },
      error: () => {
        this.attachmentError.set("Impossible d'ajouter ce fichier (5 Mo maximum)");
        this.uploadingAttachment.set(false);
        input.value = '';
      },
    });
  }

  downloadAttachment(taskId: number, attachment: Attachment): void {
    this.attachmentError.set(null);

    this.attachmentService.download(taskId, attachment.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = attachment.filename;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.attachmentError.set('Impossible de télécharger ce fichier'),
    });
  }

  deleteAttachment(taskId: number, attachmentId: number): void {
    this.attachmentError.set(null);

    this.attachmentService.delete(taskId, attachmentId).subscribe({
      next: () => this.attachments.update((attachments) => attachments.filter((a) => a.id !== attachmentId)),
      error: () => this.attachmentError.set('Impossible de supprimer ce fichier'),
    });
  }

  dueDateUrgency(dueDate: string | null | undefined): DueDateUrgency | null {
    if (!dueDate) {
      return null;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const due = new Date(dueDate);
    const diffDays = Math.round((due.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

    if (diffDays < 0) {
      return 'overdue';
    }
    if (diffDays <= 2) {
      return 'soon';
    }
    return null;
  }

  editFromDetail(task: Task): void {
    this.closeDetail();
    this.startEdit(task);
  }

  deleteFromDetail(task: Task): void {
    this.closeDetail();
    this.deleteTask(task);
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closeDetail();
  }

  statusLabel(status: TaskStatus): string {
    return this.statusLabels[status];
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} o`;
    }
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} Ko`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }
}
