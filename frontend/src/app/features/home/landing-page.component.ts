import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthModalService } from '../../core/services/auth-modal.service';
import { AuthService } from '../../core/services/auth.service';
import { BackendStatusService } from '../../core/services/backend-status.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { RankEmblemComponent } from '../../shared/components/rank-emblem/rank-emblem.component';

type LandingInspiration = {
  id: 'korea' | 'iron' | 'duo' | 'lp';
  titleKey: string;
  textKey: string;
  imageSrc: string;
  imageFallback: string;
  imageAltKey: string;
  url: string;
};

@Component({
  selector: 'app-landing-page',
  imports: [PageShellComponent, RouterLink, TranslatePipe, RankEmblemComponent],
  templateUrl: './landing-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './landing-page.component.scss',
})
export class LandingPageComponent {
  protected readonly auth = inject(AuthService);
  protected readonly authModal = inject(AuthModalService);
  protected readonly backend = inject(BackendStatusService);

  protected readonly inspirations: LandingInspiration[] = [
    {
      id: 'korea',
      titleKey: 'landing.inspiredKoreaTitle',
      textKey: 'landing.inspiredKoreaText',
      imageSrc: '/landing/card-korea.png',
      imageFallback: '/landing/korea.jpg',
      imageAltKey: 'landing.inspiredKoreaImageAlt',
      url: 'https://www.youtube.com/watch?v=BktkUH8uG64',
    },
    {
      id: 'iron',
      titleKey: 'landing.inspiredIronTitle',
      textKey: 'landing.inspiredIronText',
      imageSrc: '/landing/card-iron.png',
      imageFallback: '/landing/iron.jpg',
      imageAltKey: 'landing.inspiredIronImageAlt',
      url: 'https://www.reddit.com/r/leagueoflegends/search/?q=iron+to+challenger',
    },
    {
      id: 'duo',
      titleKey: 'landing.inspiredDuoTitle',
      textKey: 'landing.inspiredDuoText',
      imageSrc: '/landing/card-duo.png',
      imageFallback: '/landing/duo.jpg',
      imageAltKey: 'landing.inspiredDuoImageAlt',
      url: 'https://www.youtube.com/watch?v=lx2AU4CAD0o',
    },
    {
      id: 'lp',
      titleKey: 'landing.inspiredLpTitle',
      textKey: 'landing.inspiredLpText',
      imageSrc: '/landing/card-lp.png',
      imageFallback: '/landing/lp.jpg',
      imageAltKey: 'landing.inspiredLpImageAlt',
      url: 'https://soloqchallenge.fr/',
    },
  ];

  protected onCardImageError(event: Event, fallbackSrc: string): void {
    const img = event.target as HTMLImageElement | null;
    if (!img || img.dataset['fallbackApplied'] === 'true') {
      return;
    }
    img.dataset['fallbackApplied'] = 'true';
    img.src = fallbackSrc;
  }
}
