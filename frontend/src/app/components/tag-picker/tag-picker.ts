import { catchError, filter, of } from 'rxjs';
import { Component, computed, DestroyRef, inject, input, signal, effect } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Tag } from '../../models/tag';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TagService } from '../../services/tag.service';


// tag di un record, chip colorate + un menu per attaccarne altri
// i tag disponibili arrivano dal signal del service (1 per tutta l'app), quelli del record
// da una chiamata per record

@Component({
  selector: 'app-tag-picker',
  imports: [FormsModule],
  templateUrl: './tag-picker.html',
  styles: ``,
})
export class TagPicker {
  private readonly tagService = inject(TagService);
  private readonly destroyRef = inject(DestroyRef);

  readonly recordId = input.required<string>();

  protected readonly mine = signal<Tag[]>([]);
  protected readonly newName = signal('');
  protected readonly open = signal(false);

  protected readonly available = computed(() => {
    const attached = new Set(this.mine().map((t) => t.id));
    return this.tagService.all().filter((t) => !attached.has(t.id))
  })

  constructor() {
    // l'elenco globale si carica una volta sola; se è già pieno non si richiama
    if(this.tagService.all().length === 0) {
      this.tagService.load().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef)).subscribe();
    }
    effect(() => {
      const id = this.recordId();
      if (id) this.reload(id);
    })
  }

  private reload(recordId: string): void {
    this.tagService.ofRecord(recordId)
      .pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe((tags) => this.mine.set(tags));
  }

  protected attach(tag: Tag): void {
    this.mine.update((tags) => tags.filter((t) => t.id !== tag.id));
    this.tagService.detach(this.recordId(), tag.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => this.reload(this.recordId()) })
  }

  protected detach(tag: Tag): void {
    this.mine.update((tags) => tags.filter((t) => t.id !== tag.id));
    this.tagService.detach(this.recordId(), tag.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => this.reload(this.recordId()) })
  }

  // crea e attacca in un colpo: è il 90% dei casi d'uso (etichetta questo record con una cosa nuova), e
  // separare i due passaggi in due schermate diverse sarebbe solo fedeltà al modello dati
  protected createAndAttach(): void {
    const name = this.newName().trim();
    if (!name) return;
    this.tagService.create({ name, isPrivate: false })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((tag) => {
        this.newName.set('');
        this.attach(tag);
      });
  }


}
