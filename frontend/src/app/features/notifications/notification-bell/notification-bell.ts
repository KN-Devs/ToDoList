import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { interval, startWith, switchMap } from 'rxjs';
import { Notification } from '../../../core/models/notification.model';
import { NotificationService } from '../../../core/services/notification.service';
import { RealtimeService } from '../../../core/services/realtime.service';

const POLL_INTERVAL_MS = 30000;

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.scss',
})
export class NotificationBell implements OnInit {
  readonly panelOpen = signal(false);
  readonly unreadCount = signal(0);
  readonly notifications = signal<Notification[]>([]);
  readonly loading = signal(false);

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private readonly notificationService: NotificationService,
    private readonly realtimeService: RealtimeService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    interval(POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.notificationService.getUnreadCount()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (result) => this.unreadCount.set(result.count),
        error: () => {},
      });

    this.realtimeService
      .watchNotifications()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((notification) => {
        this.unreadCount.update((count) => count + 1);
        if (this.panelOpen()) {
          this.notifications.update((list) => [notification, ...list]);
        }
      });
  }

  togglePanel(): void {
    const opening = !this.panelOpen();
    this.panelOpen.set(opening);
    if (opening) {
      this.loadNotifications();
    }
  }

  closePanel(): void {
    this.panelOpen.set(false);
  }

  loadNotifications(): void {
    this.loading.set(true);
    this.notificationService.getAll().subscribe({
      next: (notifications) => {
        this.notifications.set(notifications);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openNotification(notification: Notification): void {
    if (notification.id !== null && !notification.read) {
      this.notificationService.markRead(notification.id).subscribe();
      this.unreadCount.update((count) => Math.max(0, count - 1));
      this.notifications.update((list) =>
        list.map((n) => (n.id === notification.id ? { ...n, read: true } : n))
      );
    }

    this.closePanel();

    if (notification.projectId !== null) {
      this.router.navigate(['/projects', notification.projectId]);
    }
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe(() => {
      this.notifications.update((list) => list.map((n) => (n.id !== null ? { ...n, read: true } : n)));
      this.notificationService.getUnreadCount().subscribe((result) => this.unreadCount.set(result.count));
    });
  }

  trackNotification(notification: Notification): string {
    return notification.id !== null ? `n${notification.id}` : `t${notification.taskId}`;
  }
}
