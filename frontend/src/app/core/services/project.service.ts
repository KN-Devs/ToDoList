import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Project, ProjectRequest } from '../models/project.model';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<Project[]> {
    return this.http.get<Project[]>(`${API_BASE_URL}/projects`);
  }

  getById(id: number): Observable<Project> {
    return this.http.get<Project>(`${API_BASE_URL}/projects/${id}`);
  }

  create(request: ProjectRequest): Observable<Project> {
    return this.http.post<Project>(`${API_BASE_URL}/projects`, request);
  }

  update(id: number, request: ProjectRequest): Observable<Project> {
    return this.http.put<Project>(`${API_BASE_URL}/projects/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/projects/${id}`);
  }

  inviteMember(id: number, email: string): Observable<Project> {
    return this.http.post<Project>(`${API_BASE_URL}/projects/${id}/invitations`, { email });
  }

  cancelInvitation(id: number, email: string): Observable<Project> {
    return this.http.delete<Project>(
      `${API_BASE_URL}/projects/${id}/invitations/${encodeURIComponent(email)}`
    );
  }

  removeMember(id: number, email: string): Observable<Project> {
    return this.http.delete<Project>(
      `${API_BASE_URL}/projects/${id}/members/${encodeURIComponent(email)}`
    );
  }

  updateMemberPermission(id: number, email: string, canManageTasks: boolean): Observable<Project> {
    return this.http.patch<Project>(
      `${API_BASE_URL}/projects/${id}/members/${encodeURIComponent(email)}`,
      { canManageTasks }
    );
  }
}
