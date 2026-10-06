import { Component, computed, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UiButton } from '../../components/ui/button';
import { FilterBuilder } from '../../components/filter-builder/filter-builder';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CampaignService } from '../../services/campaign.service';
import { ObjectTypeService } from '../../services/object-type.service';
import { ToastService } from '../../services/toast.service';
import { Campaign, CampaignAudience, CampaignInput, CampaignStats } from '../../models/campaign';
import { FieldDef } from '../../models/object-type';
import { FilterGroup } from '../../models/filter';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

type Step = 'content' | 'audience' | 'send';

// lista + editor in quattro passi (contenuto, pubblico, prova, invio). Il conteggio live è la parte che conta: il pulsante
// deve dire a quanti va e quanti salta, e l'invio vuole il numero DIGITATO, non un sì/no
@Component({
  selector: 'app-campaigns',
  imports: [FormsModule, UiButton, FilterBuilder, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './campaigns.html',
  styles: ``,
})
export class Campaigns {

  private readonly service = inject(CampaignService);
  private readonly objects = inject(ObjectTypeService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly rows: WritableSignal<Campaign[]> = signal([]);
  protected readonly editorOpen: WritableSignal<boolean> = signal(false);
  protected readonly editing: WritableSignal<Campaign | null> = signal(null);
  protected readonly step: WritableSignal<Step> = signal('content');
  protected readonly draft: WritableSignal<CampaignInput> = signal(this.empty());
  protected readonly fields: WritableSignal<FieldDef[]> = signal([]);
  protected readonly audience: WritableSignal<CampaignAudience | null> = signal(null);
  protected readonly stats: WritableSignal<CampaignStats | null> = signal(null);
  protected readonly typed = signal('');
  protected readonly busy: WritableSignal<boolean> = signal(false);

  protected readonly canSend = computed(() => {
    const a = this.audience();
    return !!a && a.sendable > 0 && this.typed().trim() === String(a.sendable);
  });

  constructor() {
    this.load();
  }

  protected open(c: Campaign | null): void {
    this.editorOpen.set(true);
    this.editing.set(c);
    this.audience.set(null);
    this.stats.set(null);
    this.typed.set('');
    this.draft.set(c ? { name: c.name, subject: c.subject, preheader: c.preheader, bodyHtml: c.bodyHtml, bodyText: c.bodyText,
      targetObjectKey: c.targetObjectKey, targetFilter: c.targetFilter } : this.empty());
    this.step.set(c && c.status !== 'DRAFT' ? 'send' : 'content');
    this.loadFields(this.draft().targetObjectKey);
    if (c && c.status !== 'DRAFT') this.refreshStats();
  }

  protected close(): void {
    this.editorOpen.set(false);
    this.load();
  }

  protected set<K extends keyof CampaignInput>(k: K, v: CampaignInput[K]): void {
    this.draft.update((d) => ({ ...d, [k]: v }));
    if (k === 'targetObjectKey') this.loadFields(v as string);
  }

  protected setFilter(f: FilterGroup): void {
    this.set('targetFilter', f);
  }

  // salva e passa al conteggio: il pubblico si calcola sempre sulla versione salvata
  protected saveAndCount(): void {
    this.busy.set(true);
    this.service.save(this.editing()?.id ?? null, this.draft()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (c) => {
        this.editing.set(c);
        this.step.set('audience');
        this.service.audience(c.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((a) => {
          this.audience.set(a);
          this.busy.set(false);
        });
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }


  protected test(): void {
    const c = this.editing();
    if (!c) return;
    this.service.test(c.id).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.toasts.success('Prova inviata alla tua email'));
  }

  protected send(): void {
    const c = this.editing();
    const a = this.audience();
    if (!c || !a || !this.canSend()) return;
    this.busy.set(true);
    this.service.send(c.id, a.sendable).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (sent) => {
        this.busy.set(false);
        this.editing.set(sent);
        this.step.set('send');
        this.toasts.success(`Invio avviato: ${a.sendable} destinatari`);
        this.refreshStats();
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }

  protected pause(): void {
    const c = this.editing();
    if (!c) return;
    this.service.pause(c.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((p) => {
      this.editing.set(p);
      this.load();
    });
  }

  protected refreshStats(): void {
    const c = this.editing();
    if (!c) return;
    this.service.stats(c.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((s) => this.stats.set(s));
  }

  protected rate(n: number, of: number): string {
    return of ? `${Math.round((100*n) / of)}%` : '-';
  }

  private loadFields(key: string): void {
    this.objects.getByKey(key).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((o) => this.fields.set(o.fields ?? []));
  }

  private load(): void {
    this.service.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((r) => this.rows.set(r));
  }

  private empty(): CampaignInput {
    return {
      name: '', subject: '', preheader: '', targetObjectKey: 'contact',
      targetFilter: { combinator: 'and', conditions: [] },
      bodyHtml: '<p>Ciao <span th:text="${r.firstName} ?: \'\'"></span>,</p>\n<p></p>',
      bodyText: 'Ciao [[${r.firstName} ?: \'\']],\n'
    };
  }

}
