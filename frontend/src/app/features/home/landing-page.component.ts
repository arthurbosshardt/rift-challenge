import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthModalService } from '../../core/services/auth-modal.service';
import { AuthService } from '../../core/services/auth.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';

type LandingInspiration = {
  id: 'korea' | 'iron' | 'duo' | 'lp';
  titleKey: string;
  textKey: string;
  imageSrc: string;
  imageAltKey: string;
  url: string;
};

@Component({
  selector: 'app-landing-page',
  imports: [PageShellComponent, RouterLink, TranslatePipe],
  templateUrl: './landing-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './landing-page.component.scss',
})
export class LandingPageComponent {
  protected readonly auth = inject(AuthService);
  protected readonly authModal = inject(AuthModalService);

  protected readonly inspirations: LandingInspiration[] = [
    {
      id: 'korea',
      titleKey: 'landing.inspiredKoreaTitle',
      textKey: 'landing.inspiredKoreaText',
      imageSrc: '/landing/korea.jpg',
      imageAltKey: 'landing.inspiredKoreaImageAlt',
      url: 'https://www.youtube.com/watch?v=BktkUH8uG64',
    },
    {
      id: 'iron',
      titleKey: 'landing.inspiredIronTitle',
      textKey: 'landing.inspiredIronText',
      imageSrc: '/landing/iron.jpg',
      imageAltKey: 'landing.inspiredIronImageAlt',
      url: 'https://www.reddit.com/r/leagueoflegends/search/?q=iron+to+challenger',
    },
    {
      id: 'duo',
      titleKey: 'landing.inspiredDuoTitle',
      textKey: 'landing.inspiredDuoText',
      imageSrc: '/landing/duo-yt.jpg',
      imageAltKey: 'landing.inspiredDuoImageAlt',
      url: 'https://www.youtube.com/watch?v=lx2AU4CAD0o',
    },
    {
      id: 'lp',
      titleKey: 'landing.inspiredLpTitle',
      textKey: 'landing.inspiredLpText',
      imageSrc: '/landing/lp-yt.jpg',
      imageAltKey: 'landing.inspiredLpImageAlt',
      url: 'https://www.youtube.com/watch?v=WgMKRDvgp7A',
    },
  ];
}
