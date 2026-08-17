import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';

@Component({
  selector: 'app-settings-page',
  template: '',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class SettingsPageComponent implements OnInit {
  private readonly settingsModal = inject(SettingsModalService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  ngOnInit(): void {
    this.settingsModal.open();
    const target = this.auth.linkedAccount() ? '/my-challenges' : '/public-challenges';
    void this.router.navigateByUrl(target, { replaceUrl: true });
  }
}
