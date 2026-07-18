import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { Task } from '../../../core/models/task.model';
import { TaskService } from '../../../core/services/task.service';
import { TaskList } from './task-list';

const TASK: Task = {
  id: 1,
  nom: 'Préparer la démo',
  description: 'Vérifier le rendu visuel',
  status: 'TODO',
  email: 'marie@example.com',
};

describe('TaskList', () => {
  let taskService: {
    getAll: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let component: TaskList;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    taskService = {
      getAll: vi.fn().mockReturnValue(of([TASK])),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [TaskList],
      providers: [
        { provide: TaskService, useValue: taskService },
        { provide: AuthService, useValue: { isAdmin: () => false } },
      ],
    });

    component = TestBed.createComponent(TaskList).componentInstance;
    confirmSpy = vi.spyOn(window, 'confirm');
  });

  afterEach(() => confirmSpy.mockRestore());

  it('loads the task list on init', () => {
    component.ngOnInit();

    expect(taskService.getAll).toHaveBeenCalled();
    expect(component.tasks()).toEqual([TASK]);
    expect(component.loading()).toBe(false);
  });

  it('shows an error message when reload fails', () => {
    taskService.getAll.mockReturnValue(throwError(() => new Error('network error')));

    component.reload();

    expect(component.errorMessage()).toBe('Impossible de charger les tâches');
    expect(component.loading()).toBe(false);
  });

  it('createTask() does nothing when required fields are missing', () => {
    component.newTask = { nom: '', description: '', status: 'TODO' };

    component.createTask();

    expect(taskService.create).not.toHaveBeenCalled();
  });

  it('createTask() appends the created task, resets the form and switches to the board tab', () => {
    const created: Task = { ...TASK, id: 2, nom: 'Nouvelle tâche' };
    taskService.create.mockReturnValue(of(created));
    component.newTask = { nom: 'Nouvelle tâche', description: 'Desc', status: 'TODO' };
    component.activeTab.set('create');

    component.createTask();

    expect(component.tasks()).toEqual([created]);
    expect(component.newTask).toEqual({ nom: '', description: '', status: 'TODO' });
    expect(component.creating()).toBe(false);
    expect(component.activeTab()).toBe('board');
  });

  it('createTask() surfaces an error and stops the loading state', () => {
    taskService.create.mockReturnValue(throwError(() => new Error('failed')));
    component.newTask = { nom: 'Nouvelle tâche', description: 'Desc', status: 'TODO' };

    component.createTask();

    expect(component.errorMessage()).toBe('Impossible de créer la tâche');
    expect(component.creating()).toBe(false);
  });

  it('startEdit() populates the edit form and cancelEdit() clears it', () => {
    component.startEdit(TASK);

    expect(component.editingId()).toBe(TASK.id);
    expect(component.editForm).toEqual({
      nom: TASK.nom,
      description: TASK.description,
      status: TASK.status,
    });

    component.cancelEdit();

    expect(component.editingId()).toBeNull();
  });

  it('saveEdit() updates the task in place and exits edit mode', () => {
    component.ngOnInit();
    const updated: Task = { ...TASK, status: 'DONE' };
    taskService.update.mockReturnValue(of(updated));
    component.startEdit(TASK);
    component.editForm.status = 'DONE';

    component.saveEdit(TASK);

    expect(taskService.update).toHaveBeenCalledWith(TASK.id, component.editForm);
    expect(component.tasks()).toEqual([updated]);
    expect(component.editingId()).toBeNull();
  });

  it('deleteTask() does not call the service when the confirmation is declined', () => {
    confirmSpy.mockReturnValue(false);

    component.deleteTask(TASK);

    expect(taskService.delete).not.toHaveBeenCalled();
  });

  it('deleteTask() removes the task when the confirmation is accepted', () => {
    component.ngOnInit();
    confirmSpy.mockReturnValue(true);
    taskService.delete.mockReturnValue(of(undefined));

    component.deleteTask(TASK);

    expect(taskService.delete).toHaveBeenCalledWith(TASK.id);
    expect(component.tasks()).toEqual([]);
  });

  it('statusLabel() returns the French label for a status', () => {
    expect(component.statusLabel('IN_PROGRESS')).toBe('En cours');
  });

  it('defaults to the board tab', () => {
    expect(component.activeTab()).toBe('board');
  });

  it('tasksByStatus() groups tasks by their status', () => {
    const inProgress: Task = { ...TASK, id: 2, status: 'IN_PROGRESS' };
    taskService.getAll.mockReturnValue(of([TASK, inProgress]));
    component.ngOnInit();

    const grouped = component.tasksByStatus();

    expect(grouped.TODO).toEqual([TASK]);
    expect(grouped.IN_PROGRESS).toEqual([inProgress]);
    expect(grouped.DONE).toEqual([]);
  });

  describe('changeStatus', () => {
    it('does nothing when the status is unchanged', () => {
      component.changeStatus(TASK, TASK.status);

      expect(taskService.update).not.toHaveBeenCalled();
    });

    it('updates the task status via the service and reflects it in state', () => {
      component.ngOnInit();
      const moved: Task = { ...TASK, status: 'DONE' };
      taskService.update.mockReturnValue(of(moved));

      component.changeStatus(TASK, 'DONE');

      expect(taskService.update).toHaveBeenCalledWith(TASK.id, {
        nom: TASK.nom,
        description: TASK.description,
        status: 'DONE',
      });
      expect(component.tasks()).toEqual([moved]);
    });

    it('surfaces an error when the move fails', () => {
      taskService.update.mockReturnValue(throwError(() => new Error('failed')));

      component.changeStatus(TASK, 'DONE');

      expect(component.errorMessage()).toBe('Impossible de déplacer la tâche');
    });
  });

  describe('onDrop', () => {
    it('does nothing when dropped back in the same column', () => {
      const sameContainer = {};

      component.onDrop(
        {
          previousContainer: sameContainer,
          container: sameContainer,
          item: { data: TASK },
        } as never,
        'TODO'
      );

      expect(taskService.update).not.toHaveBeenCalled();
    });

    it('changes the status when dropped in a different column', () => {
      component.ngOnInit();
      const moved: Task = { ...TASK, status: 'IN_PROGRESS' };
      taskService.update.mockReturnValue(of(moved));

      component.onDrop(
        { previousContainer: {}, container: { different: true }, item: { data: TASK } } as never,
        'IN_PROGRESS'
      );

      expect(taskService.update).toHaveBeenCalledWith(TASK.id, {
        nom: TASK.nom,
        description: TASK.description,
        status: 'IN_PROGRESS',
      });
    });
  });
});
