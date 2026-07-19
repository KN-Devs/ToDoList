import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { User } from '../../../core/models/auth.model';
import { AccountMenu } from './account-menu';

const USER: User = { id: 1, nom: 'Dupont', prenom: 'Marie', email: 'marie@example.com', role: 'USER' };

describe('AccountMenu', () => {
  let authService: {
    currentUser: ReturnType<typeof vi.fn>;
    isAdmin: ReturnType<typeof vi.fn>;
    updateProfile: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let component: AccountMenu;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    authService = {
      currentUser: vi.fn().mockReturnValue(USER),
      isAdmin: vi.fn().mockReturnValue(false),
      updateProfile: vi.fn(),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [AccountMenu],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }],
    });

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
    component = TestBed.createComponent(AccountMenu).componentInstance;
  });

  it('initials() returns the first letter of the first and last name', () => {
    expect(component.initials()).toBe('MD');
  });

  it('initials() falls back to "?" when there is no current user', () => {
    authService.currentUser.mockReturnValue(null);

    expect(component.initials()).toBe('?');
  });

  it('toggleMenu() opens and closes the panel', () => {
    expect(component.menuOpen()).toBe(false);

    component.toggleMenu();
    expect(component.menuOpen()).toBe(true);

    component.toggleMenu();
    expect(component.menuOpen()).toBe(false);
  });

  it('closeMenu() resets the menu and the editing state', () => {
    component.toggleMenu();
    component.startEditing();

    component.closeMenu();

    expect(component.menuOpen()).toBe(false);
    expect(component.editing()).toBe(false);
  });

  it('startEditing() prefills the form from the current user', () => {
    component.startEditing();

    expect(component.editing()).toBe(true);
    expect(component.nom).toBe(USER.nom);
    expect(component.prenom).toBe(USER.prenom);
    expect(component.email).toBe(USER.email);
  });

  it('cancelEditing() exits edit mode without closing the menu', () => {
    component.toggleMenu();
    component.startEditing();

    component.cancelEditing();

    expect(component.editing()).toBe(false);
    expect(component.menuOpen()).toBe(true);
  });

  describe('saveProfile', () => {
    it('does nothing when a field is missing', () => {
      component.nom = '';
      component.prenom = 'Marie';
      component.email = 'marie@example.com';

      component.saveProfile();

      expect(authService.updateProfile).not.toHaveBeenCalled();
    });

    it('closes the menu on success', () => {
      authService.updateProfile.mockReturnValue(of(USER));
      component.toggleMenu();
      component.startEditing();

      component.saveProfile();

      expect(authService.updateProfile).toHaveBeenCalledWith({
        nom: USER.nom,
        prenom: USER.prenom,
        email: USER.email,
      });
      expect(component.saving()).toBe(false);
      expect(component.editing()).toBe(false);
      expect(component.menuOpen()).toBe(false);
    });

    it('shows a dedicated message on a 409 conflict', () => {
      authService.updateProfile.mockReturnValue(throwError(() => ({ status: 409 })));
      component.startEditing();

      component.saveProfile();

      expect(component.errorMessage()).toBe('Cet email est déjà utilisé');
      expect(component.saving()).toBe(false);
    });

    it('shows a generic message on other errors', () => {
      authService.updateProfile.mockReturnValue(throwError(() => ({ status: 500 })));
      component.startEditing();

      component.saveProfile();

      expect(component.errorMessage()).toBe("Impossible de mettre à jour tes informations");
    });
  });

  it('logout() logs out and navigates to /login', () => {
    component.logout();

    expect(authService.logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
