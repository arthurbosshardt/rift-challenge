import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SkeletonComponent } from '../skeleton/skeleton.component';

@Component({
  selector: 'app-settings-accounts-skeleton',
  imports: [SkeletonComponent],
  templateUrl: './settings-accounts-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './settings-accounts-skeleton.component.scss',
})
export class SettingsAccountsSkeletonComponent {}
