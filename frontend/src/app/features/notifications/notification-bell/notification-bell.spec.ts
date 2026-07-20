import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { vi } from 'vitest';
import { Notification } from '../../../core/models/notification.model';
import { NotificationService } from '../../../core/services/notification.service';
import { RealtimeService } from '../../../core/services/realtime.service';
import { NotificationBell } from './notification-bell';

const INVITATION: Notification = {
  id: 1,
  type: 'PROJECT_INVITATION',
  message: 'Marie vous a invité à rejoindre le projet "Refonte du site"',
  projectId: 5,
  taskId: null,
  createdAt: '2026-01-01T10:00:00Z',
  read: false,
};

const DUE_SOON: Notification = {
  id: null,
  type: 'TASK_DUE_SOON',
  message: 'Échéance proche : "Tache" (2026-01-05)',
  projectId: 5,
  taskId: 42,
  createdAt: '2026-01-04T00:00:00Z',
  read: false,
};

describe('NotificationBell', () => {
  let notificationService: {
    getUnreadCount: ReturnType<typeof vi.fn>;
    getAll: ReturnType<typeof vi.fn>;
    markRead: ReturnType<typeof vi.fn>;
    markAllRead: ReturnType<typeof vi.fn>;
  };
  let navigate: ReturnType<typeof vi.fn>;
  let component: NotificationBell;
  let notificationEvents$: Subject<Notification>;
  let realtimeService: { watchNotifications: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.useFakeTimers();

    notificationService = {
      getUnreadCount: vi.fn().mockReturnValue(of({ count: 0 })),
      getAll: vi.fn().mockReturnValue(of([])),
      markRead: vi.fn().mockReturnValue(of(undefined)),
      markAllRead: vi.fn().mockReturnValue(of(undefined)),
    };
    navigate = vi.fn();
    notificationEvents$ = new Subject<Notification>();
    realtimeService = { watchNotifications: vi.fn().mockReturnValue(notificationEvents$) };

    TestBed.configureTestingModule({
      imports: [NotificationBell],
      providers: [
        { provide: NotificationService, useValue: notificationService },
        { provide: RealtimeService, useValue: realtimeService },
        { provide: Router, useValue: { navigate } },
      ],
    });

    component = TestBed.createComponent(NotificationBell).componentInstance;
  });

  afterEach(() => vi.useRealTimers());

  it('fetches the unread count immediately on init', () => {
    notificationService.getUnreadCount.mockReturnValue(of({ count: 4 }));

    component.ngOnInit();

    expect(notificationService.getUnreadCount).toHaveBeenCalledTimes(1);
    expect(component.unreadCount()).toBe(4);
  });

  it('polls the unread count again after the interval elapses', () => {
    component.ngOnInit();
    expect(notificationService.getUnreadCount).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(30000);

    // Le nombre exact de relances dépend du scheduler RxJS sous horloge
    // simulée ; on vérifie seulement qu'un nouveau sondage a bien eu lieu.
    expect(notificationService.getUnreadCount.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('togglePanel() opens the panel and loads notifications', () => {
    notificationService.getAll.mockReturnValue(of([INVITATION]));

    component.togglePanel();

    expect(component.panelOpen()).toBe(true);
    expect(notificationService.getAll).toHaveBeenCalledTimes(1);
    expect(component.notifications()).toEqual([INVITATION]);
  });

  it('togglePanel() closes the panel without reloading', () => {
    component.togglePanel();
    notificationService.getAll.mockClear();

    component.togglePanel();

    expect(component.panelOpen()).toBe(false);
    expect(notificationService.getAll).not.toHaveBeenCalled();
  });

  it('openNotification() marks a persisted unread notification as read and navigates', () => {
    component.notifications.set([INVITATION]);
    component.unreadCount.set(1);

    component.openNotification(INVITATION);

    expect(notificationService.markRead).toHaveBeenCalledWith(1);
    expect(component.unreadCount()).toBe(0);
    expect(component.notifications()[0].read).toBe(true);
    expect(component.panelOpen()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/projects', 5]);
  });

  it('openNotification() does not call markRead for an already-read notification', () => {
    component.openNotification({ ...INVITATION, read: true });

    expect(notificationService.markRead).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/projects', 5]);
  });

  it('openNotification() does not call markRead for a due-date (virtual) notification', () => {
    component.openNotification(DUE_SOON);

    expect(notificationService.markRead).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/projects', 5]);
  });

  it('markAllRead() marks every persisted notification as read but leaves due-date ones untouched', () => {
    component.notifications.set([INVITATION, DUE_SOON]);
    notificationService.getUnreadCount.mockReturnValue(of({ count: 1 }));

    component.markAllRead();

    expect(notificationService.markAllRead).toHaveBeenCalledTimes(1);
    const [invitation, dueSoon] = component.notifications();
    expect(invitation.read).toBe(true);
    expect(dueSoon.read).toBe(false);
    expect(component.unreadCount()).toBe(1);
  });

  it('trackNotification() returns distinct keys for persisted and virtual notifications', () => {
    expect(component.trackNotification(INVITATION)).toBe('n1');
    expect(component.trackNotification(DUE_SOON)).toBe('t42');
  });

  describe('real-time push', () => {
    it('subscribes to the user notification queue on init', () => {
      component.ngOnInit();

      expect(realtimeService.watchNotifications).toHaveBeenCalledTimes(1);
    });

    it('increments the unread count when a notification is pushed', () => {
      component.ngOnInit();
      component.unreadCount.set(2);

      notificationEvents$.next(INVITATION);

      expect(component.unreadCount()).toBe(3);
    });

    it('prepends the pushed notification to the list when the panel is open', () => {
      component.ngOnInit();
      component.panelOpen.set(true);
      component.notifications.set([DUE_SOON]);

      notificationEvents$.next(INVITATION);

      expect(component.notifications()).toEqual([INVITATION, DUE_SOON]);
    });

    it('does not touch the visible list when the panel is closed', () => {
      component.ngOnInit();
      component.notifications.set([DUE_SOON]);

      notificationEvents$.next(INVITATION);

      expect(component.notifications()).toEqual([DUE_SOON]);
    });
  });
});
