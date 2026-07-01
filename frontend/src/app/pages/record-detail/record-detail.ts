import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { Drawer } from '../../components/drawer/drawer';
import { DynamicForm } from '../../components/dynamic-form/dynamic-form';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { FieldCell, fieldCell } from '../../core/field-format';
import { FieldDef } from '../../models/object-type';
import { FileObject } from '../../models/file-object';
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
