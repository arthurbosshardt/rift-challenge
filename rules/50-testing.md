# Tests

Applies to: `backend/src/test/**`, `frontend/src/**/*.spec.ts`.

## Général

Toute règle métier significative a un test. Les tests vérifient un comportement, pas un détail d'implémentation.

## Backend — JUnit + Mockito

- **Jamais** d'appel réseau réel dans un test unitaire — tous les clients Riot sont mockés (voir `40-riot-api.md`).
- Priorités : ownership, limites 16 (SOLOQ) / 8 (DUOQ) / 10 (comptes Riot), dates UTC et `endAt > startAt`, cooldown de refresh (accepté / rejeté / concurrence), queue 420 uniquement, matchs hors fenêtre exclus, dédup au resync, détection DuoQ, calculs LP/rang, clés i18n FR/EN synchronisées (`translations.spec.ts`).
- Tests de refresh : premier refresh accepté, second avant 2 min rejeté, refresh après 2 min accepté, deux requêtes concurrentes ne doivent jamais **toutes les deux** passer (voir la réclamation atomique dans `10-backend.md`).
- `ChallengeParticipantSyncService` (cœur du pipeline de sync — filtre queue, bornes de fenêtre, dédup, plafond d'import) a sa propre suite de tests dédiée (`ChallengeParticipantSyncServiceTest`) : à étendre en priorité si son comportement change.

## Frontend — Vitest (`ng test`, jsdom)

- Utiliser `ng test`, pas `npx vitest run` directement (le runner CLI configure le compilateur JIT et les globals de test nécessaires).
- Périmètre actuel : utils et services (`core/utils/*.spec.ts`, `core/services/*.spec.ts`), i18n. **Pas de tests de composants Angular et pas de tests e2e (Cypress/Playwright) pour l'instant** — choix assumé, pas un trou à combler par défaut. Si un composant devient assez complexe/critique pour justifier un test dédié, en discuter avant d'introduire l'outillage e2e.

## Régression

En cas de bug : 1) reproduire avec un test, 2) corriger, 3) garder le test de régression.
