import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmEmail } from './confirm-email';

describe('ConfirmEmail', () => {
  let confirmEmail: ReturnType<typeof vi.fn>;

  function configure(token: string | null) {
    confirmEmail = vi.fn();

    TestBed.configureTestingModule({
      imports: [ConfirmEmail],
      providers: [
        { provide: AuthService, useValue: { confirmEmail } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => token } } } },
      ],
    });

    return TestBed.createComponent(ConfirmEmail).componentInstance;
  }

  it('shows an error when no token is present in the URL', () => {
    const component = configure(null);

    component.ngOnInit();

    expect(component.errorMessage()).toBe('Lien de confirmation invalide.');
    expect(confirmEmail).not.toHaveBeenCalled();
  });

  it('confirms the email and shows success', () => {
    const component = configure('some-token');
    confirmEmail.mockReturnValue(of(undefined));

    component.ngOnInit();

    expect(confirmEmail).toHaveBeenCalledWith('some-token');
    expect(component.success()).toBe(true);
    expect(component.loading()).toBe(false);
  });

  it('shows an error when the token is invalid or expired', () => {
    const component = configure('bad-token');
    confirmEmail.mockReturnValue(throwError(() => ({ status: 410 })));

    component.ngOnInit();

    expect(component.success()).toBe(false);
    expect(component.errorMessage()).toContain('invalide');
  });
});
