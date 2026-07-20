import { Injectable, effect } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, EMPTY, Observable, switchMap } from 'rxjs';
import { WS_BASE_URL } from '../config/api.config';
import { CommentEvent } from '../models/comment.model';
import { Notification } from '../models/notification.model';
import { TaskEvent } from '../models/task.model';
import { AuthService } from './auth.service';

/**
 * Le token JWT n'est vérifié qu'une fois, à la connexion STOMP (CONNECT) : la
 * connexion n'est pas recréée lors du rafraîchissement périodique de l'access
 * token (15 min). Simplification volontaire pour ce portfolio, cohérente avec
 * de nombreuses implémentations STOMP+JWT réelles.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private client: Client | null = null;
  private readonly connected$ = new BehaviorSubject(false);

  constructor(private readonly authService: AuthService) {
    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.connect();
      } else {
        this.disconnect();
      }
    });
  }

  watchProjectTasks(projectId: number): Observable<TaskEvent> {
    return this.watch<TaskEvent>(`/topic/projects/${projectId}/tasks`);
  }

  watchTaskComments(projectId: number, taskId: number): Observable<CommentEvent> {
    return this.watch<CommentEvent>(`/topic/projects/${projectId}/tasks/${taskId}/comments`);
  }

  watchNotifications(): Observable<Notification> {
    return this.watch<Notification>('/user/queue/notifications');
  }

  private connect(): void {
    if (this.client) {
      return;
    }

    const token = this.authService.token();
    if (!token) {
      return;
    }

    this.client = new Client({
      brokerURL: `${WS_BASE_URL}/ws`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => this.connected$.next(true),
      onWebSocketClose: () => this.connected$.next(false),
      onStompError: () => this.connected$.next(false),
    });

    this.client.activate();
  }

  private disconnect(): void {
    this.connected$.next(false);
    this.client?.deactivate();
    this.client = null;
  }

  /**
   * switchMap must see every connected$ emission, including `false` — a plain
   * filter().switchMap() would drop it and leave the previous STOMP
   * subscription dangling instead of tearing it down on disconnect.
   */
  private watch<T>(destination: string): Observable<T> {
    return this.connected$.pipe(
      switchMap((connected) => {
        if (!connected) {
          return EMPTY;
        }

        return new Observable<T>((subscriber) => {
          const subscription = this.client!.subscribe(destination, (message: IMessage) => {
            subscriber.next(JSON.parse(message.body) as T);
          });
          return () => subscription.unsubscribe();
        });
      })
    );
  }
}
