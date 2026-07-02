import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
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
  tap
} from 'rxjs';

import { Drawer } from '../../components/drawer/drawer';
import { DynamicForm } from '../../components/dynamic-form/dynamic-form';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { FieldCell, fieldCell } from '../../core/field-format';
import { FieldDef, ObjectType } from '../../models/object-type';
import { FileObject } from '../../models/file-object';
import { RecordItem } from '../../models/record';
import { FileService } from '../../services/file.service';
import { ObjectTypeService } from '../../services/object-type.service';
import { RecordsService } from '../../services/records.service';

@Component({
  selector: 'app-record-detail',
  standalone: true,
  imports: [RouterLink, Drawer, DynamicForm, UiButton, UiSpinner],
  templateUrl: './record-detail.html'
})
export class RecordDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly recordsService = inject(RecordsService);
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly fileService = inject(FileService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly objectKey = this.route.snapshot.paramMap.get('objectKey') ?? '';
  protected readonly recordId = this.route.snapshot.paramMap.get('id') ?? '';

  protected readonly loading = signal(true);
  protected readonly editOpen = signal(false);
  protected readonly submitting = signal(false);
  protected readonly uploading = signal(false);

  // l'ObjectType (per i campi) si carica una volta sola: stream -> signal
  protected readonly object = toSignal(this.objectTypeService.getByKey(this.objectKey), {
    initialValue: null
  });

  // trigger di ricarica del record dopo una modifica
  private readonly reload$ = new Subject<void>();

  private readonly detail = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() =>
        this.recordsService.findOne(this.objectKey, this.recordId).pipe(catchError(() => of(null)))
      ),
      tap(() => this.loading.set(false))
    ),
    { initialValue: null }
  );

  protected readonly record = computed(() => this.detail()?.record ?? null);
  protected readonly outgoing = computed(() => this.detail()?.outgoing ?? []);
  protected readonly incoming = computed(() => this.detail()?.incoming ?? []);

  // allegati del record: stream ricaricabile dopo ogni upload
  private readonly filesReload$ = new Subject<void>();
  protected readonly files = toSignal(
    this.filesReload$.pipe(
      startWith(undefined),
      switchMap(() => this.fileService.list(this.recordId).pipe(catchError(() => of<FileObject[]>([]))))
    ),
    { initialValue: [] as FileObject[] }
  );

  protected readonly visibleFields = computed<FieldDef[]>(() =>
    (this.object()?.fields ?? []).filter((f) => !f.hidden).sort((a, b) => a.sortOrder - b.sortOrder)
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
          this.reload$.next();
        },
        error: () => this.submitting.set(false)
      });
  }

  // ── collegamenti (aggiungi/rimuovi) ─────────────────────────────────────────
  protected readonly linkFormOpen = signal(false);
  protected readonly linkTargetKey = signal('');
  protected readonly linkRelationKey = signal('related');

  // tutti i tipi, per la select del target (inclusi i disabilitati: un collegamento resta lecito)
  protected readonly allObjects = toSignal(
    this.objectTypeService.load().pipe(catchError(() => of<ObjectType[]>([]))),
    { initialValue: [] as ObjectType[] }
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
          catchError(() => of<RecordItem[]>([]))
        );
      })
    ),
    { initialValue: [] as RecordItem[] }
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
    if (!confirm('Rimuovere il collegamento?')) return;
    this.recordsService
      .removeLink(this.recordId, linkId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
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
        }
      });
  }

  protected remove(): void {
    if (!confirm('Eliminare questo record?')) return;
    this.recordsService
      .remove(this.objectKey, this.recordId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.router.navigate(['/o', this.objectKey]));
  }
}
