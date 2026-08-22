import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type NavIconName =
  | 'public-challenges'
  | 'my-activity'
  | 'create-challenge'
  | 'settings'
  | 'login'
  | 'signup'
  | 'logout'
  | 'challenges'
  | 'history'
  | 'stats'
  | 'leaderboard';

@Component({
  selector: 'app-nav-icon',
  changeDetection: ChangeDetectionStrategy.Eager,
  host: {
    class: 'nav-icon',
  },
  template: `
    @switch (name()) {
      @case ('public-challenges') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
            fill="currentColor"
          />
        </svg>
      }
      @case ('my-activity') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M18 20a6 6 0 0 0-12 0"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <circle cx="12" cy="10" r="4" fill="none" stroke="currentColor" stroke-width="1.75" />
          <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="1.75" />
        </svg>
      }
      @case ('challenges') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"
            fill="currentColor"
          />
        </svg>
      }
      @case ('history') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M3 12a9 9 0 1 0 2.64-6.36L3 8"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path d="M3 3v5h5" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
          <path d="M12 7v5l4 2" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      }
      @case ('stats') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M4 19V5M4 19h16M8 16V9M12 16V7M16 16v-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      }
      @case ('leaderboard') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M13.5.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67zM11.71 19c-1.78 0-3.22-1.4-3.22-3.14 0-1.62 1.05-2.76 2.81-3.12 1.77-.36 3.6-1.21 4.62-2.58.39 1.29.59 2.65.59 4.04 0 2.65-2.15 4.8-4.8 4.8z"
            fill="currentColor"
          />
        </svg>
      }
      @case ('create-challenge') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 5v14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
          <path d="M5 12h14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
        </svg>
      }
      @case ('settings') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
          />
          <path
            d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.604.852.997 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      }
      @case ('login') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      }
      @case ('signup') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <circle
            cx="9"
            cy="7"
            r="4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
          />
          <path
            d="M19 8v6M22 11h-6"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
          />
        </svg>
      }
      @case ('logout') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M12 2v10M18.36 6.64a9 9 0 1 1-12.72 0"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      }
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-shrink: 0;
      align-items: center;
      justify-content: center;
      width: 1.125rem;
      height: 1.125rem;
      color: inherit;
    }

    svg {
      display: block;
      width: 100%;
      height: 100%;
    }
  `,
})
export class NavIconComponent {
  readonly name = input.required<NavIconName>();
}
