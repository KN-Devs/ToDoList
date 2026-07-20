import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
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
  members: [],
  pendingInvitations: [],
};

describe('ProjectList', () => {
  let projectService: {
    getAll: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let component: ProjectList;

  beforeEach(() => {
    projectService = {
      getAll: vi.fn().mockReturnValue(of([PROJECT])),
      create: vi.fn(),
    };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ProjectList],
      providers: [
        { provide: ProjectService, useValue: projectService },
        { provide: Router, useValue: router },
      ],
    });

    component = TestBed.createComponent(ProjectList).componentInstance;
  });

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

  it('openProject() navigates to the project detail page', () => {
    component.openProject(PROJECT);

    expect(router.navigate).toHaveBeenCalledWith(['/projects', PROJECT.id]);
  });

  describe('rendered template', () => {
    function setValue(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
      el.value = value;
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function clickTab(root: HTMLElement, label: string) {
      Array.from(root.querySelectorAll<HTMLButtonElement>('[role="tab"]'))
        .find((b) => b.textContent?.trim() === label)!
        .click();
    }

    it('renders one card per project with its name and description', () => {
      const fixture = TestBed.createComponent(ProjectList);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      const cards = root.querySelectorAll('.project-card');
      expect(cards).toHaveLength(1);
      expect(cards[0].querySelector('h3')?.textContent).toBe(PROJECT.nom);
      expect(cards[0].textContent).toContain(PROJECT.description);
    });

    it('switching to the create tab shows the form, and submitting it calls create()', async () => {
      projectService.create.mockReturnValue(
        of({ ...PROJECT, id: 2, nom: 'Nouveau projet' })
      );
      const fixture = TestBed.createComponent(ProjectList);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      clickTab(root, 'Créer un projet');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const form = root.querySelector<HTMLFormElement>('form.project-form');
      expect(form).not.toBeNull();

      setValue(root.querySelector('input[name="nom"]')!, 'Nouveau projet');
      setValue(root.querySelector('textarea[name="description"]')!, 'Une description');
      setValue(root.querySelector('input[name="startDate"]')!, '2026-01-01');
      setValue(root.querySelector('input[name="endDate"]')!, '2026-12-31');
      fixture.detectChanges();
      await fixture.whenStable();

      form!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

      expect(projectService.create).toHaveBeenCalledWith({
        nom: 'Nouveau projet',
        description: 'Une description',
        startDate: '2026-01-01',
        endDate: '2026-12-31',
      });
    });

    it('clicking a card navigates to the project detail page', () => {
      const fixture = TestBed.createComponent(ProjectList);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      root.querySelector<HTMLElement>('.project-card')!.click();

      expect(router.navigate).toHaveBeenCalledWith(['/projects', PROJECT.id]);
    });
  });
});
