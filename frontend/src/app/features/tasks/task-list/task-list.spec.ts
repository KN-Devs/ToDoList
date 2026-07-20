import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { AttachmentService } from '../../../core/services/attachment.service';
import { CommentService } from '../../../core/services/comment.service';
import { Attachment } from '../../../core/models/attachment.model';
import { Comment } from '../../../core/models/comment.model';
import { Task } from '../../../core/models/task.model';
import { TaskService } from '../../../core/services/task.service';
import { TaskList } from './task-list';

const PROJECT_ID = 7;

const TASK: Task = {
  id: 1,
  nom: 'Préparer la démo',
  description: 'Vérifier le rendu visuel',
  status: 'TODO',
  email: 'marie@example.com',
  dueDate: null,
};

const COMMENT: Comment = {
  id: 1,
  content: 'Un commentaire',
  authorEmail: 'marie@example.com',
  createdAt: '2026-01-01T10:00:00Z',
};

const ATTACHMENT: Attachment = {
  id: 1,
  filename: 'plan.pdf',
  contentType: 'application/pdf',
  fileSize: 2048,
  uploadedByEmail: 'marie@example.com',
  createdAt: '2026-01-01T10:00:00Z',
};

describe('TaskList', () => {
  let taskService: {
    getAllForProject: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let commentService: {
    getForTask: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let attachmentService: {
    getForTask: ReturnType<typeof vi.fn>;
    upload: ReturnType<typeof vi.fn>;
    download: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let component: TaskList;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    taskService = {
      getAllForProject: vi.fn().mockReturnValue(of([TASK])),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    };
    commentService = {
      getForTask: vi.fn().mockReturnValue(of([])),
      create: vi.fn(),
      delete: vi.fn(),
    };
    attachmentService = {
      getForTask: vi.fn().mockReturnValue(of([])),
      upload: vi.fn(),
      download: vi.fn(),
      delete: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [TaskList],
      providers: [
        { provide: TaskService, useValue: taskService },
        { provide: CommentService, useValue: commentService },
        { provide: AttachmentService, useValue: attachmentService },
        { provide: AuthService, useValue: { isAdmin: () => false, currentUser: () => ({ email: 'marie@example.com' }) } },
      ],
    });

    component = TestBed.createComponent(TaskList).componentInstance;
    component.projectId = PROJECT_ID;
    confirmSpy = vi.spyOn(window, 'confirm');
  });

  afterEach(() => confirmSpy.mockRestore());

  it('loads the task list on init', () => {
    component.ngOnChanges();

    expect(taskService.getAllForProject).toHaveBeenCalledWith(PROJECT_ID);
    expect(component.tasks()).toEqual([TASK]);
    expect(component.loading()).toBe(false);
  });

  it('shows an error message when reload fails', () => {
    taskService.getAllForProject.mockReturnValue(throwError(() => new Error('network error')));

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

    expect(taskService.create).toHaveBeenCalledWith(PROJECT_ID, {
      nom: 'Nouvelle tâche',
      description: 'Desc',
      status: 'TODO',
    });
    expect(component.tasks()).toEqual([created]);
    expect(component.newTask).toEqual({ nom: '', description: '', status: 'TODO', dueDate: null });
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
      dueDate: null,
    });

    component.cancelEdit();

    expect(component.editingId()).toBeNull();
  });

  it('saveEdit() updates the task in place and exits edit mode', () => {
    component.ngOnChanges();
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
    component.ngOnChanges();
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

  it('defaults canManage to true', () => {
    expect(component.canManage).toBe(true);
  });

  it('tasksByStatus() groups tasks by their status', () => {
    const inProgress: Task = { ...TASK, id: 2, status: 'IN_PROGRESS' };
    taskService.getAllForProject.mockReturnValue(of([TASK, inProgress]));
    component.ngOnChanges();

    const grouped = component.tasksByStatus();

    expect(grouped.TODO).toEqual([TASK]);
    expect(grouped.IN_PROGRESS).toEqual([inProgress]);
    expect(grouped.DONE).toEqual([]);
  });

  describe('search and status filter', () => {
    const other: Task = {
      id: 2,
      nom: 'Corriger un bug',
      description: 'Le formulaire plante sur Firefox',
      status: 'DONE',
      email: 'marie@example.com',
    };

    beforeEach(() => {
      taskService.getAllForProject.mockReturnValue(of([TASK, other]));
      component.ngOnChanges();
    });

    it('visibleStatuses() returns every status by default', () => {
      expect(component.visibleStatuses()).toEqual(['TODO', 'IN_PROGRESS', 'DONE']);
    });

    it('visibleStatuses() narrows to a single status when filtered', () => {
      component.statusFilter.set('DONE');

      expect(component.visibleStatuses()).toEqual(['DONE']);
    });

    it('tasksByStatus() filters by a search query matching the name or description', () => {
      component.searchQuery.set('firefox');

      const grouped = component.tasksByStatus();

      expect(grouped.TODO).toEqual([]);
      expect(grouped.DONE).toEqual([other]);
    });

    it('hasVisibleTasks() is false when the search and filter combination matches nothing', () => {
      component.statusFilter.set('TODO');
      component.searchQuery.set('firefox');

      expect(component.hasVisibleTasks()).toBe(false);
    });

    it('hasVisibleTasks() is true when at least one visible column has a match', () => {
      component.statusFilter.set('DONE');
      component.searchQuery.set('firefox');

      expect(component.hasVisibleTasks()).toBe(true);
    });
  });

  describe('changeStatus', () => {
    it('does nothing when the status is unchanged', () => {
      component.changeStatus(TASK, TASK.status);

      expect(taskService.update).not.toHaveBeenCalled();
    });

    it('updates the task status via the service and reflects it in state', () => {
      component.ngOnChanges();
      const moved: Task = { ...TASK, status: 'DONE' };
      taskService.update.mockReturnValue(of(moved));

      component.changeStatus(TASK, 'DONE');

      expect(taskService.update).toHaveBeenCalledWith(TASK.id, {
        nom: TASK.nom,
        description: TASK.description,
        status: 'DONE',
        dueDate: null,
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
      component.ngOnChanges();
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
        dueDate: null,
      });
    });
  });

  describe('task detail', () => {
    it('openDetail() selects the task', () => {
      component.openDetail(TASK);

      expect(component.selectedTask()).toEqual(TASK);
    });

    it('openDetail() does nothing while the task is being edited', () => {
      component.startEdit(TASK);

      component.openDetail(TASK);

      expect(component.selectedTask()).toBeNull();
    });

    it('closeDetail() clears the selection', () => {
      component.openDetail(TASK);

      component.closeDetail();

      expect(component.selectedTask()).toBeNull();
    });

    it('editFromDetail() closes the modal and starts editing', () => {
      component.openDetail(TASK);

      component.editFromDetail(TASK);

      expect(component.selectedTask()).toBeNull();
      expect(component.editingId()).toBe(TASK.id);
    });

    it('deleteFromDetail() closes the modal and deletes the task', () => {
      component.ngOnChanges();
      confirmSpy.mockReturnValue(true);
      taskService.delete.mockReturnValue(of(undefined));
      component.openDetail(TASK);

      component.deleteFromDetail(TASK);

      expect(component.selectedTask()).toBeNull();
      expect(taskService.delete).toHaveBeenCalledWith(TASK.id);
    });

    it('onEscapeKey() closes the modal', () => {
      component.openDetail(TASK);

      component.onEscapeKey();

      expect(component.selectedTask()).toBeNull();
    });

    it('openDetail() loads comments and attachments for the task', () => {
      commentService.getForTask.mockReturnValue(of([COMMENT]));
      attachmentService.getForTask.mockReturnValue(of([ATTACHMENT]));

      component.openDetail(TASK);

      expect(commentService.getForTask).toHaveBeenCalledWith(TASK.id);
      expect(attachmentService.getForTask).toHaveBeenCalledWith(TASK.id);
      expect(component.comments()).toEqual([COMMENT]);
      expect(component.attachments()).toEqual([ATTACHMENT]);
    });

    it('closeDetail() clears comments, attachments and the draft comment', () => {
      commentService.getForTask.mockReturnValue(of([COMMENT]));
      attachmentService.getForTask.mockReturnValue(of([ATTACHMENT]));
      component.openDetail(TASK);
      component.newCommentContent = 'brouillon';

      component.closeDetail();

      expect(component.comments()).toEqual([]);
      expect(component.attachments()).toEqual([]);
      expect(component.newCommentContent).toBe('');
    });
  });

  describe('comments', () => {
    it('postComment() does nothing when the content is blank', () => {
      component.newCommentContent = '   ';

      component.postComment(TASK.id);

      expect(commentService.create).not.toHaveBeenCalled();
    });

    it('postComment() appends the created comment and clears the draft', () => {
      commentService.create.mockReturnValue(of(COMMENT));
      component.newCommentContent = 'Un commentaire';

      component.postComment(TASK.id);

      expect(commentService.create).toHaveBeenCalledWith(TASK.id, 'Un commentaire');
      expect(component.comments()).toEqual([COMMENT]);
      expect(component.newCommentContent).toBe('');
      expect(component.postingComment()).toBe(false);
    });

    it('postComment() surfaces an error', () => {
      commentService.create.mockReturnValue(throwError(() => new Error('failed')));
      component.newCommentContent = 'Un commentaire';

      component.postComment(TASK.id);

      expect(component.commentError()).toBe("Impossible d'ajouter le commentaire");
      expect(component.postingComment()).toBe(false);
    });

    it('deleteComment() removes the comment from state', () => {
      component.comments.set([COMMENT]);
      commentService.delete.mockReturnValue(of(undefined));

      component.deleteComment(TASK.id, COMMENT.id);

      expect(commentService.delete).toHaveBeenCalledWith(TASK.id, COMMENT.id);
      expect(component.comments()).toEqual([]);
    });

    it('deleteComment() surfaces an error', () => {
      component.comments.set([COMMENT]);
      commentService.delete.mockReturnValue(throwError(() => new Error('failed')));

      component.deleteComment(TASK.id, COMMENT.id);

      expect(component.commentError()).toBe('Impossible de supprimer ce commentaire');
    });

    it('isOwnComment() is true when the comment author matches the current user', () => {
      expect(component.isOwnComment(COMMENT)).toBe(true);
      expect(component.isOwnComment({ ...COMMENT, authorEmail: 'other@example.com' })).toBe(false);
    });
  });

  describe('attachments', () => {
    it('onFileSelected() uploads the chosen file and appends it to the list', () => {
      attachmentService.upload.mockReturnValue(of(ATTACHMENT));
      const file = new File(['contenu'], 'plan.pdf', { type: 'application/pdf' });
      const input = document.createElement('input');
      Object.defineProperty(input, 'files', { value: [file] });
      const event = { target: input } as unknown as Event;

      component.onFileSelected(event, TASK.id);

      expect(attachmentService.upload).toHaveBeenCalledWith(TASK.id, file);
      expect(component.attachments()).toEqual([ATTACHMENT]);
      expect(component.uploadingAttachment()).toBe(false);
    });

    it('onFileSelected() does nothing when no file is chosen', () => {
      const input = document.createElement('input');
      const event = { target: input } as unknown as Event;

      component.onFileSelected(event, TASK.id);

      expect(attachmentService.upload).not.toHaveBeenCalled();
    });

    it('onFileSelected() surfaces an error', () => {
      attachmentService.upload.mockReturnValue(throwError(() => new Error('failed')));
      const file = new File(['contenu'], 'plan.pdf', { type: 'application/pdf' });
      const input = document.createElement('input');
      Object.defineProperty(input, 'files', { value: [file] });
      const event = { target: input } as unknown as Event;

      component.onFileSelected(event, TASK.id);

      expect(component.attachmentError()).toBe("Impossible d'ajouter ce fichier (5 Mo maximum)");
      expect(component.uploadingAttachment()).toBe(false);
    });

    it('deleteAttachment() removes the attachment from state', () => {
      component.attachments.set([ATTACHMENT]);
      attachmentService.delete.mockReturnValue(of(undefined));

      component.deleteAttachment(TASK.id, ATTACHMENT.id);

      expect(attachmentService.delete).toHaveBeenCalledWith(TASK.id, ATTACHMENT.id);
      expect(component.attachments()).toEqual([]);
    });

    it('deleteAttachment() surfaces an error', () => {
      component.attachments.set([ATTACHMENT]);
      attachmentService.delete.mockReturnValue(throwError(() => new Error('failed')));

      component.deleteAttachment(TASK.id, ATTACHMENT.id);

      expect(component.attachmentError()).toBe('Impossible de supprimer ce fichier');
    });

    it('downloadAttachment() surfaces an error when the download fails', () => {
      attachmentService.download.mockReturnValue(throwError(() => new Error('failed')));

      component.downloadAttachment(TASK.id, ATTACHMENT);

      expect(component.attachmentError()).toBe('Impossible de télécharger ce fichier');
    });
  });

  describe('dueDateUrgency', () => {
    it('returns null when there is no due date', () => {
      expect(component.dueDateUrgency(null)).toBeNull();
      expect(component.dueDateUrgency(undefined)).toBeNull();
    });

    it('returns "overdue" for a date in the past', () => {
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);

      expect(component.dueDateUrgency(yesterday.toISOString().slice(0, 10))).toBe('overdue');
    });

    it('returns "soon" for a date within the next two days', () => {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);

      expect(component.dueDateUrgency(tomorrow.toISOString().slice(0, 10))).toBe('soon');
    });

    it('returns null for a date further in the future', () => {
      const nextMonth = new Date();
      nextMonth.setDate(nextMonth.getDate() + 30);

      expect(component.dueDateUrgency(nextMonth.toISOString().slice(0, 10))).toBeNull();
    });
  });

  describe('formatFileSize', () => {
    it('formats bytes', () => {
      expect(component.formatFileSize(512)).toBe('512 o');
    });

    it('formats kilobytes', () => {
      expect(component.formatFileSize(2048)).toBe('2.0 Ko');
    });

    it('formats megabytes', () => {
      expect(component.formatFileSize(3 * 1024 * 1024)).toBe('3.0 Mo');
    });
  });
});
