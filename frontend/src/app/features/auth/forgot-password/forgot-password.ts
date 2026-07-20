import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword {
  email = '';

  readonly loading = signal(false);
  readonly sent = signal(false);

  constructor(private readonly authService: AuthService) {}

  submit(): void {
    if (!this.email) {
      return;
    }

    this.loading.set(true);
    this.authService.forgotPassword(this.email).subscribe(() => {
      this.loading.set(false);
      this.sent.set(true);
    });
  }
}
