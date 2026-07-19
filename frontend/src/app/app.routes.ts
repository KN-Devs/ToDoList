import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
    canActivate: [guestGuard],
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
    canActivate: [guestGuard],
  },
  {
    path: 'tasks',
    loadComponent: () =>
      import('./features/tasks/task-list/task-list').then((m) => m.TaskList),
    canActivate: [authGuard],
  },
  {
    path: 'projects',
    loadComponent: () =>
      import('./features/projects/project-list/project-list').then((m) => m.ProjectList),
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'tasks', pathMatch: 'full' },
  { path: '**', redirectTo: 'tasks' },
];
