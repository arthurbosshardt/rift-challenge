# Base de données — PostgreSQL / Flyway

Applies to: `backend/src/main/resources/db/migration/**`, entités JPA.

## Principes

- PostgreSQL, géré via **Flyway**. Toute évolution de schéma = une nouvelle migration commitée. **Jamais** de modification manuelle du schéma en prod, et **jamais** de modification d'une migration déjà appliquée — en ajouter une nouvelle.
- Migrations actuelles : `V1` → `V35`. `V1`–`V13` parlent encore de `race` (nom historique) ; `V14` renomme le concept en `challenge`. `V17`–`V19` font évoluer `share_slug` vers un UUID stable (ne pas recréer un slug basé sur le nom). `V25`+ construisent le socle leaderboard (cache, sync des comptes, stats/historique de rang par compte, registre de comptes Riot indépendant des challenges) ; `V26` retire le multi-comptes (`user_riot_account` redevient `UNIQUE (user_id)`) ; `V33` fusionne les anciennes tables de matchs par compte.
- Timestamps **UTC** partout en base. Conversion locale uniquement à l'affichage (frontend).

## Concepts persistés

- `app_user`
- `user_riot_account` (PUUID, icône — un seul compte par utilisateur, `UNIQUE (user_id)`)
- `riot_account` (registre de tout compte Riot connu du système — lié ou non à un `app_user` — utilisé pour la sync leaderboard/profil joueur indépendamment des challenges, `V30`)
- `challenge` (owner, name unique, type, région, start/end UTC, share_slug, data_synced_at)
- `challenge_participant`
- `challenge_duo` (2 participants)
- `account_match` (win, champion, stats de combat par **compte** — indépendant de tout challenge ; un challenge recalcule sa progression à la volée en filtrant ces lignes par sa propre fenêtre de dates. Anciennement deux tables séparées, `leaderboard_account_match` + `challenge_participant_match`, fusionnées en `V33` ; le flag `historical` marque les lignes migrées sans stats de combat)
- `rank_snapshot` (type BASELINE/REFRESH, flag estimated, par challenge)
- `leaderboard_account_rank` + `leaderboard_account_rank_history` (rang/LP courant et historique par compte, pour calculer les LP gagnés exacts hors contexte d'un challenge, `V25`/`V35`)
- `challenge_refresh` (dernier refresh accepté pour un challenge — réclamé de façon atomique, voir `10-backend.md`)
- `leaderboard_cache` (snapshot du classement global, régénéré périodiquement)

## Intégrité

- Un utilisateur applicatif possède des challenges ; un compte Riot est indépendant de l'auth applicative (ne pas supposer 1 user = 1 compte Riot). `riot_account` peut exister sans `app_user` associé (comptes suivis pour le leaderboard/profils publics sans lien applicatif).
- Contraintes utiles : email/username unique, nom de challenge unique (ignore-case), `share_slug` unique, `account_match` unique par `(riot_puuid, riot_match_id)`, FK vers challenge/participant/duo, cascade appropriée pour éviter les participants/duos orphelins.
- Index sur les colonnes de lookup fréquent : `challenge.id`, `owner_id`, `challenge_participant`, PUUID Riot, `riot_match_id`, `champion_id` (`account_match`), timestamps de match, lookup de refresh. Ne pas indexer à l'aveugle — justifier par le pattern d'accès réel.

## Riot match data

Ne pas stocker la réponse Riot brute. Persister uniquement le nécessaire : identification participant, dédup (`riot_match_id`), win/loss, queue, timestamps, progression LP/rang, détection DuoQ.
