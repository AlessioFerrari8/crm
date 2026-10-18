import {
  Component,
  ElementRef,
  HostListener,
  WritableSignal,
  computed,
  effect,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import {
  CalendarDays,
  ChartLine,
  FileText,
  LayoutDashboard,
  LucideAngularModule,
  Plus,
  Search,
  Settings
} from 'lucide-angular';

import { resolveObjectIcon } from '../../core/object-icons';
import { SearchResult } from '../../models/record';
import { ObjectTypeService } from '../../services/object-type.service';
import { RecordsService } from '../../services/records.service';
import { I18nService } from '../../services/i18n';

// Voce della palette: sezione ('Record' | 'Vai a' | 'Azioni') + destinazione di navigazione `header` è precalcolato nel
// computed
interface PaletteItem {
  section: string;
  header: boolean;
  label: string;
  sublabel?: string;
  icon: unknown;
  color?: string | null;
  route: string[];
  queryParams?: Record<string, string>;
}

// Command palette globale (Ctrl+K / Cmd+K): ricerca record live + navigazione rapida verso ogni tipo/pagina + azioni ("Nuovo
// <tipo>" apre la lista col drawer di creazione via ?new=1) Montata una volta nella shell autenticata (app.html); overlay
// chiuso da Esc o click fuori.
@Component({
  selector: 'app-command-palette',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './command-palette.html'
})
export class CommandPalette {
  private readonly router = inject(Router);
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly recordsService = inject(RecordsService);
  protected readonly i18n = inject(I18nService);

  protected readonly SearchIcon = Search;

  protected readonly open: WritableSignal<boolean> = signal(false);
  protected readonly query = signal('');
  protected readonly index: WritableSignal<number> = signal(0);


  private readonly input = viewChild<ElementRef<HTMLInputElement>>('paletteInput');

  constructor() {
    // all'apertura: stato pulito e focus sull'input (setTimeout: l'@if deve prima renderizzare)
    effect(() => {
      if (this.open()) {
        this.query.set('');
        this.index.set(0);
        setTimeout(() => this.input()?.nativeElement.focus());
      }
    });
    // a ogni cambio della lista la selezione riparte dalla prima voce
    effect(() => {
      this.items();
      this.index.set(0);
    });
  }

  // ricerca record live (min 2 caratteri, debounced) sulla ricerca globale del backend
  private readonly searchResults = toSignal(
    toObservable(this.query).pipe(
      debounceTime(200),
      map((q) => q.trim()),
      distinctUntilChanged(),
      switchMap((q) =>
        q.length < 2
          ? of<SearchResult[]>([])
          : this.recordsService.search(q).pipe(catchError(() => of<SearchResult[]>([])))
      )
    ),
    { initialValue: [] as SearchResult[] }
  );

  protected readonly items = computed<PaletteItem[]>(() => {
    const q = this.query().trim().toLowerCase();
    const objs = (this.objectTypeService.objects() ?? []).filter((o) => o.enabled);

    const records = this.searchResults().map((r) => ({
      section: this.i18n.t('palette.section.records'),
      label: r.title || this.i18n.t('common.untitled'),
      sublabel: r.objectType?.label,
      icon: FileText,
      route: ['/o', r.objectType?.key ?? '', r.id]
    }));

    const nav = [
      { section: 'Vai a', label: 'Dashboard', icon: LayoutDashboard, route: ['/'] },
      ...objs.map((o) => ({
        section: 'Vai a',
        label: o.pluralLabel,
        icon: resolveObjectIcon(o.icon),
        color: o.color,
        route: ['/o', o.key]
      })),
      { section: 'Vai a', label: 'Calendario', icon: CalendarDays, route: ['/calendar'] },
      { section: 'Vai a', label: 'Grafici', icon: ChartLine, route: ['/charts'] },
      { section: 'Vai a', label: 'Impostazioni', icon: Settings, route: ['/settings'] }
    ].filter((i) => !q || i.label.toLowerCase().includes(q));

    const actions = objs
      .map((o) => ({
        section: 'Azioni',
        label: `Nuovo ${o.label}`,
        icon: Plus,
        route: ['/o', o.key],
        queryParams: { new: '1' }
      }))
      .filter((i) => !q || i.label.toLowerCase().includes(q));

    // lista piatta con l'header calcolato sul cambio di sezione
    let prev = '';
    return [...records, ...nav, ...actions].map((i) => {
      const header = i.section !== prev;
      prev = i.section;
      return { ...i, header } as PaletteItem;
    });
  });

  @HostListener('document:keydown', ['$event'])
  protected onGlobalKeydown(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.open.update((v) => !v);
      // se non ancora caricati (palette aperta prima della sidebar), carica gli object type
      if (this.open() && !this.objectTypeService.objects()) {
        this.objectTypeService.load().subscribe();
      }
    } else if (e.key === 'Escape' && this.open()) {
      this.close();
    }
  }

  protected onInputKeydown(e: KeyboardEvent): void {
    const max = this.items().length - 1;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      this.index.set(Math.min(this.index() + 1, max));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      this.index.set(Math.max(this.index() - 1, 0));
    } else if (e.key === 'Enter') {
      const it = this.items()[this.index()];
      if (it) this.run(it);
    }
  }

  protected run(it: PaletteItem): void {
    this.router.navigate(it.route, it.queryParams ? { queryParams: it.queryParams } : undefined);
    this.close();
  }

  protected close(): void {
    this.open.set(false);
  }
}
