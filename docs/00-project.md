# Rift Challenge — règles projet

Document de reprise pour quiconque (humain ou assistant IA) débarque sur ce repo. Lis ce fichier et le reste de `docs/` **avant** d'ajouter une page, un composant, un endpoint ou une règle métier. Réutilise l'existant — n'invente pas un nouveau pattern quand un équivalent existe déjà.

Fichiers du dossier :

| Fichier | Contenu |
|---|---|
| `00-project.md` | Ce fichier — produit, règles métier, principes généraux |
| `10-backend.md` | Spring Boot : architecture, API, throttling |
| `20-frontend.md` | Angular : architecture, design system |
| `30-database.md` | PostgreSQL / Flyway |
| `40-riot-api.md` | Intégration Riot Games |
| `50-testing.md` | Stratégie de tests |
| `60-git-and-delivery.md` | Git, CI/CD, déploiement |
| `DEPLOY.md` | Guide opérationnel déploiement (humains) |
| `GOOGLE_OAUTH.md` | Guide opérationnel Google OAuth (humains) |

Guides opérationnels complémentaires (humains) : [`DEPLOY.md`](./DEPLOY.md), [`GOOGLE_OAUTH.md`](./GOOGLE_OAUTH.md).

---

## 1. Le produit

Rift Challenge permet à des joueurs League of Legends de créer un **challenge** sur une période donnée, d'y inscrire des comptes Riot, puis de comparer qui a le plus progressé (LP / rang / V-D) pendant cette fenêtre. Un **leaderboard global** (tous challenges confondus) complète l'expérience.

- **Repo** : `rift-race` (nom historique du dépôt). Le produit s'appelle **Rift Challenge** partout ailleurs (UI, domaine, packages backend `com.riftchallenge`). L'ancien terme *race* survit dans des redirections d'URL, des migrations SQL anciennes (`V1`–`V13`), et quelques clés `localStorage` de compatibilité (`riftrace.theme`, etc. — ne pas les retirer, elles migrent les anciens utilisateurs).
- **Prod** : https://rift-challenge.com
- **Langue UI** : français par défaut si le navigateur est `fr*`, sinon anglais. Toute chaîne visible passe par i18n (voir `20-frontend.md`).

Inspiration assumée (landing) : SoloQ KR, Iron to Challenger, défis DuoQ streamers, soloqchallenge.fr.

### Ce que l'utilisateur peut faire

Compte applicatif (email + mot de passe **ou** Google, via Supabase) :

- créer / éditer / supprimer **ses** challenges (il en est **owner**) ;
- ajouter / retirer des participants (SOLOQ) ou des duos (DUOQ) ;
- choisir nom, dates de début et de fin, région ;
- lier **un compte Riot** (le support multi-comptes/smurfs a existé puis a été retiré, voir `V26__user_riot_account_drop_smurfs.sql`) ;
- voir les challenges (liste globale), ceux qu'il a créés, ceux auxquels il participe (`/my-participations`), et le **classement global** ;
- consulter le **profil public** de n'importe quel joueur (`/players/{riotId}`) : activité récente (matchs Ranked Solo/Duo, refresh manuel), et challenges auxquels ce joueur participe — que le joueur soit ou non l'utilisateur connecté ;
- rafraîchir manuellement les données Riot d'un challenge (**1 fois / 2 min**, imposé côté backend) ou l'activité d'un profil joueur (throttle dédié, voir `10-backend.md`) ;
- partager un challenge via une URL stable (`/challenges/{shareSlug}`, `shareSlug` = UUID du challenge).

Une fois connecté (et compte Riot lié), l'écran d'accueil par défaut redirige vers **son propre profil joueur** (`/players/{riotId}`) plutôt que vers un tableau de bord dédié — voir `myChallengesRedirectGuard` (`/my-challenges` est une URL legacy conservée pour les anciens liens, qui redirige vers ce même profil).

Les visiteurs non connectés voient la landing, la liste des challenges, le leaderboard global, le détail d'un challenge via son lien de partage, et n'importe quel profil joueur — tout est consultable publiquement, y compris sans compte.

### Ce que le produit n'est pas

- Pas un client Riot, pas un tracker live type Porofessor.
- Les classements **ne sont pas temps réel**. Le refresh par challenge (ou par profil joueur) est manuel et throttlé ; le leaderboard global est recalculé 4×/jour (voir `10-backend.md`).
- Pas affilié à Riot Games (disclaimer légal dans le footer).
- Un ancien **tableau de bord** (« mes games récentes », modal 5v5, endpoint `GET /api/challenges/recent`) a existé et reste dans le code mais n'est plus dans la navigation ni le parcours par défaut : il a été remplacé par le profil joueur public (`/players/{riotId}`), jugé plus convaincant. Ne pas réactiver l'ancien tableau de bord sans demande explicite.

---

## 2. Vocabulaire métier

| Terme | Sens |
|---|---|
| **Challenge** | Un défi avec nom unique, type, owner, `startAt`, `endAt`, région. |
| **Owner** | Créateur. Seul lui modifie le challenge, ajoute/retire des joueurs. |
| **SOLOQ** | Jusqu'à **16 joueurs** individuels. |
| **DUOQ** | Jusqu'à **8 duos** = 16 joueurs. Un duo = exactement 2 joueurs. |
| **Participant** | Un compte Riot (PUUID) inscrit dans un challenge. |
| **Riot ID** | `gameName#tagLine` (affichage). L'identité stable est le **PUUID**. |
| **Compte Riot lié** | Un seul compte Riot par utilisateur app (`UNIQUE (user_id)`). Le multi-comptes ("principal + smurfs") a existé puis a été retiré — ne pas le réintroduire sans demande explicite. |
| **LP gained** | Delta de LP **pendant la fenêtre du challenge**, pas le LP actuel tout court. |
| **Rank estimated** | Rang/LP reconstruits à partir des matchs quand le live Riot n'est plus fiable (challenge fini, peu de games). Flag `rankEstimated`. |
| **Share slug** | Identifiant d'URL. **UUID du challenge** (stable si on renomme). |
| **Profil joueur** | Page publique `/players/{riotId}` : activité récente + challenges d'un joueur donné, connecté ou non, owner du profil ou non. |
| **Refresh** | Sync manuelle Riot → Postgres pour un challenge (ou l'activité d'un profil joueur). Cooldown **2 minutes** pour un challenge, appliqué de façon atomique côté backend. |
| **Leaderboard global** | Classement tous-challenges-confondus (saison + fenêtre glissante 7 jours), recalculé périodiquement et mis en cache. « Saison » = **année ranked Riot complète** (depuis le reset de LP de janvier), pas un split — Riot ne reset **pas** les LP entre les 3 saisons internes de l'année (S1/S2/S3), donc borner à un split sous-compterait l'activité des joueurs. |

Les anciennes routes `/races/...`, `/my-races`, `/public-races` redirigent vers `/challenges/...`.

---

## 3. Règles métier (à respecter absolument)

### 3.1 Types et limites

- Un challenge a **exactement un type** : `SOLOQ` ou `DUOQ`. On ne mixe pas.
- SOLOQ : max 16 participants.
- DUOQ : max 8 duos / 16 joueurs. Ce n'est **pas** une liste plate de 16 joueurs.
- Le nom de challenge est **unique** (ignore-case) en base.
- **Un seul** compte Riot / utilisateur applicatif (`UserRiotAccountService.linkAccount` refuse tout second lien). Le multi-comptes a existé (`V11`) puis a été retiré (`V26`) — ne pas le réintroduire sans demande explicite.

### 3.2 Cycle de vie

Statut calculé **côté serveur** (UTC), jamais uniquement côté frontend :

| Statut | Condition |
|---|---|
| `NOT_STARTED` | `now < startAt` |
| `ACTIVE` | `startAt ≤ now < endAt` |
| `FINISHED` | `now ≥ endAt` |

`endAt` est obligatoire et **strictement après** `startAt`.

Avant le début : afficher clairement que ça n'a pas commencé + date/heure + countdown ; **ne pas compter** les stats post-start (les matchs avant `startAt` ne comptent jamais).

### 3.3 Matchs éligibles

Seule la file **Ranked Solo/Duo**, queue Riot **`420`**, est synchronisée. Un match **avant** `startAt` (UTC) ou **à/après** `endAt` ne contribue pas.

**DUOQ** : une game ne compte pour le duo **que si les deux membres sont dans le même match éligible**. Si l'un a joué SoloQ sans l'autre pendant la fenêtre, le duo est **inéligible** (`eligible: false`, raison `SOLOQ_WITHOUT_PARTNER|<RiotId>`). Ne jamais faire confiance au client pour déclarer un match DuoQ valide. Détails : `DuoEligibilityService`.

### 3.4 Classement

- Par challenge — SOLOQ : tri principalement sur `rankScore` / LP gagnés. DUOQ : score **combiné** des deux joueurs ; duos inéligibles restent visibles mais grisés, en bas.
- LP / rang peuvent être **estimés** (`rankEstimated`) via replay des matchs (`RankReplayService`, `MatchLpEstimator`), surtout pour les défis terminés.
- Leaderboard global — catégories : rang (saison uniquement), win rate (floor de **20 games** en saison / **10 games** en fenêtre glissante 7 jours — le floor ne s'applique **qu'au** win rate, pas aux autres catégories), win streak, LP gagnés, games jouées. Recalculé par un scheduler (voir `10-backend.md`), pas à la demande.

### 3.5 Refresh Riot

- **1 refresh accepté / 2 min / challenge**, enforcé backend via une opération atomique (pas un simple check-puis-write — voir `10-backend.md`). Le bouton UI n'est que du UX.
- Throttle IP supplémentaire ~5 s contre le spam, plus un throttle par utilisateur (~2 s) sur la création de challenge/participant/duo pour protéger le quota Riot partagé.
- Le frontend affiche : disponible / cooldown / prochaine heure / « pas temps réel ».
- Modifier nom / dates ne déclenche pas de refresh Riot (réponse metadata only).
- Sync incrémentale : max ~10 nouveaux matchs / refresh (ou plus en rattrapage). Ne pas re-télécharger tout l'historique.
- Page challenge = lecture **Postgres**, pas Riot à chaque vue.

### 3.6 Partage

- URL : `/challenges/{shareSlug}` avec `shareSlug = UUID` du challenge. Renommer **ne change pas** l'URL.
- Open Graph : HTML + image PNG générés backend (`ChallengeOpenGraphService`) pour Discord/Twitter/Slack — voir aussi `20-frontend.md` (référencement) pour la limite de ce mécanisme.

### 3.7 Auth et comptes Riot

- Auth : **Supabase** (JWT). Le backend valide le token en le vérifiant auprès de Supabase (`GET /auth/v1/user`) — il ne réimplémente pas la vérification cryptographique du JWT et ne stocke aucun mot de passe applicatif.
- Google OAuth via Supabase (voir `docs/GOOGLE_OAUTH.md`).
- Lier un Riot ID résout le PUUID via l'API Riot **uniquement côté backend**.
- Sans compte Riot lié : on peut quand même créer un challenge, mais « mes challenges rejoints » exige un lien.

---

## 4. Principes d'ingénierie

- Lire le code existant + `docs/` avant un gros changement. Plus petit diff cohérent.
- Ne pas sur-ingénierer : pas d'abstraction, de lib ou de config pour un besoin hypothétique.
- Pas de logique métier dans les composants Angular. Règles métier dans les services backend, jamais uniquement côté client.
- **Ne jamais faire confiance au frontend** : ownership, limites 16/8/10, cooldown, DuoQ — tout est revérifié côté serveur.
- DTOs entre API et persistance. Entités JPA **jamais** exposées directement.
- Migrations pour tout changement de schéma ; jamais de modification manuelle de la prod.
- Ne pas committer de secret (clé Riot, JWT, mot de passe DB, `.env`, `environment.prod.ts` avec de vraies valeurs).
- Ne pas rewriter l'app ou extraire un design system parallèle. Ne pas ajouter de lib « pour faire joli ».
- Pas de refactor hors-sujet pendant l'implémentation d'une feature.

## 5. Sécurité (rappel)

Ne jamais committer : mots de passe, hashs, clés API Riot, secrets OAuth, identifiants DB, secrets de signature JWT, fichiers d'environnement contenant de vraies valeurs.

Ne jamais exposer la clé Riot au frontend. Ne jamais logger un secret. Toute limite/quota (cooldown, throttle, limites de participants) doit être vérifiable côté serveur de façon atomique — un simple "lire puis écrire" laisse une fenêtre de course exploitable en concurrence.

## 6. Méthode de travail

Avant d'implémenter une feature significative :

1. Inspecter le code existant et les règles pertinentes dans `docs/`.
2. Expliquer brièvement le plan d'implémentation et les fichiers concernés.
3. Faire le plus petit changement cohérent.
4. Ajouter/mettre à jour les tests pour toute règle métier touchée.
5. Rapporter ce qui a changé et les hypothèses prises.

Ne pas réécrire l'application en profondeur sans demande explicite.

---

## 7. État connu / pièges

- **Dashboard legacy** : code + API `GET /api/challenges/recent` + modal 5v5 encore présents mais hors navigation et hors parcours par défaut (remplacés par le profil joueur `/players/{riotId}`). `/dashboard` et `/my-challenges` redirigent (voir `core/guards/home.guards.ts`) — vérifier ces guards avant d'y toucher.
- **Share slug** : UUID, jamais recalculé après un renommage.
- **Rangs estimés** : défis finis / peu de games ; plafonds dans `RankReplayService`.
- **Icônes** : Community Dragon / Data Dragon via constantes `ddragon-constants.ts` + proxy backend champions (jamais d'appel direct depuis le navigateur vers Riot/Data Dragon).
- **Node local** : versions impaires possibles ; le build Angular tourne quand même mais n'est pas garanti LTS.
- **PowerShell** : `mvn` n'est pas toujours dans le PATH (Maven sous `%LOCALAPPDATA%\apache-maven-3.9.9`).
