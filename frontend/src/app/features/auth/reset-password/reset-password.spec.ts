import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { ResetPassword } from './reset-password';

describe('ResetPassword', () => {
  let resetPassword: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  function configure(token: string | null) {
    resetPassword = vi.fn();

    TestBed.configureTestingModule({
      imports: [ResetPassword],
      providers: [
        { provide: AuthService, useValue: { resetPassword } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => token } } } },
      ],
    });

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
    const component = TestBed.createComponent(ResetPassword).componentInstance;
    component.ngOnInit();
    return component;
  }

  it('shows an error when no token is present in the URL', () => {
    const component = configure(null);

    expect(component.errorMessage()).toBe('Lien de réinitialisation invalide.');
  });

  it('resets the password and shows success', () => {
    const component = configure('some-token');
    resetPassword.mockReturnValue(of(undefined));
    component.newPassword = 'NewPassword123!';

    component.submit();

    expect(resetPassword).toHaveBeenCalledWith('some-token', 'NewPassword123!');
    expect(component.success()).toBe(true);
  });

  it('shows an expiry-specific message on a 410 response', () => {
    const component = configure('some-token');
    resetPassword.mockReturnValue(throwError(() => ({ status: 410 })));
    component.newPassword = 'NewPassword123!';

    component.submit();

    expect(component.errorMessage()).toContain('expiré');
  });

  it('goToLogin() navigates to /login', () => {
    const component = configure('some-token');

    component.goToLogin();

    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
