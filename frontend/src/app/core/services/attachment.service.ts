import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Attachment } from '../models/attachment.model';

@Injectable({ providedIn: 'root' })
export class AttachmentService {
  constructor(private readonly http: HttpClient) {}

  getForTask(taskId: number): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(`${API_BASE_URL}/tasks/${taskId}/attachments`);
  }

  upload(taskId: number, file: File): Observable<Attachment> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Attachment>(`${API_BASE_URL}/tasks/${taskId}/attachments`, formData);
  }

  download(taskId: number, attachmentId: number): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/tasks/${taskId}/attachments/${attachmentId}/download`, {
      responseType: 'blob',
    });
  }

  delete(taskId: number, attachmentId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/tasks/${taskId}/attachments/${attachmentId}`);
  }
}
