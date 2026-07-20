import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { InvitationAcceptResponse } from '../models/invitation.model';

@Injectable({ providedIn: 'root' })
export class InvitationService {
  constructor(private readonly http: HttpClient) {}

  accept(token: string): Observable<InvitationAcceptResponse> {
    return this.http.post<InvitationAcceptResponse>(`${API_BASE_URL}/invitations/accept`, { token });
  }
}
