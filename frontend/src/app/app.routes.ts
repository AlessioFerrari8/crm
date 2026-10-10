import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

// Routing piatto: la shell (sidebar/topbar) vive nel root app.html in base allo stato di auth,
// quindi qui non c'è un componente layout: le rotte protette sono figlie di un parent pathless
// con l'authGuard e rendono nel <router-outlet> del root
export const routes: Routes = [
  {
    path: 'login',
    title: 'Accedi · NorthLeap',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login)
  },
  {
    path: 'onboarding',
    title: 'Benvenuto · NorthLeap',
    loadComponent: () => import('./pages/onboarding/onboarding').then((m) => m.Onboarding)
  },
  {
    // pubblica: l'invitato arriva qui dal link /invite?token=... copiato dall'admin
    path: 'invite',
    title: 'Invito · NorthLeap',
    loadComponent: () => import('./pages/accept-invite/accept-invite').then((m) => m.AcceptInvite)
  },
  {
    // pubblica: web form di lead capture, condivisibile/incorporabile (tipi con publicForm attivo)
    path: 'f/:objectKey',
    title: 'Modulo · NorthLeap',
    loadComponent: () => import('./pages/public-form/public-form').then((m) => m.PublicForm)
  },
  {
    path: 'nps/:token',
    title: 'Un minuto per noi · NorthLeap',
    loadComponent: () => import('./pages/nps-public/nps-public').then((m) => m.NpsPublic)
  },
  {
    path: 'q/:id',
    title: 'Preventivo · NorthLeap',
    loadComponent: () => import('./pages/quote-accept/quote-accept').then((m) => m.QuoteAccept)
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'Dashboard · NorthLeap',
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
        title: 'Grafici · NorthLeap',
        loadComponent: () => import('./pages/charts/charts').then((m) => m.Charts)
      },
      {
        path: 'calendar',
        title: 'Calendario · NorthLeap',
        loadComponent: () => import('./pages/calendar/calendar').then((m) => m.Calendar)
      },
      {
        path: 'search',
        title: 'Ricerca · NorthLeap',
        loadComponent: () => import('./pages/search/search').then((m) => m.Search)
      },
      {
        path: 'e-invoices',
        title: 'Fatture elettroniche · NorthLeap',
        loadComponent: () => import('./pages/e-invoices/e-invoices').then((m) => m.EInvoices)
      },
      {
        path: 'surveys',
        title: 'Sondaggi NPS · NorthLeap',
        loadComponent: () => import('./pages/nps/nps').then((m) => m.Nps)
      },
      {
        path: 'campaigns',
        title: 'Campagne · NorthLeap',
        loadComponent: () => import('./pages/campaigns/campaigns').then((m) => m.Campaigns)
      },
      {
        path: 'settings',
        title: 'Impostazioni · NorthLeap',
        loadComponent: () => import('./pages/settings/settings').then((m) => m.Settings)
      },
      {
        path: 'preferences',
        title: 'Le mie preferenze · NorthLeap',
        loadComponent: () => import('./pages/preferences/preferences').then((m) => m.Preferences)
      },
      {
        path: 'dashboards',
        title: 'Dashboard · NorthLeap',
        loadComponent: () => import('./pages/dashboard-tabs/dashboard-tabs').then((m) => m.DashboardTabs)
      },
      {
        path: 'reports',
        title: 'Report · NorthLeap',
        loadComponent: () => import('./pages/reports/reports').then((m) => m.Reports)
      },
      {
        path: 'duplicates',
        title: 'Duplicati · NorthLeap',
        loadComponent: () => import('./pages/duplicates/duplicates').then((m) => m.Duplicates)
      },
      {
        path: 'tags/:tagId',
        title: 'Tag · NorthLeap',
        loadComponent: () => import('./pages/tag-records/tag-records').then((m) => m.TagRecords)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
