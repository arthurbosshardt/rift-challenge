# Backend — Spring Boot

Applies to: `backend/**`. Voir `00-project.md` pour les règles métier et `40-riot-api.md` pour l'intégration Riot.

## Stack

Java **21**, Spring Boot **3.3**, Maven, Spring Web, Spring Data JPA, PostgreSQL, Flyway, Bean Validation, Spring Security (filtre JWT Supabase), Actuator (health uniquement exposé).

## Architecture

Packages orientés **feature**, pas par couche technique globale :

```
authentication/   — filtre JWT Supabase, /api/auth/me
account/          — AppUser, comptes Riot liés
challenge/        — CRUD, progress, duos, throttles, OG, sitemap
riot/             — clients HTTP Riot, parsing, icônes, rank score, replay LP
synchronization/  — sync des matchs, snapshots de rang, backfill champion
summoner/         — recherche / résolution d'invocateur
leaderboard/      — cache, calcul, scheduler du classement global
match/            — détail de match (partagé challenge/duo)
config/           — Spring config (sécurité, CORS, Riot, executor)
common/           — utilitaires partagés (throttle générique, résolution de clé client)
health/           — health checks
```

Dans une feature : `controller` (mince, délègue), `service` (règles métier), `repository` (persistance), `dto` (contrats API), entité JPA si besoin. **Controllers minces. Règles dans les services. Entités JPA jamais exposées — toujours des DTOs.**

## API (repères)

Base `/api`.

Challenges :
- `GET /api/challenges/public?challengeName=&summoner=&type=` — liste (cache), `POST /public/refresh`
- `GET /api/challenges/owned` · `/participating` · `/mine`
- `GET /api/challenges/share/{shareSlug}`
- `POST /api/challenges` (throttle mutation par user)
- `PATCH /{id}` (nom + dates ensemble) · `PATCH /{id}/schedule|start|end|name`
- `DELETE /{id}`
- `POST /{id}/refresh` (cooldown 2 min atomique + throttle IP)
- `POST/DELETE /{id}/participants`, `/{id}/duos` (throttle mutation par user)
- `GET /recent` (dashboard, auth, throttle dédié)

Autres : `GET /api/auth/me` · comptes Riot (`UserRiotAccountController`) · `GET /api/summoners/search|resolve` · icônes champion `/api/champion-icons/{id}.png` (proxy backend) · OG `/api/challenge-preview[-image]/{slug}` · `GET /api/leaderboard` + `POST /api/leaderboard/refresh` (admin) · `GET /api/sitemap.xml` (dynamique, toutes les URLs de challenges publiques) · `GET /actuator/health`.

Auth : Bearer JWT Supabase. Deux `SecurityFilterChain` (endpoints publics vs authentifiés) — voir `SecurityConfig`. CORS piloté par la variable d'env `CORS_ORIGINS` (jamais de wildcard en dur dans le code).

Validation : **toujours** backend (`@Valid` + règles dans les services, avec des constantes nommées pour les limites : `MAX_PARTICIPANTS`, `MAX_DUOS`, `MAX_ACCOUNTS_PER_USER`). Le frontend valide pour l'UX seulement.

## Ownership et IDOR

Chaque endpoint mutatif (challenge, participants, duos, comptes Riot) doit vérifier l'ownership **côté service**, pas seulement dans le controller (pattern `requireOwnedChallenge` / `findByIdAndUserId`). Un ID dans l'URL ne prouve jamais un droit d'accès.

## Throttling et concurrence

Plusieurs mécanismes complémentaires, tous côté backend :

- **Cooldown de refresh** (2 min/challenge) : persistant en base (`challenge_refresh`), réclamé via un **upsert atomique** (`INSERT ... ON CONFLICT ... WHERE refreshed_at <= cooldownFloor`) — pas un read-puis-write, qui laisserait une fenêtre de course entre deux requêtes concurrentes. Voir `ChallengeRefreshRepository.claimRefresh` / `ChallengeRefreshRecordService.tryClaimRefresh`.
- **Throttle IP** (~5 s) sur le refresh, en mémoire (`IntervalRequestThrottle`) — suffisant en instance unique ; à revoir (Redis ou équivalent partagé) si le service scale un jour à plusieurs instances.
- **Throttle par utilisateur** (~2 s, `ChallengeMutationRequestThrottle`) sur la création de challenge/participant/duo, pour éviter qu'un compte n'épuise le quota Riot partagé par spam.
- **Throttle activité récente** (~10 s/user) sur `/recent`, plus coûteux en appels Riot.

Pour tout nouveau mécanisme de limite : privilégier une opération atomique en base si plusieurs instances peuvent tourner en parallèle ; un simple map en mémoire suffit pour un throttle anti-spam à faible enjeu, pas pour une garantie métier dure.

## Appels Riot

Timeouts configurés (connect/read), retry borné avec `Retry-After` sur 429. Jamais d'appel Riot dans une transaction DB ouverte (les écritures se font en `@Transactional(REQUIRES_NEW)` séparées des appels réseau). Voir `40-riot-api.md`.

## Gestion d'erreurs

`GlobalExceptionHandler` centralise les réponses d'erreur connues. Ne jamais laisser fuiter une stack trace ou un message Postgres brut au client. Un `catch (Exception e)` qui avale silencieusement une erreur doit au minimum logger.

## Config

Pool Hikari dimensionné pour le pooler Supabase (session pooler, ~15 clients partagés — ne pas monter le pool sans vérifier cette limite). Pas de niveau `DEBUG` en prod (fuite potentielle de données dans les logs).
