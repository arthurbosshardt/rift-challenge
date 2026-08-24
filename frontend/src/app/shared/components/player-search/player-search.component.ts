import { ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { SummonerTypeaheadComponent } from '../summoner-typeahead/summoner-typeahead.component';
import { SummonerSuggestion } from '../../../core/services/summoner-search.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { NavIconComponent } from '../nav-icon/nav-icon.component';

@Component({
  selector: 'app-player-search',
  imports: [SummonerTypeaheadComponent, TranslatePipe, NavIconComponent],
  templateUrl: './player-search.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './player-search.component.scss',
})
export class PlayerSearchComponent {
  private readonly router = inject(Router);
  private readonly root = viewChild<ElementRef<HTMLElement>>('root');

  protected readonly expanded = signal(false);
  protected readonly query = signal('');

  @HostListener('document:click', ['$event'])
  protected collapseOnOutsideClick(event: MouseEvent): void {
    if (!this.expanded()) {
      return;
    }
    const host = this.root()?.nativeElement;
    if (host && !host.contains(event.target as Node)) {
      this.collapse();
    }
  }

  @HostListener('document:keydown.escape')
  protected collapseOnEscape(): void {
    this.collapse();
  }

  protected expand(): void {
    this.expanded.set(true);
  }

  protected collapse(): void {
    this.expanded.set(false);
    this.query.set('');
  }

  protected onSelected(suggestion: SummonerSuggestion): void {
    this.collapse();
    void this.router.navigate(['/players', suggestion.riotId]);
  }
}
