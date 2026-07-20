export interface RegisterRequest {
  nom: string;
  prenom: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UpdateProfileRequest {
  nom: string;
  prenom: string;
  email: string;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface EmailOnlyRequest {
  email: string;
}

export interface ConfirmEmailRequest {
  token: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export type Role = 'ADMIN' | 'USER';

export interface User {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: Role;
}
