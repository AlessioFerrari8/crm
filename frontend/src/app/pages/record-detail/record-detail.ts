import {
  Component,
  DOCUMENT,
  DestroyRef,
  WritableSignal,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { Drawer } from '../../components/drawer/drawer';
import { DynamicForm } from '../../components/dynamic-form/dynamic-form';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { FieldCell, fieldCell } from '../../core/field-format';
import { FieldDef, ObjectType } from '../../models/object-type';
import { FileObject } from '../../models/file-object';
import { RecordItem, RecordNote } from '../../models/record';
import { AdminService } from '../../services/admin.service';
import { AuthService } from '../../services/auth.service';
import { FileService } from '../../services/file.service';
import { ObjectTypeService } from '../../services/object-type.service';
import { RecordsService } from '../../services/records.service';
import { ToastService } from '../../services/toast.service';
import { ConfirmService } from '../../services/confirm.service';
import { DocumentLines } from '../../components/document-lines/document-lines';
import { TagPicker } from '../../components/tag-picker/tag-picker';
import { DOCUMENT_KEYS } from '../../models/document-line';
import { DocumentLineService } from '../../services/document-line.service';
import { PdfService } from '../../services/pdf.service';
import { EInvoiceService } from '../../services/e-invoice.service';
import { RecordTime } from '../../components/record-time/record-time';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../core/api-config';
import { RecordHistory } from '../../components/record-history/record-history';

@Component({
  selector: 'app-record-detail',
  standalone: true,
  imports: [RouterLink, Drawer, DynamicForm, UiButton, UiSpinner, DocumentLines, TagPicker, RecordTime, RecordHistory],
  templateUrl: './record-detail.html',
})
export class RecordDetail {
  // vari servizi
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly recordsService = inject(RecordsService);
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly fileService = inject(FileService);
  private readonly admin = inject(AdminService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  private readonly title = inject(Title);
  private readonly confirmService = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly lineService = inject(DocumentLineService);
  private readonly pdf = inject(PdfService);
  private readonly eInvoices = inject(EInvoiceService);
  private readonly http = inject(HttpClient);

  // NON readonly: navigando verso un altro record della stessa route (duplica, link tra record)
  // Angular riusa il componente, la subscription a paramMap nel costruttore aggiorna questi
  // campi e rilancia gli stream (che li leggono dentro switchMap, quindi a valore fresco).
  protected objectKey = this.route.snapshot.paramMap.get('objectKey') ?? '';
  protected recordId = this.route.snapshot.paramMap.get('id') ?? '';

  // le righe si mostrano solo sui 4 moduli documento: sugli altri la tabella non avrebbe senso e l'endpoint
  // risponderebbe comunque vuoto
  protected readonly hasLines = computed(() => DOCUMENT_KEYS.includes(this.objectKey));
  protected readonly isQuote = computed(() => this.objectKey === 'quote');

  protected readonly printable = computed(() => ['quote', 'sales_order', 'delivery_note', 'invoice']
    .includes(this.record()?.objectType?.key ?? ''));
  protected readonly printing: WritableSignal<boolean> = signal(false);

  protected readonly generating: WritableSignal<boolean> = signal(false);
  // l'elenco di cosa manca, per intero: il taost lo taglierebbe
  protected readonly eInvoiceError: WritableSignal<string | null> = signal(null);

  protected numberOf(key: string): number {
    return Number(this.record()?.data?.[key] ?? 0) || 0;
  }

  protected readonly converting: WritableSignal<boolean> = signal(false);

  // preventivo -> ordine, crea un ordine nuovo con le stesse righe e ci porta sopra
  // il preventivo resta modificabile
  protected convertToOrder(): void {
    if (this.converting()) return;
    this.converting.set(true);
    this.lineService.convertQuoteToOrder(this.recordId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.converting.set(false);
          this.toasts.success('Ordine creato dal preventivo');
          this.router.navigate(['/o', 'sales_order', order.id]);
        },
        error: () => this.converting.set(false)
      });
  }

  // revisione, il padre passa a "revisionato"
  protected revise(): void {
    this.lineService.reviseQuote(this.recordId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rev) => this.router.navigate(['/o', 'quote', rev.id]));
  }

  protected readonly loading: WritableSignal<boolean> = signal(true);
  protected readonly editOpen: WritableSignal<boolean> = signal(false);
  protected readonly submitting: WritableSignal<boolean> = signal(false);
  protected readonly uploading: WritableSignal<boolean> = signal(false);

  // l'ObjectType (per i campi), rifetchato a ogni cambio di :objectKey (navigazioni tra record)
  protected readonly object = toSignal(
    this.route.paramMap.pipe(
      map((p) => p.get('objectKey') ?? ''),
      distinctUntilChanged(),
      switchMap((key) => this.objectTypeService.getByKey(key).pipe(catchError(() => of(null)))),
    ),
    { initialValue: null },
  );

  // trigger di ricarica del record dopo una modifica
  private readonly reload$ = new Subject<void>();

  // le righe documento scrivono i totali dentro Record.data lato server: dopo un salvataggio
  // la testata va riletta, altrimenti resta quella caricata prima delle righe
  protected reloadRecord(): void {
    this.reload$.next();
  }

  constructor() {
    // titolo della tab del browser: il titolo del record corrente
    effect(() => {
      const t = this.record()?.title;
      if (t) this.title.setTitle(`${t} · NorthLeap`);
    });
    // stessa route, altro record: aggiorna i campi e rilancia gli stream dipendenti
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((p) => {
      const key = p.get('objectKey') ?? '';
      const id = p.get('id') ?? '';
      if (key === this.objectKey && id === this.recordId) return;
      this.objectKey = key;
      this.recordId = id;
      this.editOpen.set(false);
      this.linkFormOpen.set(false);
      this.noteBody.set('');
      this.reload$.next();
      this.filesReload$.next();
      this.notesReload$.next();
    });
  }

  private readonly detail = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() =>
        this.recordsService.findOne(this.objectKey, this.recordId).pipe(catchError(() => of(null))),
      ),
      tap(() => this.loading.set(false)),
    ),
    { initialValue: null },
  );

  protected readonly record = computed(() => this.detail()?.record ?? null);
  protected readonly outgoing = computed(() => this.detail()?.outgoing ?? []);
  protected readonly incoming = computed(() => this.detail()?.incoming ?? []);

  // allegati del record: stream ricaricabile dopo ogni upload
  private readonly filesReload$ = new Subject<void>();
  protected readonly files = toSignal(
    this.filesReload$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.fileService.list(this.recordId).pipe(catchError(() => of<FileObject[]>([]))),
      ),
    ),
    { initialValue: [] as FileObject[] },
  );

  protected readonly visibleFields = computed<FieldDef[]>(() =>
    (this.object()?.fields ?? [])
      .filter((f) => !f.hidden)
      .sort((a, b) => a.sortOrder - b.sortOrder),
  );

  // cella type-aware (badge colorati, link, testo formattato) - formatter condiviso con la record-list
  protected cell(field: FieldDef): FieldCell {
    const rec = this.record();
    if (!rec) return { kind: 'text', text: '—' };
    const raw = field.key === 'status' ? rec.status : rec.data[field.key];
    return fieldCell(field, raw);
  }

  protected saveEdit(data: Record<string, unknown>): void {
    this.submitting.set(true);
    this.recordsService
      .update(this.objectKey, this.recordId, { data })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.editOpen.set(false);
          this.toasts.success('Modifiche salvate');
          this.reload$.next();
        },
        error: () => this.submitting.set(false),
      });
  }

  // note (timeline collaborativa) 
  private readonly notesReload$ = new Subject<void>();
  protected readonly noteBody = signal('');
  protected readonly noteForCustomer: WritableSignal<boolean> = signal(false);
  protected readonly noteSaving: WritableSignal<boolean> = signal(false);

  protected readonly notes = toSignal(
    this.notesReload$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.recordsService.listNotes(this.recordId).pipe(catchError(() => of<RecordNote[]>([]))),
      ),
    ),
    { initialValue: [] as RecordNote[] },
  );

  protected addNote(): void {
    const body = this.noteBody().trim();
    if (!body || this.noteSaving()) return;
    this.noteSaving.set(true);
    this.recordsService
      .addNote(this.recordId, body, this.noteForCustomer())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.noteSaving.set(false);
          this.noteBody.set('');
          this.noteForCustomer.set(false);
          this.notesReload$.next();
        },
        error: () => this.noteSaving.set(false),
      });
  }

  // solo l'autore vede il cestino (il backend rifiuta comunque le note altrui)
  protected isMyNote(n: RecordNote): boolean {
    return n.authorId != null && n.authorId === this.auth.user()?.userId;
  }

  protected deleteNote(n: RecordNote): void {
    this.confirmService.ask('Eliminare questa nota?').then((ok) => {
      if (!ok) return;
      this.recordsService
        .removeNote(this.recordId, n.id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.notesReload$.next());
    });
  }

  protected noteDate(n: RecordNote): string {
    return new Date(n.createdAt).toLocaleString('it-IT');
  }


  // ── duplica record ──────────────────────────────────────────────────────────
  protected readonly duplicating = signal(false);

  protected duplicate(): void {
    const rec = this.record();
    if (!rec || this.duplicating()) return;
    this.duplicating.set(true);
    this.recordsService
      .create(this.objectKey, { data: { ...rec.data } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.duplicating.set(false);
          this.toasts.success('Record duplicato');
          this.router.navigate(['/o', this.objectKey, created.id]);
        },
        error: () => this.duplicating.set(false),
      });
  }

  // ── proprietario del record ─────────────────────────────────────────────────
  // lista utenti per la riassegnazione: richiede il permesso RBAC "user" READ; senza, la select
  // non compare (il backend comunque ignora ownerId arbitrari sotto scope OWN)
  protected readonly users = toSignal(this.admin.listUsers().pipe(catchError(() => of(null))), {
    initialValue: null,
  });

  protected setOwner(userId: string): void {
    if (!userId) return;
    this.recordsService
      .update(this.objectKey, this.recordId, { data: {}, ownerId: userId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  // ── collegamenti (aggiungi/rimuovi) ─────────────────────────────────────────
  protected readonly linkFormOpen = signal(false);
  protected readonly linkTargetKey = signal('');
  protected readonly linkRelationKey = signal('related');

  // tutti i tipi, per la select del target (inclusi i disabilitati: un collegamento resta lecito)
  protected readonly allObjects = toSignal(
    this.objectTypeService.load().pipe(catchError(() => of<ObjectType[]>([]))),
    { initialValue: [] as ObjectType[] },
  );

  // ricerca del record da collegare: per titolo, dentro il tipo scelto, con debounce
  private readonly linkQuery$ = new Subject<string>();
  protected readonly linkResults = toSignal(
    this.linkQuery$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((q) => {
        const key = this.linkTargetKey();
        if (!key || q.trim().length < 1) return of<RecordItem[]>([]);
        return this.recordsService.query(key, { q: q.trim(), pageSize: 8 }).pipe(
          map((r) => r.items),
          catchError(() => of<RecordItem[]>([])),
        );
      }),
    ),
    { initialValue: [] as RecordItem[] },
  );

  protected onLinkSearch(q: string): void {
    this.linkQuery$.next(q);
  }

  protected onTargetType(key: string): void {
    this.linkTargetKey.set(key);
    this.linkQuery$.next(''); // svuota i risultati del tipo precedente
  }

  protected addLink(target: RecordItem): void {
    this.recordsService
      .addLink(this.recordId, target.id, this.linkRelationKey().trim() || 'related')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.linkFormOpen.set(false);
        this.reload$.next();
      });
  }

  protected removeLink(linkId: string): void {
    this.confirmService.ask('Rimuovere il collegamento?').then((ok) => {
      if (!ok) return;
      this.recordsService
        .removeLink(this.recordId, linkId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.reload$.next());
    });
  }

  // rotta verso l'altro record del collegamento (se il backend ha annidato objectType)
  protected routeFor(rec: RecordItem): string[] | null {
    return rec.objectType?.key ? ['/o', rec.objectType.key, rec.id] : null;
  }

  protected downloadUrl(file: FileObject): string {
    return this.fileService.downloadUrl(file.id);
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.fileService
      .upload(file, this.recordId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.uploading.set(false);
          input.value = ''; // permette di ricaricare lo stesso file
          this.filesReload$.next();
        },
        error: () => {
          this.uploading.set(false);
          input.value = '';
        },
      });
  }

  protected remove(): void {
    this.confirmService.ask('Eliminare questo record?').then((ok) => {
      if (!ok) return;
      this.recordsService
        .remove(this.objectKey, this.recordId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          this.toasts.success('Record eliminato');
          this.router.navigate(['/o', this.objectKey]);
        });
    });
  }

  // il link firmato da mandare al cliente. Rigenerarlo revoca il precedente
  protected copyAcceptLink(): void {
    this.http
      .post<{ url: string }>(`${API_BASE_URL}/api/quotes/${this.recordId}/acceptLink`, {})
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((r) => {
        void navigator.clipboard.writeText(r.url);
        this.toasts.success('Link copiato: incollalo nelle mail al cliente');
      });
  }

  protected generateEInvoice(): void {
    this.generating.set(true);
    this.eInvoiceError.set(null);
    this.eInvoices.generate(this.recordId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.generating.set(false);
        this.router.navigate(['/e-invoices']);
      },
      error: (err) => {
        this.generating.set(false);
        this.eInvoiceError.set(err?.error?.message ?? 'Generazione non riuscita');
      }
    });
  }

  protected print(): void {
    const id = this.record()?.id;
    if (!id) return;
    this.printing.set(true);
    this.pdf.print(id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${this.record()?.data?.['number'] ?? 'documento'}.pdf`;
        a.click();
        // senza revoke ogni stampa lascia un blob in memoria finché non ricarichi la pagina
        // Il setTimeout serve perché revocare nell'istante il click annulla il download su Firefox
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.printing.set(false);
      },
      error: () => this.printing.set(false),
    });
  }
}

