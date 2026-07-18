import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let logout: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  function setup(token: string | null) {
    logout = vi.fn();
    navigate = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: () => token, logout } },
        { provide: Router, useValue: { navigate } },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('attaches the Bearer token when one is present', () => {
    setup('jwt-token');

    http.get('/api/tasks').subscribe();

    const req = httpMock.expectOne('/api/tasks');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush([]);
  });

  it('does not attach an Authorization header when there is no token', () => {
    setup(null);

    http.get('/api/tasks').subscribe();

    const req = httpMock.expectOne('/api/tasks');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('logs out and redirects to /login on a 401 response', () => {
    setup('expired-token');

    http.get('/api/tasks').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/tasks');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not log out on other error statuses', () => {
    setup('jwt-token');

    http.get('/api/tasks').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/tasks');
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
});
