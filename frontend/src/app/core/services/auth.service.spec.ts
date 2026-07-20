import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../config/api.config';
import { User } from '../models/auth.model';
import { AuthService } from './auth.service';

const USER: User = { id: 1, nom: 'Dupont', prenom: 'Marie', email: 'marie@example.com', role: 'USER' };
const ADMIN: User = { id: 2, nom: 'Admin', prenom: 'Super', email: 'admin@example.com', role: 'ADMIN' };

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts unauthenticated when no token is stored', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.hasRefreshToken()).toBe(false);
  });

  it('picks up an existing token from localStorage on construction', () => {
    localStorage.setItem('todolist.token', 'existing-token');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    const freshService = TestBed.inject(AuthService);

    expect(freshService.isAuthenticated()).toBe(true);
    expect(freshService.token()).toBe('existing-token');
  });

  it('login() stores the token and refresh token, and loads the current user', () => {
    let result: User | undefined;
    service.login({ email: USER.email, password: 'secret' }).subscribe((user) => (result = user));

    const loginReq = httpMock.expectOne(`${API_BASE_URL}/auth/login`);
    expect(loginReq.request.method).toBe('POST');
    loginReq.flush({ token: 'jwt-token', refreshToken: 'refresh-token' });

    const meReq = httpMock.expectOne(`${API_BASE_URL}/auth/me`);
    meReq.flush(USER);

    expect(result).toEqual(USER);
    expect(service.token()).toBe('jwt-token');
    expect(localStorage.getItem('todolist.token')).toBe('jwt-token');
    expect(localStorage.getItem('todolist.refreshToken')).toBe('refresh-token');
    expect(service.hasRefreshToken()).toBe(true);
    expect(service.currentUser()).toEqual(USER);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('register() stores the token and refresh token, and loads the current user', () => {
    service
      .register({ nom: USER.nom, prenom: USER.prenom, email: USER.email, password: 'secret' })
      .subscribe();

    httpMock.expectOne(`${API_BASE_URL}/auth/register`).flush({ token: 'jwt-token', refreshToken: 'refresh-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.currentUser()).toEqual(USER);
    expect(localStorage.getItem('todolist.refreshToken')).toBe('refresh-token');
  });

  it('isAdmin reflects the role of the loaded user', () => {
    service.login({ email: ADMIN.email, password: 'secret' }).subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/login`).flush({ token: 'jwt-token', refreshToken: 'refresh-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(ADMIN);

    expect(service.isAdmin()).toBe(true);
  });

  it('isAdmin is false for a regular user', () => {
    service.login({ email: USER.email, password: 'secret' }).subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/login`).flush({ token: 'jwt-token', refreshToken: 'refresh-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    expect(service.isAdmin()).toBe(false);
  });

  it('refresh() exchanges the stored refresh token for a new access token', () => {
    localStorage.setItem('todolist.refreshToken', 'old-refresh-token');

    let newToken: string | undefined;
    service.refresh().subscribe((token) => (newToken = token));

    const req = httpMock.expectOne(`${API_BASE_URL}/auth/refresh`);
    expect(req.request.body).toEqual({ refreshToken: 'old-refresh-token' });
    req.flush({ token: 'new-token', refreshToken: 'new-refresh-token' });

    expect(newToken).toBe('new-token');
    expect(service.token()).toBe('new-token');
    expect(localStorage.getItem('todolist.refreshToken')).toBe('new-refresh-token');
  });

  it('refresh() errors immediately when no refresh token is stored', () => {
    let errored = false;
    service.refresh().subscribe({ error: () => (errored = true) });

    expect(errored).toBe(true);
  });

  it('logout() clears the token and the current user, and revokes the refresh token server-side', () => {
    service.login({ email: USER.email, password: 'secret' }).subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/login`).flush({ token: 'jwt-token', refreshToken: 'refresh-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.hasRefreshToken()).toBe(false);
    expect(localStorage.getItem('todolist.token')).toBeNull();
    expect(localStorage.getItem('todolist.refreshToken')).toBeNull();

    const logoutReq = httpMock.expectOne(`${API_BASE_URL}/auth/logout`);
    expect(logoutReq.request.body).toEqual({ refreshToken: 'refresh-token' });
    logoutReq.flush(null);
  });

  it('logout() does not call the backend when there is no refresh token to revoke', () => {
    service.logout();

    httpMock.expectNone(`${API_BASE_URL}/auth/logout`);
  });

  it('loadCurrentUser() sets the current user signal', () => {
    service.loadCurrentUser().subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    expect(service.currentUser()).toEqual(USER);
  });

  it('confirmEmail() posts the token', () => {
    service.confirmEmail('some-token').subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/auth/confirm-email`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'some-token' });
    req.flush(null);
  });

  it('resendConfirmation() posts the email', () => {
    service.resendConfirmation('marie@example.com').subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/auth/resend-confirmation`);
    expect(req.request.body).toEqual({ email: 'marie@example.com' });
    req.flush(null);
  });

  it('forgotPassword() posts the email', () => {
    service.forgotPassword('marie@example.com').subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/auth/forgot-password`);
    expect(req.request.body).toEqual({ email: 'marie@example.com' });
    req.flush(null);
  });

  it('resetPassword() posts the token and new password', () => {
    service.resetPassword('some-token', 'NewPassword123!').subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/auth/reset-password`);
    expect(req.request.body).toEqual({ token: 'some-token', newPassword: 'NewPassword123!' });
    req.flush(null);
  });
});
