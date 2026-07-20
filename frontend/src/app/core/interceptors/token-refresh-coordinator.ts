import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/**
 * État partagé entre toutes les requêtes HTTP concurrentes de l'interceptor
 * d'authentification, pour n'effectuer qu'un seul appel de rafraîchissement
 * à la fois même si plusieurs requêtes échouent en même temps avec un token
 * expiré. Injectable (plutôt qu'un état de module) pour rester isolé entre
 * chaque exécution de test.
 */
@Injectable({ providedIn: 'root' })
export class TokenRefreshCoordinator {
  isRefreshing = false;
  readonly refreshedToken$ = new BehaviorSubject<string | null>(null);
}
