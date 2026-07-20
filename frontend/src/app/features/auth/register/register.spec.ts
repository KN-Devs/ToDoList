import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { Register } from './register';

describe('Register', () => {
  let register: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;
  let component: Register;

  beforeEach(() => {
    register = vi.fn();

    TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), { provide: AuthService, useValue: { register } }],
    });

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
    component = TestBed.createComponent(Register).componentInstance;
  });

  it('does nothing when the form is incomplete', () => {
    component.nom = 'Dupont';
    component.prenom = '';
    component.email = 'marie@example.com';
    component.password = 'password123';

    component.submit();

    expect(register).not.toHaveBeenCalled();
  });

  it('shows the confirmation message on successful registration', () => {
    register.mockReturnValue(
      of({ id: 1, nom: 'Dupont', prenom: 'Marie', email: 'marie@example.com', role: 'USER' })
    );
    component.nom = 'Dupont';
    component.prenom = 'Marie';
    component.email = 'marie@example.com';
    component.password = 'password123';

    component.submit();

    expect(register).toHaveBeenCalledWith({
      nom: 'Dupont',
      prenom: 'Marie',
      email: 'marie@example.com',
      password: 'password123',
    });
    expect(component.registered()).toBe(true);
    expect(component.loading()).toBe(false);
  });

  it('continue() navigates to /projects', () => {
    component.continue();

    expect(navigate).toHaveBeenCalledWith(['/projects']);
  });

  it('shows an error message when registration fails', () => {
    register.mockReturnValue(throwError(() => new Error('conflict')));
    component.nom = 'Dupont';
    component.prenom = 'Marie';
    component.email = 'marie@example.com';
    component.password = 'password123';

    component.submit();

    expect(component.errorMessage()).toBe(
      "Impossible de créer le compte, vérifie les informations saisies"
    );
    expect(component.loading()).toBe(false);
    expect(component.registered()).toBe(false);
  });
});
