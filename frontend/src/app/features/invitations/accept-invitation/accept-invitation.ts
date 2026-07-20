import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { InvitationService } from '../../../core/services/invitation.service';

@Component({
  selector: 'app-accept-invitation',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './accept-invitation.html',
  styleUrl: './accept-invitation.scss',
})
export class AcceptInvitation implements OnInit {
  readonly loading = signal(true);
  readonly projectNom = signal<string | null>(null);
  readonly projectId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly invitationService: InvitationService,
    protected readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.loading.set(false);
      this.errorMessage.set("Lien d'invitation invalide.");
      return;
    }

    this.invitationService.accept(token).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.projectNom.set(response.projectNom);
        this.projectId.set(response.projectId);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set("Cette invitation est invalide, a expiré ou a déjà été utilisée.");
      },
    });
  }

  goToProject(): void {
    const id = this.projectId();
    if (id) {
      this.router.navigate(['/projects', id]);
    }
  }
}
