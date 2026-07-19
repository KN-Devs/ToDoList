import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-account-menu',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './account-menu.html',
  styleUrl: './account-menu.scss',
})
export class AccountMenu {
  readonly menuOpen = signal(false);
  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  nom = '';
  prenom = '';
  email = '';

  constructor(
    protected readonly authService: AuthService,
    private readonly router: Router
  ) {}

  initials(): string {
    const user = this.authService.currentUser();
    if (!user) {
      return '?';
    }
    return `${user.prenom.charAt(0)}${user.nom.charAt(0)}`.toUpperCase();
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
    this.editing.set(false);
    this.errorMessage.set(null);
  }

  startEditing(): void {
    const user = this.authService.currentUser();
    if (!user) {
      return;
    }

    this.nom = user.nom;
    this.prenom = user.prenom;
    this.email = user.email;
    this.errorMessage.set(null);
    this.editing.set(true);
  }

  cancelEditing(): void {
    this.editing.set(false);
    this.errorMessage.set(null);
  }

  saveProfile(): void {
    if (!this.nom || !this.prenom || !this.email) {
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    this.authService
      .updateProfile({ nom: this.nom, prenom: this.prenom, email: this.email })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.editing.set(false);
          this.menuOpen.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 409 ? 'Cet email est déjà utilisé' : "Impossible de mettre à jour tes informations"
          );
        },
      });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
