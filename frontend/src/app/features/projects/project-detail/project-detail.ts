import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ProjectService } from '../../../core/services/project.service';
import { Project, ProjectRequest } from '../../../core/models/project.model';
import { TaskList } from '../../tasks/task-list/task-list';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, TaskList],
  templateUrl: './project-detail.html',
  styleUrl: './project-detail.scss',
})
export class ProjectDetail implements OnInit {
  readonly project = signal<Project | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly editing = signal(false);
  editForm: ProjectRequest = { nom: '', description: '', startDate: '', endDate: '' };

  newMemberEmail = '';
  readonly addingMember = signal(false);
  readonly memberError = signal<string | null>(null);

  readonly isOwner = computed(() => this.project()?.ownerEmail === this.authService.currentUser()?.email);

  readonly canManageTasks = computed(() => {
    const project = this.project();
    if (!project) {
      return false;
    }
    if (this.isOwner() || this.authService.isAdmin()) {
      return true;
    }
    const email = this.authService.currentUser()?.email;
    return project.members.some((m) => m.email === email && m.canManageTasks);
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly projectService: ProjectService,
    protected readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading.set(true);
    this.errorMessage.set(null);

    this.projectService.getById(id).subscribe({
      next: (project) => {
        this.project.set(project);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger le projet');
        this.loading.set(false);
      },
    });
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
    this.errorMessage.set(null);
    this.projectService.update(project.id, this.editForm).subscribe({
      next: (updated) => {
        this.project.set(updated);
        this.editing.set(false);
      },
      error: () => this.errorMessage.set('Impossible de mettre à jour le projet'),
    });
  }

  deleteProject(project: Project): void {
    if (!confirm(`Supprimer le projet "${project.nom}" ?`)) {
      return;
    }

    this.errorMessage.set(null);
    this.projectService.delete(project.id).subscribe({
      next: () => this.router.navigate(['/projects']),
      error: () => this.errorMessage.set('Impossible de supprimer le projet'),
    });
  }

  addMember(project: Project): void {
    if (!this.newMemberEmail) {
      return;
    }

    this.addingMember.set(true);
    this.memberError.set(null);
    this.errorMessage.set(null);

    this.projectService.addMember(project.id, this.newMemberEmail).subscribe({
      next: (updated) => {
        this.project.set(updated);
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
    this.errorMessage.set(null);
    this.projectService.removeMember(project.id, email).subscribe({
      next: (updated) => this.project.set(updated),
      error: () => this.errorMessage.set('Impossible de retirer cette personne'),
    });
  }

  toggleMemberPermission(project: Project, email: string, canManageTasks: boolean): void {
    this.errorMessage.set(null);
    this.projectService.updateMemberPermission(project.id, email, canManageTasks).subscribe({
      next: (updated) => this.project.set(updated),
      error: () => this.errorMessage.set('Impossible de modifier les droits de cette personne'),
    });
  }
}
