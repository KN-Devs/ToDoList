import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
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

  it('allows access when authenticated', () => {
    setup(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, {} as never)
    );

    expect(result).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('redirects to /login and blocks access when not authenticated', () => {
    setup(false);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, {} as never)
    );

    expect(result).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
