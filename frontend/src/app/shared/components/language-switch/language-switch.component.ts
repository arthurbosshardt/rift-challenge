import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';

const BASE_OFFSET_PX = 16;
const FOOTER_GAP_PX = 12;

@Component({
  selector: 'app-language-switch',
  imports: [TranslatePipe],
  templateUrl: './language-switch.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './language-switch.component.scss',
})
export class LanguageSwitchComponent {
  protected readonly i18n = inject(I18nService);

  protected readonly bottomOffsetPx = signal(BASE_OFFSET_PX);

  constructor() {
    this.updateOffset();
  }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  protected updateOffset(): void {
    const footer = document.querySelector('footer.site-footer');
    if (!footer) {
      this.bottomOffsetPx.set(BASE_OFFSET_PX);
      return;
    }
    const footerTop = footer.getBoundingClientRect().top;
    const overlap = window.innerHeight - footerTop;
    this.bottomOffsetPx.set(overlap > 0 ? BASE_OFFSET_PX + overlap + FOOTER_GAP_PX : BASE_OFFSET_PX);
  }
}
