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
}

export type Role = 'ADMIN' | 'USER';

export interface User {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: Role;
}
