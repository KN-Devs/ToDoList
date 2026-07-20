import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AuthResponse,
  ConfirmEmailRequest,
  EmailOnlyRequest,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  ResetPasswordRequest,
  UpdateProfileRequest,
  User,
} from '../models/auth.model';

const TOKEN_KEY = 'todolist.token';
const REFRESH_TOKEN_KEY = 'todolist.refreshToken';

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
      tap((response) => this.setTokens(response.token, response.refreshToken)),
      switchMap(() => this.loadCurrentUser())
    );
  }

  login(request: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => this.setTokens(response.token, response.refreshToken)),
      switchMap(() => this.loadCurrentUser())
    );
  }

  updateProfile(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<AuthResponse>(`${API_BASE_URL}/auth/me`, request).pipe(
      tap((response) => this.setTokens(response.token, response.refreshToken)),
      switchMap(() => this.loadCurrentUser())
    );
  }

  confirmEmail(token: string): Observable<void> {
    const request: ConfirmEmailRequest = { token };
    return this.http.post<void>(`${API_BASE_URL}/auth/confirm-email`, request);
  }

  resendConfirmation(email: string): Observable<void> {
    const request: EmailOnlyRequest = { email };
    return this.http.post<void>(`${API_BASE_URL}/auth/resend-confirmation`, request);
  }

  forgotPassword(email: string): Observable<void> {
    const request: EmailOnlyRequest = { email };
    return this.http.post<void>(`${API_BASE_URL}/auth/forgot-password`, request);
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    const request: ResetPasswordRequest = { token, newPassword };
    return this.http.post<void>(`${API_BASE_URL}/auth/reset-password`, request);
  }

  loadCurrentUser(): Observable<User> {
    return this.http
      .get<User>(`${API_BASE_URL}/auth/me`)
      .pipe(tap((user) => this.currentUserSignal.set(user)));
  }

  hasRefreshToken(): boolean {
    return localStorage.getItem(REFRESH_TOKEN_KEY) !== null;
  }

  /** Échange le refresh token stocké contre un nouvel access token. */
  refresh(): Observable<string> {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return throwError(() => new Error('Aucun refresh token disponible'));
    }

    const request: RefreshTokenRequest = { refreshToken };
    return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, request).pipe(
      tap((response) => this.setTokens(response.token, response.refreshToken)),
      map((response) => response.token)
    );
  }

  logout(): void {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);

    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.tokenSignal.set(null);
    this.currentUserSignal.set(null);

    if (refreshToken) {
      // Révocation côté serveur en best-effort : la déconnexion côté client
      // est déjà effective quoi qu'il arrive.
      const request: RefreshTokenRequest = { refreshToken };
      this.http
        .post<void>(`${API_BASE_URL}/auth/logout`, request)
        .pipe(catchError(() => of(undefined)))
        .subscribe();
    }
  }

  private setTokens(token: string, refreshToken: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    this.tokenSignal.set(token);
  }
}
