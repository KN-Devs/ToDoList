import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AccountMenu } from './features/account/account-menu/account-menu';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AccountMenu],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  constructor(protected readonly authService: AuthService) {}

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.authService.loadCurrentUser().subscribe();
    }
  }
}
