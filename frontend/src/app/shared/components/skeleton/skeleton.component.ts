import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-skeleton',
  host: {
    class: 'skeleton',
    '[class.skeleton--circle]': 'circle()',
    '[class.skeleton--inline]': 'inline()',
    '[style.width]': 'width()',
    '[style.height]': 'height()',
    '[style.max-width]': 'maxWidth()',
    '[style.border-radius]': 'radius()',
  },
  template: '',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './skeleton.component.scss',
})
export class SkeletonComponent {
  readonly circle = input(false);
  readonly inline = input(false);
  readonly width = input<string | null>(null);
  readonly height = input<string | null>(null);
  readonly maxWidth = input<string | null>(null);
  readonly radius = input<string | null>(null);
}
