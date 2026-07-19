import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { ProjectService } from '../../../core/services/project.service';
import { Project, ProjectRequest } from '../../../core/models/project.model';

export type ProjectsTab = 'list' | 'create';

const EMPTY_PROJECT_FORM: ProjectRequest = {
  nom: '',
  description: '',
  startDate: '',
  endDate: '',
};

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './project-list.html',
  styleUrl: './project-list.scss',
})
export class ProjectList implements OnInit {
  readonly projects = signal<Project[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly activeTab = signal<ProjectsTab>('list');

  newProject: ProjectRequest = { ...EMPTY_PROJECT_FORM };
  readonly creating = signal(false);

  readonly selectedProject = signal<Project | null>(null);
  readonly editing = signal(false);
  editForm: ProjectRequest = { ...EMPTY_PROJECT_FORM };

  newMemberEmail = '';
  readonly addingMember = signal(false);
  readonly memberError = signal<string | null>(null);

  constructor(
    private readonly projectService: ProjectService,
    protected readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.projectService.getAll().subscribe({
      next: (projects) => {
        this.projects.set(projects);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les projets');
        this.loading.set(false);
      },
    });
  }

  createProject(): void {
    if (!this.newProject.nom || !this.newProject.description || !this.newProject.startDate || !this.newProject.endDate) {
      return;
    }

    this.creating.set(true);
    this.errorMessage.set(null);

    this.projectService.create(this.newProject).subscribe({
      next: (project) => {
        this.projects.update((projects) => [...projects, project]);
        this.newProject = { ...EMPTY_PROJECT_FORM };
        this.creating.set(false);
        this.activeTab.set('list');
      },
      error: () => {
        this.errorMessage.set('Impossible de créer le projet');
        this.creating.set(false);
      },
    });
  }

  isOwner(project: Project): boolean {
    return project.ownerEmail === this.authService.currentUser()?.email;
  }

  openDetail(project: Project): void {
    this.selectedProject.set(project);
    this.editing.set(false);
    this.newMemberEmail = '';
    this.memberError.set(null);
  }

  closeDetail(): void {
    this.selectedProject.set(null);
    this.editing.set(false);
  }

  startEdit(project: Project): void {
    this.editForm = {
      nom: project.nom,
      description: project.description,
      startDate: project.startDate,
      endDate: project.endDate,
    };
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  saveEdit(project: Project): void {
    this.projectService.update(project.id, this.editForm).subscribe({
      next: (updated) => {
        this.projects.update((projects) => projects.map((p) => (p.id === updated.id ? updated : p)));
        this.selectedProject.set(updated);
        this.editing.set(false);
      },
      error: () => this.errorMessage.set('Impossible de mettre à jour le projet'),
    });
  }

  deleteProject(project: Project): void {
    if (!confirm(`Supprimer le projet "${project.nom}" ?`)) {
      return;
    }

    this.projectService.delete(project.id).subscribe({
      next: () => {
        this.projects.update((projects) => projects.filter((p) => p.id !== project.id));
        this.closeDetail();
      },
      error: () => this.errorMessage.set('Impossible de supprimer le projet'),
    });
  }

  addMember(project: Project): void {
    if (!this.newMemberEmail) {
      return;
    }

    this.addingMember.set(true);
    this.memberError.set(null);

    this.projectService.addMember(project.id, this.newMemberEmail).subscribe({
      next: (updated) => {
        this.projects.update((projects) => projects.map((p) => (p.id === updated.id ? updated : p)));
        this.selectedProject.set(updated);
        this.newMemberEmail = '';
        this.addingMember.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.addingMember.set(false);
        this.memberError.set(
          error.status === 404
            ? 'Aucun utilisateur avec cet email'
            : error.status === 400
              ? 'Cette personne est déjà membre du projet'
              : "Impossible d'ajouter cette personne"
        );
      },
    });
  }

  removeMember(project: Project, email: string): void {
    this.projectService.removeMember(project.id, email).subscribe({
      next: (updated) => {
        this.projects.update((projects) => projects.map((p) => (p.id === updated.id ? updated : p)));
        this.selectedProject.set(updated);
      },
      error: () => this.errorMessage.set('Impossible de retirer cette personne'),
    });
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closeDetail();
  }
}
