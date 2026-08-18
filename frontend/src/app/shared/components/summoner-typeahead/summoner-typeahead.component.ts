import {
  ChangeDetectionStrategy,
  Component,
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
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';

@Component({
  selector: 'app-summoner-typeahead',
  imports: [FormsModule, PlayerAvatarComponent],
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

  private readonly searchApi = inject(SummonerSearchService);
  private readonly queries = new Subject<string>();
  private readonly root = viewChild<ElementRef<HTMLElement>>('root');

  protected readonly suggestions = signal<SummonerSuggestion[]>([]);
  protected readonly open = signal(false);

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
      });
  }

  @HostListener('document:click', ['$event'])
  protected closeOnOutsideClick(event: MouseEvent): void {
    const host = this.root()?.nativeElement;
    if (host && !host.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  protected onInput(value: string): void {
    this.valueChange.emit(value);
    this.queries.next(value);
  }

  protected pick(suggestion: SummonerSuggestion): void {
    this.valueChange.emit(suggestion.gameName);
    this.selected.emit(suggestion);
    this.suggestions.set([]);
    this.open.set(false);
  }
}
