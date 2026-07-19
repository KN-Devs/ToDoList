import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Task, TaskRequest } from '../models/task.model';

@Injectable({ providedIn: 'root' })
export class TaskService {
  constructor(private readonly http: HttpClient) {}

  getAllForProject(projectId: number): Observable<Task[]> {
    return this.http.get<Task[]>(`${API_BASE_URL}/projects/${projectId}/tasks`);
  }

  create(projectId: number, request: TaskRequest): Observable<Task> {
    return this.http.post<Task>(`${API_BASE_URL}/projects/${projectId}/tasks`, request);
  }

  update(id: number, request: TaskRequest): Observable<Task> {
    return this.http.put<Task>(`${API_BASE_URL}/tasks/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/tasks/${id}`);
  }
}
