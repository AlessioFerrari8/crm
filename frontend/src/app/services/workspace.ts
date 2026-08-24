import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, Service, signal, WritableSignal } from '@angular/core';
import { catchError, Observable, of, tap } from 'rxjs';
import { API_BASE_URL } from '../core/api-config';
import { IWorkspace, IWorkspaceProfile } from '../models/IWorkspace';

@Service()
export class Workspace {

    // http client
    private readonly http = inject(HttpClient);

    // --- brand (topbar): leggibile da tutti ---
    private readonly _workspace: WritableSignal<IWorkspace | null> = signal(null);
    readonly workspace = this._workspace.asReadonly();
    // fallback a 'NorthLeap' finché non è caricato o se vuoto
    readonly displayName = computed(() => this._workspace()?.name || 'NorthLeap');

    // --- profilo (tab Azienda): solo admin ---
    private readonly _profile: WritableSignal<IWorkspaceProfile | null> = signal(null);
    readonly profile = this._profile.asReadonly();

    load(): Observable<IWorkspace | null> {
        return this.http.get<IWorkspace>(`${API_BASE_URL}/api/workspace`).pipe(
            tap((ws) => this._workspace.set(ws)),
            catchError(() => of(null))   // non loggato -> 401 -> ignora, resta il fallback
        );
    }

    // vista admin completa, endpoint dedicato /profile (gated user/READ lato backend)
    loadProfile(): Observable<IWorkspaceProfile | null> {
        return this.http.get<IWorkspaceProfile>(`${API_BASE_URL}/api/workspace/profile`).pipe(
            tap((p) => this._profile.set(p)),
            catchError(() => of(null))
        );
    }

    // aggiorna i campi brand (nome/colore/logo/link Cal.com). Aggiorna il signal brand -> la
    // topbar e la pagina Calendario reagiscono.
    updateBrand(payload: { name: string; brandColor: string; logoUrl: string | null; calcomUrl?: string }): Observable<IWorkspace> {
        return this.http.patch<IWorkspace>(`${API_BASE_URL}/api/workspace`, payload).pipe(
            tap((ws) => this._workspace.set(ws))
        );
    }

}
