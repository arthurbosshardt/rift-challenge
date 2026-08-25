# Base de données — PostgreSQL / Flyway

Applies to: `backend/src/main/resources/db/migration/**`, entités JPA.

## Principes

- PostgreSQL, géré via **Flyway**. Toute évolution de schéma = une nouvelle migration commitée. **Jamais** de modification manuelle du schéma en prod, et **jamais** de modification d'une migration déjà appliquée — en ajouter une nouvelle.
- Migrations actuelles : `V1` → `V24`. `V1`–`V13` parlent encore de `race` (nom historique) ; `V14` renomme le concept en `challenge`. `V17`–`V19` font évoluer `share_slug` vers un UUID stable (ne pas recréer un slug basé sur le nom).
- Timestamps **UTC** partout en base. Conversion locale uniquement à l'affichage (frontend).

## Concepts persistés

- `app_user`
- `user_riot_account` (PUUID, icône — un seul compte par utilisateur, `UNIQUE (user_id)`)
- `challenge` (owner, name unique, type, région, start/end UTC, share_slug, data_synced_at)
- `challenge_participant`
- `challenge_duo` (2 participants)
- `riot_match` + `challenge_participant_match` (win, champion, timestamps — dédup par `riot_match_id`)
- `rank_snapshot` (type BASELINE/REFRESH, flag estimated)
- `challenge_refresh` (dernier refresh accepté — réclamé de façon atomique, voir `10-backend.md`)
- `leaderboard_cache` (snapshot du classement global, régénéré périodiquement)

## Intégrité

- Un utilisateur applicatif possède des challenges ; un compte Riot est indépendant de l'auth applicative (ne pas supposer 1 user = 1 compte Riot).
- Contraintes utiles : email/username unique, nom de challenge unique (ignore-case), `share_slug` unique, FK vers challenge/participant/duo, cascade appropriée pour éviter les participants/duos/matchs orphelins.
- Index sur les colonnes de lookup fréquent : `challenge.id`, `owner_id`, `challenge_participant`, PUUID Riot, `riot_match_id`, timestamps de match, lookup de refresh. Ne pas indexer à l'aveugle — justifier par le pattern d'accès réel.

## Riot match data

Ne pas stocker la réponse Riot brute. Persister uniquement le nécessaire : identification participant, dédup (`riot_match_id`), win/loss, queue, timestamps, progression LP/rang, détection DuoQ.
