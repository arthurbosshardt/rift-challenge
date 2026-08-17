import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CreateRaceModalService } from '../../core/services/create-race-modal.service';

@Component({
  selector: 'app-create-race-redirect',
  template: '',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class CreateRaceRedirectComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly createRaceModal = inject(CreateRaceModalService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  async ngOnInit(): Promise<void> {
    await this.auth.waitUntilReady();

    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/created-races';
    this.createRaceModal.open();
    await this.router.navigateByUrl(returnUrl, { replaceUrl: true });
  }
}
