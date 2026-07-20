import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let logout: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;
  let refresh: ReturnType<typeof vi.fn>;
  let hasRefreshToken: ReturnType<typeof vi.fn>;

  function setup(token: string | null, refreshTokenAvailable: boolean) {
    logout = vi.fn();
    navigate = vi.fn();
    refresh = vi.fn();
    hasRefreshToken = vi.fn().mockReturnValue(refreshTokenAvailable);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: () => token, logout, refresh, hasRefreshToken } },
        { provide: Router, useValue: { navigate } },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('attaches the Bearer token when one is present', () => {
    setup('jwt-token', true);

    http.get('/api/tasks').subscribe();

    const req = httpMock.expectOne('/api/tasks');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush([]);
  });

  it('does not attach an Authorization header when there is no token', () => {
    setup(null, false);

    http.get('/api/tasks').subscribe();

    const req = httpMock.expectOne('/api/tasks');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('on a 401 with a refresh token available, refreshes and retries the request', () => {
    setup('expired-token', true);
    refresh.mockReturnValue(of('new-token'));

    let result: unknown;
    http.get('/api/tasks').subscribe((value) => (result = value));

    const firstReq = httpMock.expectOne('/api/tasks');
    firstReq.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    const retriedReq = httpMock.expectOne('/api/tasks');
    expect(retriedReq.request.headers.get('Authorization')).toBe('Bearer new-token');
    retriedReq.flush(['task']);

    expect(result).toEqual(['task']);
    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('logs out and redirects to /login when the refresh call itself fails', () => {
    setup('expired-token', true);
    refresh.mockReturnValue(throwError(() => ({ status: 401 })));

    http.get('/api/tasks').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/tasks');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logs out and redirects to /login on a 401 with no refresh token available', () => {
    setup('expired-token', false);

    http.get('/api/tasks').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/tasks');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(refresh).not.toHaveBeenCalled();
    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not attempt to refresh on a 401 from the login endpoint itself', () => {
    setup(null, false);

    http.post('/api/auth/login', {}).subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/auth/login');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(refresh).not.toHaveBeenCalled();
    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not log out on other error statuses', () => {
    setup('jwt-token', true);

    http.get('/api/tasks').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/tasks');
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('queues a second concurrent 401 behind the first refresh instead of refreshing twice', () => {
    setup('expired-token', true);
    // Le refresh doit rester "en vol" pendant que les deux 401 concurrents
    // arrivent, comme un vrai appel réseau : un Subject non résolu simule ça,
    // contrairement à of(...) qui se résoudrait de façon synchrone.
    const refreshResult$ = new Subject<string>();
    refresh.mockReturnValue(refreshResult$);

    let resultA: unknown;
    let resultB: unknown;
    http.get('/api/tasks').subscribe((value) => (resultA = value));
    http.get('/api/projects').subscribe((value) => (resultB = value));

    const reqA = httpMock.expectOne('/api/tasks');
    const reqB = httpMock.expectOne('/api/projects');
    reqA.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    reqB.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(refresh).toHaveBeenCalledTimes(1);

    refreshResult$.next('new-token');
    refreshResult$.complete();

    const retriedA = httpMock.expectOne('/api/tasks');
    const retriedB = httpMock.expectOne('/api/projects');
    expect(retriedA.request.headers.get('Authorization')).toBe('Bearer new-token');
    expect(retriedB.request.headers.get('Authorization')).toBe('Bearer new-token');
    retriedA.flush(['task']);
    retriedB.flush(['project']);

    expect(resultA).toEqual(['task']);
    expect(resultB).toEqual(['project']);
  });
});
