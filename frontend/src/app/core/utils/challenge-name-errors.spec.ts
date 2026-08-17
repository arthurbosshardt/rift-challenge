import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { isChallengeNameTakenError } from './challenge-name-errors';

function conflict(message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 409,
    error: { message },
  });
}

describe('isChallengeNameTakenError', () => {
  it('detects the current challenge name conflict message', () => {
    expect(isChallengeNameTakenError(conflict('Challenge name already taken'))).toBe(true);
  });

  it('still detects the previous challenge name conflict message', () => {
    expect(isChallengeNameTakenError(conflict('Challenge name already taken'))).toBe(true);
  });

  it('ignores other conflicts', () => {
    expect(isChallengeNameTakenError(conflict('Account already linked'))).toBe(false);
  });
});
