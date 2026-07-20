import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Notification, UnreadCount } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private readonly http: HttpClient) {}

  getUnreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(`${API_BASE_URL}/notifications/unread-count`);
  }

  getAll(): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${API_BASE_URL}/notifications`);
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/notifications/read-all`, {});
  }
}
