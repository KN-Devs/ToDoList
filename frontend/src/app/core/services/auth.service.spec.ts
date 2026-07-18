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

  it('login() stores the token and loads the current user', () => {
    let result: User | undefined;
    service.login({ email: USER.email, password: 'secret' }).subscribe((user) => (result = user));

    const loginReq = httpMock.expectOne(`${API_BASE_URL}/auth/login`);
    expect(loginReq.request.method).toBe('POST');
    loginReq.flush({ token: 'jwt-token' });

    const meReq = httpMock.expectOne(`${API_BASE_URL}/auth/me`);
    meReq.flush(USER);

    expect(result).toEqual(USER);
    expect(service.token()).toBe('jwt-token');
    expect(localStorage.getItem('todolist.token')).toBe('jwt-token');
    expect(service.currentUser()).toEqual(USER);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('register() stores the token and loads the current user', () => {
    service
      .register({ nom: USER.nom, prenom: USER.prenom, email: USER.email, password: 'secret' })
      .subscribe();

    httpMock.expectOne(`${API_BASE_URL}/auth/register`).flush({ token: 'jwt-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.currentUser()).toEqual(USER);
  });

  it('isAdmin reflects the role of the loaded user', () => {
    service.login({ email: ADMIN.email, password: 'secret' }).subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/login`).flush({ token: 'jwt-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(ADMIN);

    expect(service.isAdmin()).toBe(true);
  });

  it('isAdmin is false for a regular user', () => {
    service.login({ email: USER.email, password: 'secret' }).subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/login`).flush({ token: 'jwt-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    expect(service.isAdmin()).toBe(false);
  });

  it('logout() clears the token and the current user', () => {
    service.login({ email: USER.email, password: 'secret' }).subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/login`).flush({ token: 'jwt-token' });
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem('todolist.token')).toBeNull();
  });

  it('loadCurrentUser() sets the current user signal', () => {
    service.loadCurrentUser().subscribe();
    httpMock.expectOne(`${API_BASE_URL}/auth/me`).flush(USER);

    expect(service.currentUser()).toEqual(USER);
  });
});
