import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SummonerSearchService, SummonerSuggestion } from '../../../core/services/summoner-search.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { parseRiotId } from '../../../core/utils/riot-id';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { NavIconComponent } from '../nav-icon/nav-icon.component';

@Component({
  selector: 'app-summoner-typeahead',
  imports: [FormsModule, PlayerAvatarComponent, NavIconComponent],
  templateUrl: './summoner-typeahead.component.html',
  styleUrl: './summoner-typeahead.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummonerTypeaheadComponent {
  readonly name = input.required<string>();
  readonly placeholder = input('');
  readonly disabled = input(false);
  readonly invalid = input(false);
  readonly ariaInvalid = input(false);
  readonly value = input('');
  readonly valueChange = output<string>();
  readonly selected = output<SummonerSuggestion>();
  /** No suggestion matched (e.g. a real player never added to a challenge before), but the typed
   *  value still parses as a valid gameName#tagLine — let the caller navigate there directly and
   *  resolve it server-side instead of silently doing nothing. Only emitted when showSearchButton
   *  is set, same as {@link triggerSearch}'s existing top-suggestion pick. */
  readonly manualSubmit = output<string>();
  readonly showSearchButton = input(false);

  private readonly searchApi = inject(SummonerSearchService);
  private readonly i18n = inject(I18nService);
  private readonly queries = new Subject<string>();
  private readonly root = viewChild<ElementRef<HTMLElement>>('root');

  protected readonly suggestions = signal<SummonerSuggestion[]>([]);
  protected readonly open = signal(false);
  protected readonly dropdownPosition = signal({ top: 0, left: 0, width: 0 });

  constructor() {
    this.queries
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((query) => this.searchApi.search(query)),
        takeUntilDestroyed(),
      )
      .subscribe((results) => {
        this.suggestions.set(results);
        this.open.set(results.length > 0);
        if (results.length > 0) {
          this.updateDropdownPosition();
        }
      });

    // Dropdown is `position: fixed` so it can escape clipping ancestors (e.g. a
    // scrollable modal body); re-anchor it whenever the page scrolls or resizes.
    // Capture phase is required since scroll events don't bubble.
    const reposition = (): void => {
      if (this.open()) {
        this.updateDropdownPosition();
      }
    };
    window.addEventListener('scroll', reposition, true);
    window.addEventListener('resize', reposition);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('scroll', reposition, true);
      window.removeEventListener('resize', reposition);
    });
  }

  @HostListener('document:click', ['$event'])
  protected closeOnOutsideClick(event: MouseEvent): void {
    const host = this.root()?.nativeElement;
    if (host && !host.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  private updateDropdownPosition(): void {
    const host = this.root()?.nativeElement;
    if (!host) {
      return;
    }
    const rect = host.getBoundingClientRect();
    this.dropdownPosition.set({ top: rect.bottom + 4, left: rect.left, width: rect.width });
  }

  protected onInput(value: string): void {
    this.valueChange.emit(value);
    const hashIndex = value.indexOf('#');
    const query = hashIndex > 0 ? value.slice(0, hashIndex).trim() : value;
    this.queries.next(query);
  }

  protected pick(suggestion: SummonerSuggestion): void {
    this.valueChange.emit(suggestion.riotId);
    this.selected.emit(suggestion);
    this.suggestions.set([]);
    this.open.set(false);
  }

  protected onEnter(event: Event): void {
    if (!this.showSearchButton()) {
      return;
    }
    event.preventDefault();
    this.triggerSearch();
  }

  /**
   * Picks the top autocomplete match — used by the Enter key and the search button. Falls back
   * to emitting the raw typed value when it parses as a valid Riot ID but matched no suggestion
   * (nothing happens otherwise, which used to be the case for any player this app hadn't already
   * seen before).
   */
  protected triggerSearch(): void {
    const top = this.suggestions()[0];
    if (top) {
      this.pick(top);
      return;
    }

    const parsed = parseRiotId(this.value());
    if (parsed) {
      this.manualSubmit.emit(`${parsed.gameName}#${parsed.tagLine}`);
    }
  }

  protected searchAriaLabel(): string {
    return this.i18n.t('nav.searchPlayerSubmitAria');
  }
}
