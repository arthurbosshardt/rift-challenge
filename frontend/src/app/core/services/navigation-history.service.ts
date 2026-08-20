import { Injectable, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class NavigationHistoryService {
  private readonly router = inject(Router);
  private readonly previous = signal<string | null>(null);
  private currentUrl = this.router.url;

  readonly previousUrl = this.previous.asReadonly();

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.previous.set(this.currentUrl);
        this.currentUrl = event.urlAfterRedirects;
      });
  }
}
