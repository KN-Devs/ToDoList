import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { TokenRefreshCoordinator } from './token-refresh-coordinator';

const AUTH_ENDPOINTS_WITHOUT_RETRY = ['/auth/login', '/auth/register', '/auth/refresh'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const coordinator = inject(TokenRefreshCoordinator);
  const token = authService.token();

  const authorizedReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      const isRetryableRequest = !AUTH_ENDPOINTS_WITHOUT_RETRY.some((path) => req.url.includes(path));

      if (error.status === 401 && isRetryableRequest && authService.hasRefreshToken()) {
        return handleExpiredToken(req, next, authService, router, coordinator);
      }

      if (error.status === 401 && isRetryableRequest) {
        authService.logout();
        router.navigate(['/login']);
      }

      return throwError(() => error);
    })
  );
};

function handleExpiredToken(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService,
  router: Router,
  coordinator: TokenRefreshCoordinator
): Observable<HttpEvent<unknown>> {
  if (!coordinator.isRefreshing) {
    coordinator.isRefreshing = true;
    coordinator.refreshedToken$.next(null);

    return authService.refresh().pipe(
      switchMap((newToken) => {
        coordinator.isRefreshing = false;
        coordinator.refreshedToken$.next(newToken);
        return next(req.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } }));
      }),
      catchError((error) => {
        coordinator.isRefreshing = false;
        authService.logout();
        router.navigate(['/login']);
        return throwError(() => error);
      })
    );
  }

  // Un rafraîchissement est déjà en cours (déclenché par une autre requête
  // concurrente) : on attend son résultat plutôt que d'en démarrer un second.
  return coordinator.refreshedToken$.pipe(
    filter((value): value is string => value !== null),
    take(1),
    switchMap((newToken) => next(req.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } })))
  );
}
