import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, switchMap, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../models/auth.model';

const TOKEN_KEY = 'todolist.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly currentUserSignal = signal<User | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);
  readonly isAdmin = computed(() => this.currentUserSignal()?.role === 'ADMIN');

  constructor(private readonly http: HttpClient) {}

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/register`, request).pipe(
      tap((response) => this.setToken(response.token)),
      switchMap(() => this.loadCurrentUser())
    );
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => this.setToken(response.token)),
      switchMap(() => this.loadCurrentUser())
    );
  }

  loadCurrentUser(): Observable<User> {
    return this.http
      .get<User>(`${API_BASE_URL}/auth/me`)
      .pipe(tap((user) => this.currentUserSignal.set(user)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokenSignal.set(null);
    this.currentUserSignal.set(null);
  }

  private setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.tokenSignal.set(token);
  }
}
