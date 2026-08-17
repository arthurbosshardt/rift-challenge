import { Component, computed, effect, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChampionDataService } from '../../../core/services/champion-data.service';

@Component({
  selector: 'app-champion-icon',
  templateUrl: './champion-icon.component.html',
  styleUrl: './champion-icon.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ChampionIconComponent {
  private readonly championData = inject(ChampionDataService);

  readonly championId = input<number | null | undefined>(null);
  readonly iconUrl = input<string | null | undefined>(null);

  protected readonly iconFailed = signal(false);
  private readonly iconUrlIndex = signal(0);

  private readonly resolvedUrls = computed(() => {
    const directUrl = this.iconUrl();
    if (directUrl) {
      return [directUrl, ...this.championData.iconUrls(this.championId())];
    }
    return this.championData.iconUrls(this.championId());
  });

  constructor() {
    void this.championData.ensureLoaded();

    effect(() => {
      this.championId();
      this.iconUrl();
      this.championData.ready();
      this.iconFailed.set(false);
      this.iconUrlIndex.set(0);
    });
  }

  protected currentIconUrl(): string | null {
    if (this.iconFailed()) {
      return null;
    }
    const urls = this.resolvedUrls();
    return urls[this.iconUrlIndex()] ?? null;
  }

  protected onIconError(): void {
    const nextIndex = this.iconUrlIndex() + 1;
    if (nextIndex < this.resolvedUrls().length) {
      this.iconUrlIndex.set(nextIndex);
      return;
    }
    this.iconFailed.set(true);
  }
}
