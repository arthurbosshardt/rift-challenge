import { ChangeDetectionStrategy, Component } from '@angular/core';
import { APP_VERSION } from '../../../core/version';
import { TranslatePipe } from '../../../core/i18n/t.pipe';

@Component({
  selector: 'app-site-footer',
  imports: [TranslatePipe],
  templateUrl: './site-footer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './site-footer.component.scss',
})
export class SiteFooterComponent {
  protected readonly creatorEmail = 'tanor.pro@gmail.com';
  protected readonly creatorDiscord = '_tanor';
  protected readonly appVersion = APP_VERSION;
}
