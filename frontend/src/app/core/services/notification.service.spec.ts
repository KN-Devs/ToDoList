import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../config/api.config';
import { Notification, UnreadCount } from '../models/notification.model';
import { NotificationService } from './notification.service';

const NOTIFICATION: Notification = {
  id: 1,
  type: 'PROJECT_INVITATION',
  message: 'Marie vous a invité à rejoindre le projet "Refonte du site"',
  projectId: 5,
  taskId: null,
  createdAt: '2026-01-01T10:00:00Z',
  read: false,
};

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [NotificationService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getUnreadCount() fetches the unread count', () => {
    let result: UnreadCount | undefined;
    service.getUnreadCount().subscribe((count) => (result = count));

    const req = httpMock.expectOne(`${API_BASE_URL}/notifications/unread-count`);
    expect(req.request.method).toBe('GET');
    req.flush({ count: 3 });

    expect(result).toEqual({ count: 3 });
  });

  it('getAll() fetches the full notification list', () => {
    let result: Notification[] | undefined;
    service.getAll().subscribe((notifications) => (result = notifications));

    const req = httpMock.expectOne(`${API_BASE_URL}/notifications`);
    expect(req.request.method).toBe('GET');
    req.flush([NOTIFICATION]);

    expect(result).toEqual([NOTIFICATION]);
  });

  it('markRead() posts to the read endpoint', () => {
    let completed = false;
    service.markRead(1).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${API_BASE_URL}/notifications/1/read`);
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(completed).toBe(true);
  });

  it('markAllRead() posts to the read-all endpoint', () => {
    let completed = false;
    service.markAllRead().subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${API_BASE_URL}/notifications/read-all`);
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(completed).toBe(true);
  });
});
