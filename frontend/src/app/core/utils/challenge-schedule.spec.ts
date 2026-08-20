import { describe, expect, it } from 'vitest';
import { validateChallengeSchedule } from './challenge-schedule';

const messages = {
  required: 'required',
  formIncomplete: 'incomplete',
  invalidStartDate: 'bad-start',
  invalidEndDate: 'bad-end',
  invalidMaxGames: 'bad-games',
  endBeforeStart: 'end-before-start',
};

describe('validateChallengeSchedule', () => {
  it('requires start and end date in DATE mode', () => {
    const result = validateChallengeSchedule({
      endMode: 'DATE',
      startDateInput: '',
      startHourInput: 18,
      endDateInput: '',
      endHourInput: 18,
      maxGamesInput: null,
      messages,
    });

    expect(result.valid).toBe(false);
    if (!result.valid) {
      expect(result.formError).toBe('incomplete');
      expect([...result.invalidFields]).toEqual(expect.arrayContaining(['startDate', 'endDate']));
    }
  });

  it('accepts a valid DATE schedule', () => {
    const result = validateChallengeSchedule({
      endMode: 'DATE',
      startDateInput: '2026-08-20',
      startHourInput: 10,
      endDateInput: '2026-08-21',
      endHourInput: 10,
      maxGamesInput: null,
      messages,
    });

    expect(result.valid).toBe(true);
    if (result.valid) {
      expect(result.endAt).not.toBeNull();
      expect(result.maxGames).toBeNull();
    }
  });

  it('accepts a valid GAMES schedule', () => {
    const result = validateChallengeSchedule({
      endMode: 'GAMES',
      startDateInput: '2026-08-20',
      startHourInput: 10,
      endDateInput: '',
      endHourInput: 0,
      maxGamesInput: 20,
      messages,
    });

    expect(result.valid).toBe(true);
    if (result.valid) {
      expect(result.endAt).toBeNull();
      expect(result.maxGames).toBe(20);
    }
  });

  it('rejects end before start', () => {
    const result = validateChallengeSchedule({
      endMode: 'DATE',
      startDateInput: '2026-08-21',
      startHourInput: 10,
      endDateInput: '2026-08-20',
      endHourInput: 10,
      maxGamesInput: null,
      messages,
    });

    expect(result.valid).toBe(false);
    if (!result.valid) {
      expect(result.formError).toBe('end-before-start');
    }
  });
});
