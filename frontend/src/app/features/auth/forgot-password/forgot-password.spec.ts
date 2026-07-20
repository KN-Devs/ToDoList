import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { ForgotPassword } from './forgot-password';

describe('ForgotPassword', () => {
  let forgotPassword: ReturnType<typeof vi.fn>;
  let component: ForgotPassword;

  beforeEach(() => {
    forgotPassword = vi.fn();

    TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [provideRouter([]), { provide: AuthService, useValue: { forgotPassword } }],
    });

    component = TestBed.createComponent(ForgotPassword).componentInstance;
  });

  it('does nothing when the email is empty', () => {
    component.email = '';

    component.submit();

    expect(forgotPassword).not.toHaveBeenCalled();
  });

  it('sends the request and shows the confirmation message', () => {
    forgotPassword.mockReturnValue(of(undefined));
    component.email = 'marie@example.com';

    component.submit();

    expect(forgotPassword).toHaveBeenCalledWith('marie@example.com');
    expect(component.sent()).toBe(true);
    expect(component.loading()).toBe(false);
  });
});
