import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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

  constructor(
    private readonly projectService: ProjectService,
    private readonly router: Router
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

  openProject(project: Project): void {
    this.router.navigate(['/projects', project.id]);
  }
}
