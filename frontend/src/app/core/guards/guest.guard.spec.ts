import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
  let navigate: ReturnType<typeof vi.fn>;

  function setup(isAuthenticated: boolean) {
    navigate = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated } },
        { provide: Router, useValue: { navigate } },
      ],
    });
  }

  it('allows access when not authenticated', () => {
    setup(false);

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as never, {} as never)
    );

    expect(result).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('redirects to /tasks and blocks access when already authenticated', () => {
    setup(true);

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as never, {} as never)
    );

    expect(result).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/tasks']);
  });
});
