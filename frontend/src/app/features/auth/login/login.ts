import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  email = '';
  password = '';

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly needsConfirmation = signal(false);
  readonly resendSent = signal(false);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  submit(): void {
    if (!this.email || !this.password) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.needsConfirmation.set(false);
    this.resendSent.set(false);

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: () => this.router.navigate(['/projects']),
      error: (error: HttpErrorResponse) => {
        if (error.status === 403) {
          this.needsConfirmation.set(true);
          this.errorMessage.set(
            typeof error.error === 'string' ? error.error : 'Veuillez confirmer votre adresse email.'
          );
        } else if (error.status === 423 && typeof error.error === 'string') {
          this.errorMessage.set(error.error);
        } else {
          this.errorMessage.set('Email ou mot de passe incorrect');
        }
        this.loading.set(false);
      },
    });
  }

  resendConfirmation(): void {
    this.authService.resendConfirmation(this.email).subscribe(() => this.resendSent.set(true));
  }
}
