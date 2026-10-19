import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

// Routing piatto: la shell (sidebar/topbar) vive nel root app.html in base allo stato di auth,
// quindi qui non c'è un componente layout: le rotte protette sono figlie di un parent pathless
// con l'authGuard e rendono nel <router-outlet> del root
export const routes: Routes = [
  {
    path: 'login',
    title: 'route.login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login)
  },
  {
    path: 'onboarding',
    title: 'route.onboarding',
    loadComponent: () => import('./pages/onboarding/onboarding').then((m) => m.Onboarding)
  },
  {
    path: 'register',
    title: 'route.register',
    loadComponent: () => import('./pages/register/register').then((m) => m.Register)
  },
  {
    path: 'verify',
    title: 'route.verify',
    loadComponent: () => import('./pages/verify/verify').then((m) => m.Verify)
  },
  {
    // pubblica: l'invitato arriva qui dal link /invite?token=... copiato dall'admin
    path: 'invite',
    title: 'route.invite',
    loadComponent: () => import('./pages/accept-invite/accept-invite').then((m) => m.AcceptInvite)
  },
  {
    // pubblica: web form di lead capture, condivisibile/incorporabile (tipi con publicForm attivo)
    path: 'f/:objectKey',
    title: 'route.publicForm',
    loadComponent: () => import('./pages/public-form/public-form').then((m) => m.PublicForm)
  },
  {
    path: 'nps/:token',
    title: 'route.nps',
    loadComponent: () => import('./pages/nps-public/nps-public').then((m) => m.NpsPublic)
  },
  {
    path: 'q/:id',
    title: 'route.quote',
    loadComponent: () => import('./pages/quote-accept/quote-accept').then((m) => m.QuoteAccept)
  },
  {
    // portale clienti: fuori dall'authGuard interno, ha il suo login
    path: 'portal',
    children: [
      { path: '', title: 'route.portal', loadComponent: () => import('./pages/portal/portal-home/portal-home').then((m) => m.PortalHome )},
      { path: 'login', title: 'route.portal', loadComponent: () => import('./pages/portal/portal-login/portal-login').then((m) => m.PortalLogin) },
      { path: 'invite', title: 'route.portal', loadComponent: () => import('./pages/portal/portal-login/portal-login').then((m) => m.PortalLogin) },
      { path: 'tickets/:id', title: 'route.portal', loadComponent: () => import('./pages/portal/portal-ticket/portal-ticket').then((m) => m.PortalTicket) }
    ]
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'route.dashboard',
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
        title: 'route.charts',
        loadComponent: () => import('./pages/charts/charts').then((m) => m.Charts)
      },
      {
        path: 'calendar',
        title: 'route.calendar',
        loadComponent: () => import('./pages/calendar/calendar').then((m) => m.Calendar)
      },
      {
        path: 'search',
        title: 'route.search',
        loadComponent: () => import('./pages/search/search').then((m) => m.Search)
      },
      {
        path: 'e-invoices',
        title: 'route.einvoices',
        loadComponent: () => import('./pages/e-invoices/e-invoices').then((m) => m.EInvoices)
      },
      {
        path: 'surveys',
        title: 'route.npsSurveys',
        loadComponent: () => import('./pages/nps/nps').then((m) => m.Nps)
      },
      {
        path: 'campaigns',
        title: 'route.campaigns',
        loadComponent: () => import('./pages/campaigns/campaigns').then((m) => m.Campaigns)
      },
      {
        path: 'settings',
        title: 'route.settings',
        loadComponent: () => import('./pages/settings/settings').then((m) => m.Settings)
      },
      {
        path: 'preferences',
        title: 'route.preferences',
        loadComponent: () => import('./pages/preferences/preferences').then((m) => m.Preferences)
      },
      {
        path: 'dashboards',
        title: 'route.dashboard',
        loadComponent: () => import('./pages/dashboard-tabs/dashboard-tabs').then((m) => m.DashboardTabs)
      },
      {
        path: 'reports',
        title: 'route.report',
        loadComponent: () => import('./pages/reports/reports').then((m) => m.Reports)
      },
      {
        path: 'duplicates',
        title: 'route.duplicates',
        loadComponent: () => import('./pages/duplicates/duplicates').then((m) => m.Duplicates)
      },
      {
        path: 'tags/:tagId',
        title: 'route.tags',
        loadComponent: () => import('./pages/tag-records/tag-records').then((m) => m.TagRecords)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
