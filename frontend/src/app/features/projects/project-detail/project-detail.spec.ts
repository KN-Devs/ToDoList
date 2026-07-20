import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { Project } from '../../../core/models/project.model';
import { ProjectService } from '../../../core/services/project.service';
import { TaskService } from '../../../core/services/task.service';
import { ProjectDetail } from './project-detail';

const PROJECT: Project = {
  id: 1,
  nom: 'Refonte du site',
  description: 'Migration vers Angular',
  startDate: '2026-01-01',
  endDate: '2026-06-30',
  ownerEmail: 'marie@example.com',
  members: [{ email: 'carol@example.com', canManageTasks: false }],
  pendingInvitations: [],
};

describe('ProjectDetail', () => {
  let projectService: {
    getById: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    inviteMember: ReturnType<typeof vi.fn>;
    cancelInvitation: ReturnType<typeof vi.fn>;
    removeMember: ReturnType<typeof vi.fn>;
    updateMemberPermission: ReturnType<typeof vi.fn>;
  };
  let taskService: { getAllForProject: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let component: ProjectDetail;

  function configure(userEmail: string, isAdmin = false) {
    TestBed.resetTestingModule();

    projectService = {
      getById: vi.fn().mockReturnValue(of(PROJECT)),
      update: vi.fn(),
      delete: vi.fn(),
      inviteMember: vi.fn(),
      cancelInvitation: vi.fn(),
      removeMember: vi.fn(),
      updateMemberPermission: vi.fn(),
    };
    taskService = { getAllForProject: vi.fn().mockReturnValue(of([])) };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ProjectDetail],
      providers: [
        { provide: ProjectService, useValue: projectService },
        { provide: TaskService, useValue: taskService },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
        {
          provide: AuthService,
          useValue: {
            currentUser: () => ({ email: userEmail }),
            isAdmin: () => isAdmin,
            isAuthenticated: () => false,
          },
        },
      ],
    });

    return TestBed.createComponent(ProjectDetail).componentInstance;
  }

  beforeEach(() => {
    component = configure('marie@example.com');
  });

  it('loads the project on init', () => {
    component.ngOnInit();

    expect(projectService.getById).toHaveBeenCalledWith(1);
    expect(component.project()).toEqual(PROJECT);
    expect(component.loading()).toBe(false);
  });

  it('shows an error when reload fails', () => {
    projectService.getById.mockReturnValue(throwError(() => new Error('network error')));

    component.reload();

    expect(component.errorMessage()).toBe('Impossible de charger le projet');
    expect(component.loading()).toBe(false);
  });

  it('isOwner() is true for the project owner', () => {
    component.ngOnInit();

    expect(component.isOwner()).toBe(true);
  });

  it('canManageTasks() is true for the owner', () => {
    component.ngOnInit();

    expect(component.canManageTasks()).toBe(true);
  });

  it('canManageTasks() is false for a member without the permission flag', () => {
    component = configure('carol@example.com');
    component.ngOnInit();

    expect(component.isOwner()).toBe(false);
    expect(component.canManageTasks()).toBe(false);
  });

  it('canManageTasks() is true for a member with the permission flag granted', () => {
    component = configure('carol@example.com');
    projectService.getById.mockReturnValue(
      of({ ...PROJECT, members: [{ email: 'carol@example.com', canManageTasks: true }] })
    );
    component.ngOnInit();

    expect(component.canManageTasks()).toBe(true);
  });

  it('canManageTasks() is true for an ADMIN who is not a member', () => {
    component = configure('admin@example.com', true);
    component.ngOnInit();

    expect(component.canManageTasks()).toBe(true);
  });

  it('startEdit() prefills the form and saveEdit() updates the project', () => {
    component.ngOnInit();
    component.startEdit(PROJECT);

    expect(component.editing()).toBe(true);
    expect(component.editForm.nom).toBe(PROJECT.nom);

    const updated: Project = { ...PROJECT, nom: 'Modifié' };
    projectService.update.mockReturnValue(of(updated));

    component.saveEdit(PROJECT);

    expect(projectService.update).toHaveBeenCalledWith(PROJECT.id, component.editForm);
    expect(component.project()).toEqual(updated);
    expect(component.editing()).toBe(false);
  });

  it('cancelEdit() exits edit mode', () => {
    component.startEdit(PROJECT);

    component.cancelEdit();

    expect(component.editing()).toBe(false);
  });

  it('deleteProject() does not call the service when declined', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

    component.deleteProject(PROJECT);

    expect(projectService.delete).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('deleteProject() deletes the project and navigates back to the list', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    projectService.delete.mockReturnValue(of(undefined));

    component.deleteProject(PROJECT);

    expect(projectService.delete).toHaveBeenCalledWith(PROJECT.id);
    expect(router.navigate).toHaveBeenCalledWith(['/projects']);
    confirmSpy.mockRestore();
  });

  describe('inviteMember', () => {
    it('does nothing when the email is empty', () => {
      component.newMemberEmail = '';

      component.inviteMember(PROJECT);

      expect(projectService.inviteMember).not.toHaveBeenCalled();
    });

    it('sends the invitation and updates the project', () => {
      component.newMemberEmail = 'dan@example.com';
      const updated: Project = {
        ...PROJECT,
        pendingInvitations: ['dan@example.com'],
      };
      projectService.inviteMember.mockReturnValue(of(updated));

      component.inviteMember(PROJECT);

      expect(projectService.inviteMember).toHaveBeenCalledWith(PROJECT.id, 'dan@example.com');
      expect(component.project()).toEqual(updated);
      expect(component.newMemberEmail).toBe('');
    });

    it('shows a not-found message on a 404 error', () => {
      component.newMemberEmail = 'ghost@example.com';
      projectService.inviteMember.mockReturnValue(throwError(() => ({ status: 404 })));

      component.inviteMember(PROJECT);

      expect(component.memberError()).toBe('Aucun utilisateur avec cet email');
    });
  });

  it('cancelInvitation() updates the project', () => {
    const withPending: Project = { ...PROJECT, pendingInvitations: ['dan@example.com'] };
    const updated: Project = { ...PROJECT, pendingInvitations: [] };
    projectService.cancelInvitation.mockReturnValue(of(updated));

    component.cancelInvitation(withPending, 'dan@example.com');

    expect(projectService.cancelInvitation).toHaveBeenCalledWith(PROJECT.id, 'dan@example.com');
    expect(component.project()).toEqual(updated);
  });

  it('removeMember() updates the project', () => {
    const updated: Project = { ...PROJECT, members: [] };
    projectService.removeMember.mockReturnValue(of(updated));

    component.removeMember(PROJECT, 'carol@example.com');

    expect(projectService.removeMember).toHaveBeenCalledWith(PROJECT.id, 'carol@example.com');
    expect(component.project()).toEqual(updated);
  });

  it('toggleMemberPermission() grants the right and updates the project', () => {
    const updated: Project = {
      ...PROJECT,
      members: [{ email: 'carol@example.com', canManageTasks: true }],
    };
    projectService.updateMemberPermission.mockReturnValue(of(updated));

    component.toggleMemberPermission(PROJECT, 'carol@example.com', true);

    expect(projectService.updateMemberPermission).toHaveBeenCalledWith(PROJECT.id, 'carol@example.com', true);
    expect(component.project()).toEqual(updated);
  });

  describe('rendered template', () => {
    it('renders the project name, owner and members', () => {
      const fixture = TestBed.createComponent(ProjectDetail);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      expect(root.querySelector('h1')?.textContent).toBe(PROJECT.nom);
      expect(root.textContent).toContain(PROJECT.ownerEmail);
      expect(root.querySelector('.member-list li')?.textContent).toContain('carol@example.com');
    });

    it('toggling the permission checkbox calls toggleMemberPermission with the flipped value', () => {
      const updated: Project = {
        ...PROJECT,
        members: [{ email: 'carol@example.com', canManageTasks: true }],
      };
      projectService.updateMemberPermission.mockReturnValue(of(updated));
      const fixture = TestBed.createComponent(ProjectDetail);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      const checkbox = root.querySelector<HTMLInputElement>('.permission-toggle input');
      checkbox!.checked = true;
      checkbox!.dispatchEvent(new Event('change', { bubbles: true }));

      expect(projectService.updateMemberPermission).toHaveBeenCalledWith(1, 'carol@example.com', true);
    });
  });
});
