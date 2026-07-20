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
    path: 'confirm-email',
    loadComponent: () =>
      import('./features/auth/confirm-email/confirm-email').then((m) => m.ConfirmEmail),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
    canActivate: [guestGuard],
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
    canActivate: [guestGuard],
  },
  {
    path: 'invitations/accept',
    loadComponent: () =>
      import('./features/invitations/accept-invitation/accept-invitation').then(
        (m) => m.AcceptInvitation
      ),
  },
  {
    path: 'projects',
    loadComponent: () =>
      import('./features/projects/project-list/project-list').then((m) => m.ProjectList),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:id',
    loadComponent: () =>
      import('./features/projects/project-detail/project-detail').then((m) => m.ProjectDetail),
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'projects', pathMatch: 'full' },
  { path: '**', redirectTo: 'projects' },
];
