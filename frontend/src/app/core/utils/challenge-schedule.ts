import { buildLocalStartAtIso } from './challenge-date';

export type ChallengeEndMode = 'DATE' | 'GAMES';

export type ScheduleInvalidField = 'startDate' | 'endDate' | 'maxGames';

export type ScheduleValidationMessages = {
  required: string;
  formIncomplete: string;
  invalidStartDate: string;
  invalidEndDate: string;
  invalidMaxGames: string;
  endBeforeStart: string;
};

export type ScheduleValidationInput = {
  endMode: ChallengeEndMode;
  startDateInput: string;
  startHourInput: number;
  endDateInput: string;
  endHourInput: number;
  maxGamesInput: number | null;
  messages: ScheduleValidationMessages;
};

export type ScheduleValidationResult =
  | {
      valid: true;
      startAt: string;
      endAt: string | null;
      maxGames: number | null;
    }
  | {
      valid: false;
      invalidFields: Set<ScheduleInvalidField>;
      fieldErrors: Partial<Record<ScheduleInvalidField, string>>;
      formError: string;
    };

export function validateChallengeSchedule(input: ScheduleValidationInput): ScheduleValidationResult {
  const invalidFields = new Set<ScheduleInvalidField>();
  const fieldErrors: Partial<Record<ScheduleInvalidField, string>> = {};
  const { messages } = input;

  if (!input.startDateInput.trim()) {
    invalidFields.add('startDate');
    fieldErrors.startDate = messages.required;
  }

  if (input.endMode === 'DATE' && !input.endDateInput.trim()) {
    invalidFields.add('endDate');
    fieldErrors.endDate = messages.required;
  }

  if (input.endMode === 'GAMES' && (input.maxGamesInput === null || input.maxGamesInput === undefined)) {
    invalidFields.add('maxGames');
    fieldErrors.maxGames = messages.required;
  }

  if (invalidFields.size > 0) {
    return {
      valid: false,
      invalidFields,
      fieldErrors,
      formError: messages.formIncomplete,
    };
  }

  const startAt = buildLocalStartAtIso(input.startDateInput, input.startHourInput);
  if (!startAt) {
    return {
      valid: false,
      invalidFields: new Set(['startDate']),
      fieldErrors: { startDate: messages.invalidStartDate },
      formError: messages.invalidStartDate,
    };
  }

  if (input.endMode === 'GAMES') {
    const maxGames = Math.trunc(input.maxGamesInput as number);
    if (!Number.isFinite(maxGames) || maxGames <= 0) {
      return {
        valid: false,
        invalidFields: new Set(['maxGames']),
        fieldErrors: { maxGames: messages.invalidMaxGames },
        formError: messages.invalidMaxGames,
      };
    }

    return { valid: true, startAt, endAt: null, maxGames };
  }

  const endAt = buildLocalStartAtIso(input.endDateInput, input.endHourInput);
  if (!endAt) {
    return {
      valid: false,
      invalidFields: new Set(['endDate']),
      fieldErrors: { endDate: messages.invalidEndDate },
      formError: messages.invalidEndDate,
    };
  }

  if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
    return {
      valid: false,
      invalidFields: new Set(['endDate']),
      fieldErrors: { endDate: messages.endBeforeStart },
      formError: messages.endBeforeStart,
    };
  }

  return { valid: true, startAt, endAt, maxGames: null };
}
