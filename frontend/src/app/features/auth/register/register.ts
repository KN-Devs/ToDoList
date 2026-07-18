import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  nom = '';
  prenom = '';
  email = '';
  password = '';

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  submit(): void {
    if (!this.nom || !this.prenom || !this.email || !this.password) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService
      .register({ nom: this.nom, prenom: this.prenom, email: this.email, password: this.password })
      .subscribe({
        next: () => this.router.navigate(['/tasks']),
        error: () => {
          this.errorMessage.set("Impossible de créer le compte, vérifie les informations saisies");
          this.loading.set(false);
        },
      });
  }
}
