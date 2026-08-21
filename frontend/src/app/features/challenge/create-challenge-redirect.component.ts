import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CreateChallengeModalService } from '../../core/services/create-challenge-modal.service';

@Component({
  selector: 'app-create-challenge-redirect',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateChallengeRedirectComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly createChallengeModal = inject(CreateChallengeModalService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  async ngOnInit(): Promise<void> {
    await this.auth.waitUntilReady();

    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/my-challenges';
    this.createChallengeModal.open();
    await this.router.navigateByUrl(returnUrl, { replaceUrl: true });
  }
}
