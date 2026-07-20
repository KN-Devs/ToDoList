import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Comment } from '../models/comment.model';

@Injectable({ providedIn: 'root' })
export class CommentService {
  constructor(private readonly http: HttpClient) {}

  getForTask(taskId: number): Observable<Comment[]> {
    return this.http.get<Comment[]>(`${API_BASE_URL}/tasks/${taskId}/comments`);
  }

  create(taskId: number, content: string): Observable<Comment> {
    return this.http.post<Comment>(`${API_BASE_URL}/tasks/${taskId}/comments`, { content });
  }

  delete(taskId: number, commentId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/tasks/${taskId}/comments/${commentId}`);
  }
}
