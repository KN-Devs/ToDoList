import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { InvitationService } from '../../../core/services/invitation.service';
import { AcceptInvitation } from './accept-invitation';

describe('AcceptInvitation', () => {
  let accept: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  function configure(token: string | null, isAuthenticated = true) {
    accept = vi.fn();

    TestBed.configureTestingModule({
      imports: [AcceptInvitation],
      providers: [
        { provide: InvitationService, useValue: { accept } },
        { provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => token } } } },
      ],
    });

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
    return TestBed.createComponent(AcceptInvitation).componentInstance;
  }

  it('shows an error when no token is present in the URL', () => {
    const component = configure(null);

    component.ngOnInit();

    expect(component.errorMessage()).toContain('invalide');
    expect(accept).not.toHaveBeenCalled();
  });

  it('accepts the invitation and stores the project info', () => {
    const component = configure('some-token');
    accept.mockReturnValue(of({ projectId: 5, projectNom: 'Refonte du site' }));

    component.ngOnInit();

    expect(accept).toHaveBeenCalledWith('some-token');
    expect(component.projectNom()).toBe('Refonte du site');
    expect(component.projectId()).toBe(5);
  });

  it('shows an error when the invitation is invalid or expired', () => {
    const component = configure('bad-token');
    accept.mockReturnValue(throwError(() => ({ status: 410 })));

    component.ngOnInit();

    expect(component.projectNom()).toBeNull();
    expect(component.errorMessage()).toContain('invalide');
  });

  it('goToProject() navigates to the accepted project', () => {
    const component = configure('some-token');
    accept.mockReturnValue(of({ projectId: 5, projectNom: 'Refonte du site' }));
    component.ngOnInit();

    component.goToProject();

    expect(navigate).toHaveBeenCalledWith(['/projects', 5]);
  });
});
