import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { RealtimeService } from './realtime.service';

const activateMock = vi.fn();
const deactivateMock = vi.fn();
const subscribeMock = vi.fn();
const unsubscribeMock = vi.fn();
let lastClientConfig: {
  brokerURL: string;
  connectHeaders: Record<string, string>;
  onConnect: () => void;
  onWebSocketClose: () => void;
} | null = null;

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    constructor(config: typeof lastClientConfig) {
      lastClientConfig = config;
    }
    activate = activateMock;
    deactivate = deactivateMock;
    subscribe = subscribeMock.mockReturnValue({ unsubscribe: unsubscribeMock });
  },
}));

describe('RealtimeService', () => {
  let isAuthenticated: ReturnType<typeof signal<boolean>>;
  let token: ReturnType<typeof signal<string | null>>;
  let service: RealtimeService;

  beforeEach(() => {
    activateMock.mockClear();
    deactivateMock.mockClear();
    subscribeMock.mockClear();
    unsubscribeMock.mockClear();
    lastClientConfig = null;

    isAuthenticated = signal(false);
    token = signal<string | null>(null);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { isAuthenticated, token },
        },
      ],
    });

    service = TestBed.inject(RealtimeService);
    TestBed.tick();
  });

  it('does not connect while unauthenticated', () => {
    expect(activateMock).not.toHaveBeenCalled();
  });

  it('connects with the current token once authenticated', () => {
    token.set('jwt-abc');
    isAuthenticated.set(true);
    TestBed.tick();

    expect(activateMock).toHaveBeenCalledTimes(1);
    expect(lastClientConfig?.connectHeaders['Authorization']).toBe('Bearer jwt-abc');
  });

  it('disconnects when the user logs out', () => {
    token.set('jwt-abc');
    isAuthenticated.set(true);
    TestBed.tick();

    isAuthenticated.set(false);
    TestBed.tick();

    expect(deactivateMock).toHaveBeenCalledTimes(1);
  });

  it('does not reconnect on a token refresh while still authenticated', () => {
    token.set('jwt-abc');
    isAuthenticated.set(true);
    TestBed.tick();

    token.set('jwt-refreshed');
    TestBed.tick();

    expect(activateMock).toHaveBeenCalledTimes(1);
  });

  it('subscribes to the destination only after the STOMP connection is established', () => {
    token.set('jwt-abc');
    isAuthenticated.set(true);
    TestBed.tick();

    const received: unknown[] = [];
    service.watchProjectTasks(5).subscribe((event) => received.push(event));

    expect(subscribeMock).not.toHaveBeenCalled();

    lastClientConfig!.onConnect();

    expect(subscribeMock).toHaveBeenCalledWith('/topic/projects/5/tasks', expect.any(Function));

    const handler = subscribeMock.mock.calls[0][1];
    handler({ body: JSON.stringify({ action: 'CREATED', task: { id: 1 } }) });

    expect(received).toEqual([{ action: 'CREATED', task: { id: 1 } }]);
  });

  it('watchTaskComments() targets the task-scoped comment topic', () => {
    token.set('jwt-abc');
    isAuthenticated.set(true);
    TestBed.tick();

    service.watchTaskComments(5, 42).subscribe();
    lastClientConfig!.onConnect();

    expect(subscribeMock).toHaveBeenCalledWith('/topic/projects/5/tasks/42/comments', expect.any(Function));
  });

  it('watchNotifications() targets the per-user notification queue', () => {
    token.set('jwt-abc');
    isAuthenticated.set(true);
    TestBed.tick();

    service.watchNotifications().subscribe();
    lastClientConfig!.onConnect();

    expect(subscribeMock).toHaveBeenCalledWith('/user/queue/notifications', expect.any(Function));
  });

  it('tears down the STOMP subscription when the WebSocket closes', () => {
    token.set('jwt-abc');
    isAuthenticated.set(true);
    TestBed.tick();

    service.watchProjectTasks(5).subscribe();
    lastClientConfig!.onConnect();
    expect(subscribeMock).toHaveBeenCalledTimes(1);

    lastClientConfig!.onWebSocketClose();

    expect(unsubscribeMock).toHaveBeenCalledTimes(1);
  });
});
