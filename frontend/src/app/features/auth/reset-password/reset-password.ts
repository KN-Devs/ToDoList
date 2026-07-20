import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.scss',
})
export class ResetPassword implements OnInit {
  newPassword = '';

  readonly token = signal<string | null>(null);
  readonly loading = signal(false);
  readonly success = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token'));
    if (!this.token()) {
      this.errorMessage.set('Lien de réinitialisation invalide.');
    }
  }

  submit(): void {
    const token = this.token();
    if (!token || !this.newPassword) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.resetPassword(token, this.newPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(
          error.status === 410
            ? 'Ce lien a expiré ou a déjà été utilisé, merci de refaire une demande.'
            : 'Impossible de réinitialiser le mot de passe.'
        );
      },
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
