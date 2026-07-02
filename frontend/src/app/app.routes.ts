import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

// Routing piatto. La shell (sidebar/topbar) vive nel root app.html in base allo stato di auth,
// quindi qui non c'e' un componente layout: le rotte protette sono figlie di un parent pathless
// con l'authGuard e rendono nel <router-outlet> del root.
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login)
  },
  {
    path: 'onboarding',
    loadComponent: () => import('./pages/onboarding/onboarding').then((m) => m.Onboarding)
  },
  {
    // pubblica: l'invitato arriva qui dal link /invite?token=... copiato dall'admin
    path: 'invite',
    loadComponent: () => import('./pages/accept-invite/accept-invite').then((m) => m.AcceptInvite)
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard)
      },
      {
        path: 'o/:objectKey',
        loadComponent: () => import('./pages/record-list/record-list').then((m) => m.RecordList)
      },
      {
        path: 'o/:objectKey/:id',
        loadComponent: () => import('./pages/record-detail/record-detail').then((m) => m.RecordDetail)
      },
      {
        path: 'charts',
        loadComponent: () => import('./pages/charts/charts').then((m) => m.Charts)
      },
      {
        path: 'search',
        loadComponent: () => import('./pages/search/search').then((m) => m.Search)
      },
      {
        path: 'settings',
        loadComponent: () => import('./pages/settings/settings').then((m) => m.Settings)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
