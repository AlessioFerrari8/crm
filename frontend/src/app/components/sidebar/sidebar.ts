import { Component, DestroyRef, NgZone, Renderer2, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { CalendarDays, ChartColumn, LucideAngularModule, Settings, Copy, FileText, LayoutDashboard, FileCode, Megaphone } from 'lucide-angular';

import { resolveObjectIcon } from '../../core/object-icons';
import { ObjectType } from '../../models/object-type';
import { ObjectTypeService } from '../../services/object-type.service';
import { I18nService } from '../../services/i18n';

const MIN_WIDTH = 64;        // rail con sole icone
const MAX_WIDTH = 200;
const EXPANDED_WIDTH = 184;  // larghezza "comoda" con le etichette, usata da toggle e snap
const LABEL_THRESHOLD = 150; // oltre questa larghezza ricompaiono le etichette
const KEY_STEP = 16;         // di quanto si muove la larghezza con le frecce
const STORAGE_KEY = 'sidebar-width';

// Sidebar ridimensionabile: di default un rail stretto con sole icone (più grandi); trascinando
// il bordo destro la si allarga, e oltre LABEL_THRESHOLD ricompaiono le etichette. La larghezza
// e' persistita in localStorage. Il host e' direttamente l'elemento .drawer-side di daisyUI.
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  templateUrl: './sidebar.html',
  host: { class: 'drawer-side z-30' }
})
export class Sidebar {

  // servizi
  private readonly objectTypeService = inject(ObjectTypeService);
  protected readonly i18n = inject(I18nService);

  // gestisco il ciclo di vita
  private readonly destroyRef = inject(DestroyRef);

  // navigazione + zona/renderer per il drag fuori da Angular
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly renderer = inject(Renderer2);

  // mi prendo la lista di object dal service omonimo
  protected readonly objects = this.objectTypeService.objects;
  // icona settings + grafici
  protected readonly SettingsIcon = Settings;
  protected readonly ChartsIcon = ChartColumn;
  protected readonly CalendarIcon = CalendarDays;
  protected readonly DashboardsIcon = LayoutDashboard;
  protected readonly ReportsIcon = FileText;
  protected readonly DuplicatesIcon = Copy;
  protected readonly EInvoicesIcon = FileCode;
  protected readonly CampaingsIcon = Megaphone;

  // estremi esposti al template (per gli attributi aria della maniglia)
  protected readonly MIN_WIDTH = MIN_WIDTH;
  protected readonly MAX_WIDTH = MAX_WIDTH;

  // grandezza, cambiata direttamente tramite il signal
  protected readonly width: WritableSignal<number> = signal(readInitialWidth());
  protected readonly dragging: WritableSignal<boolean> = signal(false);
  protected readonly showLabels = computed(() => this.width() >= LABEL_THRESHOLD);

  // stato interno del drag
  private startX = 0;
  private startWidth = 0;
  private pendingWidth = 0;
  private frame = 0;
  private asideEl?: HTMLElement; // il pannello ridimensionabile, scritto direttamente durante il drag
  private stopMove?: () => void;
  private stopUp?: () => void;
  private stopCancel?: () => void;

  protected iconFor(obj: ObjectType) {
    return resolveObjectIcon(obj.icon);
  }

  // appena nasce, carico tutti gli oggetti, gestendo la memoria grazie al destroyRef
  constructor() {
    this.objectTypeService.load().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    // se il componente muore durante un drag, pulisco listener e stili sul body
    this.destroyRef.onDestroy(() => this.teardown());
  }

  // inizio del drag. Uso i Pointer Events + setPointerCapture: cosi' il drag funziona anche con
  // touch/penna e tutti i pointermove/up vengono "catturati" dalla maniglia anche quando il
  // puntatore esce da essa o dalla finestra (niente listener orfani su document). Registro i
  // listener FUORI dalla zona Angular, cosi' i movimenti non scatenano change detection a ogni
  // frame, e lavoro a delta da startX (niente getBoundingClientRect per frame -> niente reflow).
  protected startDrag(event: PointerEvent): void {
    event.preventDefault();
    const handle = event.currentTarget as HTMLElement;
    handle.setPointerCapture(event.pointerId);
    this.asideEl = handle.closest('aside') ?? undefined;

    this.startX = event.clientX;
    this.startWidth = this.width();
    this.pendingWidth = this.startWidth;
    this.dragging.set(true);

    this.renderer.setStyle(document.body, 'user-select', 'none');
    this.renderer.setStyle(document.body, 'cursor', 'col-resize');

    this.zone.runOutsideAngular(() => {
      this.stopMove = this.renderer.listen(handle, 'pointermove', (e: PointerEvent) => this.onMove(e));
      this.stopUp = this.renderer.listen(handle, 'pointerup', () => this.endDrag());
      this.stopCancel = this.renderer.listen(handle, 'pointercancel', () => this.endDrag());
    });
  }

  private onMove(event: PointerEvent): void {
    const delta = event.clientX - this.startX;
    this.pendingWidth = clamp(this.startWidth + delta);
    // throttle: al massimo un aggiornamento per frame
    if (this.frame) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      // scrivo la larghezza DIRETTAMENTE sul DOM, fuori da Angular: nessuna change detection
      // per-frame, nessun ricalcolo del template -> il pannello insegue il cursore senza ritardo,
      // come l'explorer di VS Code. La transizione e' gia' 'none' (vedi [style.transition] in HTML
      // mentre dragging() e' true), quindi la larghezza non viene animata e segue 1:1.
      this.renderer.setStyle(this.asideEl, 'width', this.pendingWidth + 'px');
      // le etichette compaiono/scompaiono solo quando si ATTRAVERSA la soglia (raro), non a ogni
      // pixel: solo in quel momento rientro in Angular per aggiornare il signal e ridisegnare.
      if ((this.pendingWidth >= LABEL_THRESHOLD) !== this.showLabels()) {
        this.zone.run(() => this.width.set(this.pendingWidth));
      }
    });
  }

  private endDrag(): void {
    this.zone.run(() => {
      // dragging() torna false -> [style.transition] riattiva la transizione CSS, cosi' l'eventuale
      // collasso al rail viene animato dolcemente invece di scattare. La larghezza durante il drag
      // l'ha scritta onMove direttamente sul DOM; qui sincronizzo il signal (fonte di verita') con
      // il valore finale, ed e' lui a guidare di nuovo [style.width.px].
      this.dragging.set(false);
      this.persist(snap(this.pendingWidth));
    });
    this.teardown();
  }

  // doppio click sulla maniglia: toggle tra rail e larghezza espansa (animato, perche' fuori dal
  // drag la classe transition-[width] e' attiva)
  protected toggle(): void {
    this.persist(this.width() > LABEL_THRESHOLD ? MIN_WIDTH : EXPANDED_WIDTH);
  }

  // accessibilita': la maniglia e' focusabile, le frecce la ridimensionano, Home/End vanno agli estremi
  protected onKeydown(event: KeyboardEvent): void {
    let next: number | null = null;
    switch (event.key) {
      case 'ArrowLeft': next = this.width() - KEY_STEP; break;
      case 'ArrowRight': next = this.width() + KEY_STEP; break;
      case 'Home': next = MIN_WIDTH; break;
      case 'End': next = MAX_WIDTH; break;
      default: return;
    }
    event.preventDefault();
    this.persist(clamp(next));
  }

  private persist(value: number): void {
    this.width.set(value);
    localStorage.setItem(STORAGE_KEY, String(value));
  }

  private teardown(): void {
    if (this.frame) {
      cancelAnimationFrame(this.frame);
      this.frame = 0;
    }
    this.stopMove?.();
    this.stopUp?.();
    this.stopCancel?.();
    this.stopMove = undefined;
    this.stopUp = undefined;
    this.stopCancel = undefined;
    this.asideEl = undefined;
    this.renderer.removeStyle(document.body, 'user-select');
    this.renderer.removeStyle(document.body, 'cursor');
  }

  // metodo che torna alla dashboard
  toDashboard(): void {
    this.router.navigateByUrl('');
  }
}

function clamp(value: number): number {
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, value));
}

// Niente piu' "magnetismo" a meta' corsa (era cio' che dava lo scatto al rilascio): se rilasci
// nella zona morta sotto la soglia delle etichette, la sidebar collassa al rail; altrimenti resta
// esattamente dove l'hai lasciata, come l'explorer di VS Code.
function snap(value: number): number {
  return value < LABEL_THRESHOLD ? MIN_WIDTH : value;
}

function readInitialWidth(): number {
  const stored = Number(localStorage.getItem(STORAGE_KEY));
  if (stored >= MIN_WIDTH && stored <= MAX_WIDTH) return stored;
  return EXPANDED_WIDTH;
}
