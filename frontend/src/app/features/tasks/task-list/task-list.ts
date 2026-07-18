import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { TaskService } from '../../../core/services/task.service';
import {
  TASK_STATUSES,
  TASK_STATUS_LABELS,
  Task,
  TaskRequest,
  TaskStatus,
} from '../../../core/models/task.model';

export type TasksTab = 'board' | 'create';

@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [FormsModule, DragDropModule],
  templateUrl: './task-list.html',
  styleUrl: './task-list.scss',
})
export class TaskList implements OnInit {
  readonly tasks = signal<Task[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly statuses = TASK_STATUSES;
  readonly statusLabels = TASK_STATUS_LABELS;
  readonly dropListIds = TASK_STATUSES.map((status) => `drop-list-${status}`);

  readonly activeTab = signal<TasksTab>('board');

  readonly tasksByStatus = computed(() => {
    const grouped: Record<TaskStatus, Task[]> = { TODO: [], IN_PROGRESS: [], DONE: [] };
    for (const task of this.tasks()) {
      grouped[task.status].push(task);
    }
    return grouped;
  });

  newTask: TaskRequest = { nom: '', description: '', status: 'TODO' };
  readonly creating = signal(false);

  readonly editingId = signal<number | null>(null);
  editForm: TaskRequest = { nom: '', description: '', status: 'TODO' };

  constructor(
    private readonly taskService: TaskService,
    protected readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.taskService.getAll().subscribe({
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

    this.taskService.create(this.newTask).subscribe({
      next: (task) => {
        this.tasks.update((tasks) => [...tasks, task]);
        this.newTask = { nom: '', description: '', status: 'TODO' };
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
    this.editForm = { nom: task.nom, description: task.description, status: task.status };
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
      .update(task.id, { nom: task.nom, description: task.description, status: newStatus })
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

  statusLabel(status: TaskStatus): string {
    return this.statusLabels[status];
  }
}
