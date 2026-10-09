import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UiButton } from '../../components/ui/button';
import { CampaignService } from '../../services/campaign.service';
import { NpsStats, NpsSurvey } from '../../models/campaign';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';


// i sondaggi e il numero. Il numero da solo non significa niente: si mostra sempre col conteggio
@Component({
  selector: 'app-nps',
  imports: [FormsModule, UiButton],
  templateUrl: './nps.html',
  styles: ``,
})
export class Nps {
  private readonly service = inject(CampaignService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly surveys: WritableSignal<NpsSurvey[]> = signal([]);
  protected readonly stats: WritableSignal<Record<string, NpsStats>> = signal({});
  protected readonly draft = signal<Omit<NpsSurvey, 'id'>>({ name: '', question: 'Quanto consiglieresti la nostra azienda a un collega?', active: true, cooldownDays: 90 });

  constructor() {
    this.load();
  }

  protected create(): void {
    if (!this.draft().name.trim()) return;
    this.service.saveSurvey(null, this.draft()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.draft.update((id) => ({ ...id, name: '' }));
      this.load();
    });
  }

  protected toggle(s: NpsSurvey): void {
    this.service.saveSurvey(s.id, { ...s, active: !s.active }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  private load(): void {
    this.service.surveys().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((list) => {
      this.surveys.set(list);
      for (const s of list) {
        this.service.surveyStats(s.id).pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((st) => this.stats.update((m) => ({ ...m, [s.id]: st })));
      }
    });
  }

}
