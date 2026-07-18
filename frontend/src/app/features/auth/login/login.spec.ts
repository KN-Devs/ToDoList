import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { Login } from './login';

describe('Login', () => {
  let login: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;
  let component: Login;

  beforeEach(() => {
    login = vi.fn();

    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: { login } }],
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

  it('navigates to /tasks on successful login', () => {
    login.mockReturnValue(of({ id: 1, nom: 'Dupont', prenom: 'Marie', email: 'marie@example.com', role: 'USER' }));
    component.email = 'marie@example.com';
    component.password = 'password123';

    component.submit();

    expect(login).toHaveBeenCalledWith({ email: 'marie@example.com', password: 'password123' });
    expect(navigate).toHaveBeenCalledWith(['/tasks']);
  });

  it('shows an error message when login fails', () => {
    login.mockReturnValue(throwError(() => new Error('unauthorized')));
    component.email = 'marie@example.com';
    component.password = 'wrong-password';

    component.submit();

    expect(component.errorMessage()).toBe('Email ou mot de passe incorrect');
    expect(component.loading()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });
});
