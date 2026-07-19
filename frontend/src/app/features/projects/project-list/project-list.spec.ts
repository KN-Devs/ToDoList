import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { Project } from '../../../core/models/project.model';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectList } from './project-list';

const PROJECT: Project = {
  id: 1,
  nom: 'Refonte du site',
  description: 'Migration vers Angular',
  startDate: '2026-01-01',
  endDate: '2026-06-30',
  ownerEmail: 'marie@example.com',
  memberEmails: [],
};

describe('ProjectList', () => {
  let projectService: {
    getAll: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    addMember: ReturnType<typeof vi.fn>;
    removeMember: ReturnType<typeof vi.fn>;
  };
  let component: ProjectList;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    projectService = {
      getAll: vi.fn().mockReturnValue(of([PROJECT])),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      addMember: vi.fn(),
      removeMember: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [ProjectList],
      providers: [
        { provide: ProjectService, useValue: projectService },
        { provide: AuthService, useValue: { currentUser: () => ({ email: 'marie@example.com' }) } },
      ],
    });

    component = TestBed.createComponent(ProjectList).componentInstance;
    confirmSpy = vi.spyOn(window, 'confirm');
  });

  afterEach(() => confirmSpy.mockRestore());

  it('loads the project list on init', () => {
    component.ngOnInit();

    expect(projectService.getAll).toHaveBeenCalled();
    expect(component.projects()).toEqual([PROJECT]);
    expect(component.loading()).toBe(false);
  });

  it('shows an error message when reload fails', () => {
    projectService.getAll.mockReturnValue(throwError(() => new Error('network error')));

    component.reload();

    expect(component.errorMessage()).toBe('Impossible de charger les projets');
  });

  it('createProject() does nothing when a required field is missing', () => {
    component.newProject = { nom: '', description: '', startDate: '', endDate: '' };

    component.createProject();

    expect(projectService.create).not.toHaveBeenCalled();
  });

  it('createProject() appends the created project, resets the form and switches to the list tab', () => {
    const created: Project = { ...PROJECT, id: 2, nom: 'Nouveau projet' };
    projectService.create.mockReturnValue(of(created));
    component.newProject = {
      nom: 'Nouveau projet',
      description: 'desc',
      startDate: '2026-01-01',
      endDate: '2026-12-31',
    };
    component.activeTab.set('create');

    component.createProject();

    expect(component.projects()).toEqual([created]);
    expect(component.activeTab()).toBe('list');
  });

  it('isOwner() compares the project owner email to the current user', () => {
    expect(component.isOwner(PROJECT)).toBe(true);
    expect(component.isOwner({ ...PROJECT, ownerEmail: 'other@example.com' })).toBe(false);
  });

  it('openDetail() selects the project and resets the member form', () => {
    component.newMemberEmail = 'stale@example.com';

    component.openDetail(PROJECT);

    expect(component.selectedProject()).toEqual(PROJECT);
    expect(component.editing()).toBe(false);
    expect(component.newMemberEmail).toBe('');
  });

  it('closeDetail() clears the selection', () => {
    component.openDetail(PROJECT);

    component.closeDetail();

    expect(component.selectedProject()).toBeNull();
  });

  it('startEdit() prefills the form and saveEdit() updates the project', () => {
    component.ngOnInit();
    component.openDetail(PROJECT);
    component.startEdit(PROJECT);

    expect(component.editing()).toBe(true);
    expect(component.editForm.nom).toBe(PROJECT.nom);

    const updated: Project = { ...PROJECT, nom: 'Modifié' };
    projectService.update.mockReturnValue(of(updated));

    component.saveEdit(PROJECT);

    expect(projectService.update).toHaveBeenCalledWith(PROJECT.id, component.editForm);
    expect(component.projects()).toEqual([updated]);
    expect(component.selectedProject()).toEqual(updated);
    expect(component.editing()).toBe(false);
  });

  it('deleteProject() does not call the service when declined', () => {
    confirmSpy.mockReturnValue(false);

    component.deleteProject(PROJECT);

    expect(projectService.delete).not.toHaveBeenCalled();
  });

  it('deleteProject() removes the project and closes the modal when confirmed', () => {
    component.ngOnInit();
    component.openDetail(PROJECT);
    confirmSpy.mockReturnValue(true);
    projectService.delete.mockReturnValue(of(undefined));

    component.deleteProject(PROJECT);

    expect(projectService.delete).toHaveBeenCalledWith(PROJECT.id);
    expect(component.projects()).toEqual([]);
    expect(component.selectedProject()).toBeNull();
  });

  describe('addMember', () => {
    it('does nothing when the email is empty', () => {
      component.newMemberEmail = '';

      component.addMember(PROJECT);

      expect(projectService.addMember).not.toHaveBeenCalled();
    });

    it('adds the member and updates the selected project', () => {
      component.ngOnInit();
      component.openDetail(PROJECT);
      component.newMemberEmail = 'carol@example.com';
      const updated: Project = { ...PROJECT, memberEmails: ['carol@example.com'] };
      projectService.addMember.mockReturnValue(of(updated));

      component.addMember(PROJECT);

      expect(projectService.addMember).toHaveBeenCalledWith(PROJECT.id, 'carol@example.com');
      expect(component.selectedProject()).toEqual(updated);
      expect(component.newMemberEmail).toBe('');
    });

    it('shows a not-found message on a 404 error', () => {
      component.newMemberEmail = 'ghost@example.com';
      projectService.addMember.mockReturnValue(throwError(() => ({ status: 404 })));

      component.addMember(PROJECT);

      expect(component.memberError()).toBe('Aucun utilisateur avec cet email');
    });

    it('shows an already-member message on a 400 error', () => {
      component.newMemberEmail = 'carol@example.com';
      projectService.addMember.mockReturnValue(throwError(() => ({ status: 400 })));

      component.addMember(PROJECT);

      expect(component.memberError()).toBe('Cette personne est déjà membre du projet');
    });
  });

  it('removeMember() updates the selected project', () => {
    component.ngOnInit();
    component.openDetail(PROJECT);
    const updated: Project = { ...PROJECT, memberEmails: [] };
    projectService.removeMember.mockReturnValue(of(updated));

    component.removeMember(PROJECT, 'carol@example.com');

    expect(projectService.removeMember).toHaveBeenCalledWith(PROJECT.id, 'carol@example.com');
    expect(component.selectedProject()).toEqual(updated);
  });

  it('onEscapeKey() closes the modal', () => {
    component.openDetail(PROJECT);

    component.onEscapeKey();

    expect(component.selectedProject()).toBeNull();
  });
});
