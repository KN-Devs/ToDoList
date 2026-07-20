import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { Login } from './login';

describe('Login', () => {
  let login: ReturnType<typeof vi.fn>;
  let resendConfirmation: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;
  let component: Login;

  beforeEach(() => {
    login = vi.fn();
    resendConfirmation = vi.fn();

    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: { login, resendConfirmation } }],
    });

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
    component = TestBed.createComponent(Login).componentInstance;
  });

  it('does nothing when the form is incomplete', () => {
    component.email = '';
    component.password = '';

    component.submit();

    expect(login).not.toHaveBeenCalled();
  });

  it('navigates to /projects on successful login', () => {
    login.mockReturnValue(of({ id: 1, nom: 'Dupont', prenom: 'Marie', email: 'marie@example.com', role: 'USER' }));
    component.email = 'marie@example.com';
    component.password = 'password123';

    component.submit();

    expect(login).toHaveBeenCalledWith({ email: 'marie@example.com', password: 'password123' });
    expect(navigate).toHaveBeenCalledWith(['/projects']);
  });

  it('shows a generic error message on incorrect credentials', () => {
    login.mockReturnValue(throwError(() => ({ status: 401, error: 'Email ou mot de passe incorrect' })));
    component.email = 'marie@example.com';
    component.password = 'wrong-password';

    component.submit();

    expect(component.errorMessage()).toBe('Email ou mot de passe incorrect');
    expect(component.loading()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the lockout message returned by the backend on a 423 response', () => {
    const lockoutMessage = 'Compte temporairement verrouillé suite à plusieurs échecs de connexion. Réessayez dans 5 minutes.';
    login.mockReturnValue(throwError(() => ({ status: 423, error: lockoutMessage })));
    component.email = 'marie@example.com';
    component.password = 'wrong-password';

    component.submit();

    expect(component.errorMessage()).toBe(lockoutMessage);
    expect(component.loading()).toBe(false);
  });

  it('offers to resend the confirmation email on a 403 response', () => {
    const message = 'Veuillez confirmer votre adresse email avant de vous connecter.';
    login.mockReturnValue(throwError(() => ({ status: 403, error: message })));
    component.email = 'marie@example.com';
    component.password = 'password123';

    component.submit();

    expect(component.needsConfirmation()).toBe(true);
    expect(component.errorMessage()).toBe(message);

    resendConfirmation.mockReturnValue(of(undefined));
    component.resendConfirmation();

    expect(resendConfirmation).toHaveBeenCalledWith('marie@example.com');
    expect(component.resendSent()).toBe(true);
  });
});
