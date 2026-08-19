# Rift Challenge — contexte métier, fonctionnel et technique

Document de reprise pour une IA (ou un humain) qui débarque sur le repo.

- **Produit** : plateforme de challenges League of Legends.
- **Repo** : `rift-race` (nom historique). Le produit s’appelle **Rift Challenge**. L’ancien terme *race* survit dans des redirections d’URL et des migrations SQL anciennes.
- **Prod** : https://rift-challenge.com
- **Règles Cursor** (source de vérité complémentaire) : `.cursor/rules/`
- **Langue UI** : français par défaut si le navigateur est `fr*`, sinon anglais. Toute chaîne visible passe par i18n.

Lis ce fichier **avant** d’ajouter une page, un composant ou un écran. Réutilise le design system existant. N’invente pas une nouvelle palette, une nouvelle modale ou un nouveau pattern de bouton.

---

## 1. Ce que le produit fait

Rift Challenge permet à des joueurs LoL de créer un **challenge** sur une période, d’y inscrire des comptes Riot, puis de comparer qui a le plus progressé (LP / rang / V-D) pendant cette fenêtre.

Inspiration assumée (landing) : SoloQ KR, Iron to Challenger, défis DuoQ streamers, soloqchallenge.fr.

### Ce que l’utilisateur peut faire

Compte applicatif (email + mot de passe **ou** Google via Supabase) :

- créer / éditer / supprimer **ses** challenges (il en est **owner**) ;
- ajouter / retirer des participants (SOLOQ) ou des duos (DUOQ) ;
- choisir public / privé, nom, dates de début et de fin ;
- lier jusqu’à **10 comptes Riot** (1 principal + smurfs) ;
- voir les challenges publics, ceux qu’il a créés, ceux auxquels il participe ;
- rafraîchir manuellement les données Riot d’un challenge (**1 fois / 2 min**, imposé côté backend) ;
- partager un challenge via une URL stable.

Les visiteurs non connectés voient la landing, les challenges **publics**, et le détail d’un challenge via le lien de partage.

### Ce que le produit n’est pas

- Pas un client Riot, pas un tracker live type Porofessor.
- Les classements **ne sont pas temps réel**. Le refresh est manuel et throttlé.
- Pas affilié à Riot Games (disclaimer légal dans le footer).
- La page **« Mes games récentes »** (`/dashboard`) existe dans le code mais a été **retirée de la navigation** : le résultat Copilot n’a pas convaincu. Ne la réactive pas sans demande explicite. Attention : un utilisateur **connecté + compte Riot lié** qui tape `/` est encore redirigé vers `/dashboard` (`rootRedirectGuard`). C’est un reliquat.

---

## 2. Vocabulaire métier

| Terme | Sens |
|---|---|
| **Challenge** | Un défi avec nom unique, type, owner, `startAt`, `endAt`, visibilité. |
| **Owner** | Créateur. Seul lui modifie le challenge, ajoute/retire des joueurs. |
| **SOLOQ** | Jusqu’à **16 joueurs** individuels. |
| **DUOQ** | Jusqu’à **8 duos** = 16 joueurs. Un duo = exactement 2 joueurs. |
| **Participant** | Un compte Riot (PUUID) inscrit dans un challenge. |
| **Riot ID** | `gameName#tagLine` (affichage). L’identité stable est le **PUUID**. |
| **Compte principal / smurf** | Comptes Riot liés à l’utilisateur app. Il faut un principal avant les smurfs. |
| **LP gained** | Delta de LP **pendant la fenêtre du challenge**, pas le LP actuel tout court. |
| **Rank estimated** | Rang/LP reconstruits à partir des matchs quand le live Riot n’est plus fiable (challenge fini, peu de games, etc.). Flag `rankEstimated`. |
| **Share slug** | Identifiant d’URL. **UUID du challenge** (stable si on renomme). |
| **Refresh** | Sync manuelle Riot → Postgres pour un challenge. Cooldown **2 minutes**. |

Les anciennes routes `/races/...`, `/my-races`, `/public-races` redirigent vers `/challenges/...`.

---

## 3. Règles métier (à respecter absolument)

### 3.1 Types et limites

- Un challenge a **exactement un type** : `SOLOQ` ou `DUOQ`. On ne mixe pas.
- SOLOQ : max 16 participants.
- DUOQ : max 8 duos / 16 joueurs. Ce n’est **pas** une liste plate de 16 joueurs.
- Le nom de challenge est **unique** (ignore-case) en base.

### 3.2 Cycle de vie

Statut calculé **côté serveur** (UTC), jamais uniquement dans Angular :

| Statut | Condition |
|---|---|
| `NOT_STARTED` | `now < startAt` |
| `ACTIVE` | `startAt ≤ now < endAt` |
| `FINISHED` | `now ≥ endAt` |

`endAt` est obligatoire et **strictement après** `startAt`.

Avant le début :

- afficher clairement que ça n’a pas commencé + date/heure + countdown ;
- **ne pas compter** les stats post-start (les matchs avant `startAt` ne comptent jamais).

Pendant / après :

- classement visuel (cartes / rangées), pas un tableau dense ;
- LP gagnés, rang, V/D, historique de matchs (icônes champions).

### 3.3 Matchs éligibles

Seule la file **Ranked Solo/Duo**, queue Riot **`420`**, est synchronisée.

Un match **avant** `startAt` (UTC) ne contribue pas.

**DUOQ** : une game ne compte pour le duo **que si les deux membres sont dans le même match éligible**. Si l’un a joué SoloQ sans l’autre pendant la fenêtre, le duo est **inéligible** (`eligible: false`, raison `SOLOQ_WITHOUT_PARTNER|<RiotId>`). L’UI grise la rangée et affiche un message. Ne jamais faire confiance au client pour déclarer un match DuoQ valide.

Fichier : `backend/.../DuoEligibilityService.java`.

### 3.4 Classement

- SOLOQ : tri principalement sur `rankScore` / LP gagnés (voir `leaderboard-sort` côté front, calcul côté back).
- DUOQ : score **combiné** des deux joueurs ; duos inéligibles restent visibles mais grisés, en bas.
- LP / rang peuvent être **estimés** (`rankEstimated`) via replay des matchs (`RankReplayService`, `MatchLpEstimator`) surtout pour les défis terminés.

### 3.5 Refresh Riot

- **1 refresh accepté / 2 min / challenge** (`ChallengeSyncService.REFRESH_COOLDOWN`).
- Enforcé backend. Le bouton UI n’est que du UX.
- Throttle IP supplémentaire ~5 s (`ChallengeRefreshRequestThrottle`) contre le spam.
- Le front affiche : disponible / cooldown / prochaine heure / « pas temps réel ».
- Modifier nom / dates / visibilité **ne déclenche pas** de refresh Riot (réponse metadata only).
- Sync incrémentale : max ~10 nouveaux matchs / refresh, ~25 en rattrapage. Ne pas re-télécharger tout l’historique.
- Page challenge = lecture **Postgres**, pas Riot à chaque vue.

### 3.6 Partage

- URL : `/challenges/{shareSlug}` avec `shareSlug = UUID` du challenge.
- Renommer **ne change pas** l’URL (migration `V19`).
- Les anciens slugs basés sur le nom cassent après `V19`.
- Open Graph : HTML + image PNG générés backend (`ChallengeOpenGraphService`) pour Discord/Twitter.

### 3.7 Auth et comptes Riot

- Auth : **Supabase** (JWT). Le backend valide le token, ne stocke pas le mot de passe applicatif en clair (Supabase gère le hash).
- Google OAuth via Supabase (voir `docs/GOOGLE_OAUTH.md`).
- Lier un Riot ID résout le PUUID via l’API Riot **uniquement côté backend**.
- Max **10** comptes Riot / user. Un **principal** obligatoire avant les smurfs. Pas de doublon PUUID.
- Sans compte Riot lié : on peut quand même créer un challenge, mais « mes challenges rejoints » / dashboard exigent un lien.

---

## 4. Parcours UI (état actuel)

| Route | Qui | Rôle |
|---|---|---|
| `/` | Guest → `/home`. Auth + Riot lié → **`/dashboard` (reliquat)**. Auth sans Riot → `/public-challenges` | Redirect |
| `/home` | Tous | Landing marketing |
| `/public-challenges` | Tous | Liste + recherche nom / invocateur / type |
| `/my-challenges` | Auth + Riot lié | Challenges rejoints |
| `/challenges/:shareSlug` | Tous (privé = owner ou 404) | Détail / classement |
| `/challenges/new` | Auth | Ouvre la modale de création |
| `/settings` | Auth | Page qui ouvre la modale settings |
| `/dashboard` | Auth + Riot lié | Games récentes — **cachée dans la nav** |
| `/login`, `/auth/callback`, `/auth/reset-password` | Auth flow | |

Nav principale (`page-shell`) : Challenges publics, Mes challenges (si lié), bouton créer, menu user (challenges créés, settings, logout). Lien dashboard **commenté** (`TODO: Uncomment when dashboard is ready`).

Les **modales sont globales** : montées dans `app.ts` (login, logout, create, edit, delete, settings, created-challenges, game-detail). Ne pas recréer un overlay local si une modale globale existe.

---

## 5. Architecture technique

```
Angular (Vercel)  →  Spring Boot (Render)  →  PostgreSQL (Supabase)
                         ↓
                    Riot API (jamais le navigateur)
```

- Front : Angular **standalone**, TypeScript strict, HttpClient, Router, Vitest.
- Back : Java **21**, Spring Boot **3.3**, Maven, JPA, Flyway, Bean Validation.
- Secrets : `.env` local gitignoré ; variables d’environnement en prod. **Jamais** de clé Riot / JWT / DB dans Git.
- `frontend/src/environments/environment.prod.ts` du repo = placeholders. Le build Vercel le génère (`scripts/write-production-env.mjs`). Ne pas committer les vraies clés.

Packages backend (orientés feature, pas layers globaux) :

- `authentication/` — JWT Supabase, `/api/auth/me`
- `account/` — AppUser, comptes Riot liés
- `challenge/` — CRUD, progress, duos, OG, refresh throttle
- `riot/` — clients HTTP Riot, parse Riot ID, icônes, rank score, replay LP
- `synchronization/` — matchs, snapshots de rang, backfill champion
- `summoner/` — recherche / resolve invocateur
- `config/`, `health/`

Controllers **minces**. Règles dans les services. Entités JPA **jamais** exposées : DTOs.

Front :

- `core/` — auth, i18n, models, API services, guards, utils, theme
- `shared/` — layout, cartes, leaderboard bits, tooltips, skeletons
- `features/auth|home|challenge|settings/`

**Pas de logique métier dans les composants Angular.** Utils purs dans `core/utils/`. HTTP uniquement dans les services.

---

## 6. API (repères)

Base : `/api`

Challenges (`ChallengeController`) :

- `GET /api/challenges/public?challengeName=&summoner=&type=`
- `GET /api/challenges/owned` · `/participating` · `/mine` (mine = owned)
- `GET /api/challenges/share/{shareSlug}`
- `POST /api/challenges`
- `PATCH /api/challenges/{id}` (nom + dates + visibilité ensemble)
- `PATCH .../schedule|start|end|visibility|name`
- `DELETE /api/challenges/{id}`
- `POST /api/challenges/{id}/refresh`
- `POST/DELETE .../participants` · `POST/DELETE .../duos`
- `GET /api/challenges/recent` (dashboard, auth)

Autres :

- `GET /api/auth/me`
- CRUD comptes Riot : `UserRiotAccountController`
- `GET /api/summoners/search` · `/resolve`
- Icônes champion : `/api/champion-icons/{id}.png` (proxy backend)
- OG : `/api/challenge-preview/{slug}` et image PNG
- Health : `/actuator/health`

Auth : Bearer JWT Supabase. Le front proxyfie en local (`proxy.conf.json`). En prod, `API_BASE_URL` pointe vers Render.

Validation : **toujours** backend. Le front valide pour l’UX seulement.

---

## 7. Données (Postgres + Flyway)

Migrations : `backend/src/main/resources/db/migration/V1__…` → `V19__…`

- V1–V13 parlent encore de `race` ; **V14** renomme en `challenge`.
- V17 : `share_slug` en VARCHAR (expérience nom).
- V18/V19 : `share_slug` = UUID du challenge.

Concepts persistés :

- `app_user`
- `user_riot_account` (PUUID, principal/smurf, icône)
- `challenge` (owner, name unique, type, start/end UTC, public, share_slug, data_synced_at)
- `challenge_participant`
- `challenge_duo` (2 participants)
- `riot_match` + `challenge_participant_match` (win, champion, timestamps)
- `rank_snapshot` (type REFRESH, flag estimated)
- `challenge_refresh` (dernier refresh accepté)

Timestamps **UTC** partout. Conversion locale **uniquement à l’affichage**.

Ne jamais modifier le schéma prod à la main : nouvelle migration uniquement.

---

## 8. Front : i18n, thème, dates

- Fichier unique : `frontend/src/app/core/i18n/translations.ts`
- Pipe : `{{ 'nav.login' | t }}` · params : `{{ 'footer.lastUpdated' | t: { date } }}` (placeholders `{name}` **simples**, pas `{{name}}` pour interpolate)
- FR et EN doivent avoir **les mêmes clés** (test `translations.spec.ts`).
- Thème : `ThemeService` pose `data-theme="dark|light"` sur le document. Variables CSS dans `styles.scss`.
- Dates challenges : `core/utils/challenge-date.ts` + pipe `challenge-date`.
- Footer : `LAST_UPDATED_AT` dans `core/version.ts` (`YYYY-MM-DDTHH:mm`). Texte « Dernière mise à jour le : JJ/MM/AAAA à HHhmm ». **À bump à chaque release visible.**

---

## 9. Design system — réutilisation du style

**C’est la partie la plus importante pour ne pas casser l’identité visuelle.**

### 9.1 Source de vérité CSS

1. `frontend/src/styles.scss` — tokens, reset, boutons/inputs globaux, **modales**, utilitaires.
2. `frontend/src/app/features/challenge/_challenge-form-shared.scss` — formulaires create/edit (importé en tête de `styles.scss`).
3. Composant SCSS **local** uniquement pour le layout spécifique.

Ne **duplique pas** `#c5a059` ou des fonds custom. Utilise les variables.

### 9.2 Tokens (extrait)

Toujours via `var(--…)` :

| Token | Usage |
|---|---|
| `--gold`, `--gold-bright`, `--gold-dim` | Accent marque |
| `--gold-a06` … `--gold-a35` | Fonds / hover / borders teintés |
| `--bg`, `--bg-deep`, `--app-background` | Page |
| `--surface`, `--surface-elevated`, `--surface-hover` | Cartes, panneaux |
| `--text`, `--text-muted`, `--text-subtle` | Hiérarchie texte |
| `--border`, `--border-strong` | Contours (déjà teintés or) |
| `--danger`, `--success` | Erreurs / positif (LP+, victoire) |
| `--radius-sm/md/lg/pill` | 8 / 12 / 16 / pill |
| `--shadow-card` | Cartes |
| `--z-tooltip` | **2400** — tooltips au-dessus de tout |
| `--font-family` | Plus Jakarta Sans |
| `--font-brand` | LEMON MILK (titres marque uniquement) |

Dark par défaut. Light = `[data-theme='light']` (or plus sombre, fonds clairs). Tester les deux si tu touches une couleur.

LP+ / victoire : souvent `#7ddf8a` en plus de `--success` (déjà dans le détail challenge). Si tu ajoutes du vert, reste dans cette famille.

### 9.3 Typographie et forme

- Corps : Plus Jakarta Sans.
- Titres de marque / logo : LEMON MILK (`--font-brand`), letter-spacing un peu large.
- Boutons globaux : **pill**, fond `--gold-a12`, hover `--gold` border + `--gold-a18`.
- Inputs : radius `--radius-sm`, focus ring `0 0 0 2px var(--gold-a12)`.
- Pas de Material / Bootstrap / Tailwind. Pas de nouvelle lib UI.

### 9.4 Layout

- Toute page authentifiée / liste / détail passe par **`app-page-shell`** (header, nav, footer, menu user).
- Header flottant possible (`floatingNav`).
- Breakpoints historiques :
  - **1024px** : nav desktop vs menu mobile, footer, overlap titres.
  - **899px** : leaderboard détail challenge (stats à droite, historique swipe).
- Mobile : éviter le scroll horizontal sur l’expérience principale. L’historique de matchs **peut** swiper en X (strip). En DUOQ, les deux rangées de champions **scrollent ensemble**.
- Budget Angular prod : `anyComponentStyle` **warning 8kB / error 16kB**. Le détail challenge est déjà ~15 kB. N’empile pas du SCSS dans ce fichier.

### 9.5 Composants à réutiliser (ne pas recréer)

| Besoin | Composant |
|---|---|
| Cadre de page | `page-shell` |
| Carte challenge (liste) | `challenge-card` + `challenge-card-skeleton` |
| Badge SOLOQ/DUOQ | `challenge-badge` |
| Joueur (icône + nom + rang) | `player-identity` + `player-avatar` + `rank-emblem` |
| Historique champions | `match-history-strip` |
| Typeahead Riot ID | `summoner-typeahead` |
| Logo | `brand-logo` |
| Icônes nav | `nav-icon` (ajouter un `name` plutôt qu’un SVG ad hoc) |
| Loader / skeletons | `loader`, `skeleton`, `leaderboard-skeleton`, `challenge-list-skeleton` |
| Footer | `site-footer` |
| Tooltip clampé écran | directive `appClampTooltip` + enfant `[role="tooltip"]` |
| Modale détail game | `game-detail-modal` + service (déjà globaux) |

### 9.6 Pattern modale (obligatoire)

HTML :

```html
<div class="modal-overlay" role="dialog" aria-modal="true" (click)="close()">
  <section class="modal-panel" (click)="$event.stopPropagation()">
    …
  </section>
</div>
```

Classes déjà globales : `modal-overlay`, `modal-panel`, `modal-panel--wide|--settings|--edit|--create`, `modal-saving-overlay`.

Service `signal` `isOpen()` + composant monté dans `app.ts`. Overlay clique = fermer. Stop propagation sur le panneau.

### 9.7 Formulaires challenge

Réutiliser les classes de `_challenge-form-shared.scss` :

- `.challenge-form`, `.challenge-form__schedule-block`
- `.field-label`, `.field-label__required` (or), `.field-label__optional`
- `.challenge-form__datetime-row` (date + heure)

Create et edit partagent ce SCSS. Si tu ajoutes un champ, étends ce fichier plutôt que du CSS one-off.

### 9.8 Tooltips

- Position `absolute`, `z-index: var(--z-tooltip)`.
- Toujours `role="tooltip"`.
- Sur le host : `appClampTooltip` pour ne pas sortir de l’écran (surtout mobile).
- Overflow parent souvent `visible` sur mobile (bug connu du tooltip refresh « i »).

### 9.9 Liste / cartes

- Fond carte : `rgba(255,255,255,0.02)` + `border: 1px solid var(--border)` + radius 12px — déjà le langage des leaderboards et listes participants.
- Podium : teintes gold / silver / bronze en gradient léger (voir `challenge-detail-page`).
- Inéligible : `opacity` + `grayscale`, **sans** casser la grille (`grid-column: auto` avait collé l’identité à 0px — ne pas rejouer ça).
- LP+ vert, LP− `--danger`.

### 9.10 Accessibilité

- Vrais `<button>` (pas des div cliquables).
- Labels sur les inputs. Ne pas empiler `aria-label` redondant si un `<label>` existe déjà.
- Ne pas communiquer un état **uniquement** par la couleur (victoire = icône + classe + texte).

### 9.11 Naming CSS

BEM léger : `block__element--modifier` (`leaderboard__item--duo`, `site-footer__updated`). Cohérent avec le reste. Pas de modules CSS, pas de styled-components.

---

## 10. Fichiers « commence ici »

| Sujet | Fichiers |
|---|---|
| Règles produit | `.cursor/rules/00-project.mdc` |
| Routes | `frontend/src/app/app.routes.ts`, `core/guards/home.guards.ts` |
| Modèles TS | `frontend/src/app/core/models/challenge.models.ts` |
| API front | `core/services/challenge-api.service.ts` |
| i18n | `core/i18n/translations.ts` |
| Tokens CSS | `frontend/src/styles.scss` |
| Shell | `shared/components/page-shell/` |
| Détail challenge | `features/challenge/challenge-detail-page.component.*` |
| Auth | `core/services/auth.service.ts`, `authentication/` backend |
| Sync Riot | `synchronization/ChallengeSyncService.java`, `ChallengeParticipantSyncService.java` |
| Déploiement | `docs/DEPLOY.md`, `docs/GOOGLE_OAUTH.md` |

---

## 11. Tests et livraison

- Backend : JUnit + Mockito. **Mock Riot**. Pas d’appels réseau dans les unit tests.
- Front : Vitest via `ng test` (`jsdom`). Pas Karma.
- Priorités tests : ownership, limites 16/8, dates UTC, cooldown 2 min, DuoQ ensemble vs solo, dédup matchs, i18n keys sync.
- CI : compile + tests back, install + tests + **build prod** front. Le build prod échoue si un SCSS composant > 16 kB.
- Branches : `feature/…`, `fix/…`. `main` déployable.
- Commits petits, messages `feat:` / `fix:` / `test:` / `chore:`.
- Render (API Docker) + Vercel (SPA `dist/frontend/browser`) + Supabase (Postgres **session pooler** port 5432 pour Flyway).
- CORS prod : `https://rift-challenge.com` et `www`.
- Hébergement gratuit : cold start Render ~2–3 min. La landing affiche un message « warming » tant que `/actuator/health` ne répond pas (`BackendStatusService`). Ne pas retirer sans raison.

---

## 12. Contraintes pour une IA qui code ici

**Faire :**

- Lire le code existant + ce doc + `.cursor/rules/` avant un gros changement.
- Plus petit diff cohérent. Tests si règle métier.
- Réutiliser `page-shell`, tokens, modales globales, `t` pipe, DTOs.
- UTC en base, locale à l’affichage.
- Valider côté backend.

**Ne pas faire :**

- Exposer la clé Riot au front.
- Contourner le cooldown 2 min côté client.
- Compter des matchs hors queue 420 ou hors fenêtre.
- Faire confiance au front pour DuoQ / owner / refresh.
- Ajouter une lib « pour faire joli ».
- Rewriter l’app ou extraire un design system parallèle.
- Committer `.env`, `environment.prod.ts` avec de vraies clés.
- Réactiver « Games récentes » dans la nav sans demande.
- Modifier une migration Flyway déjà appliquée en prod : en ajouter une nouvelle.

---

## 13. État connu / pièges

- **Dashboard** : code + API `/recent` + modal 5v5 présents ; nav masquée ; `/` redirige encore vers `/dashboard` si Riot lié.
- **Share slug** : UUID. Création = `challenge.id.toString()`. `updateName` ne touche pas le slug.
- **Édition metadata** : `toMetadataDetailResponse` (pas de reload participants) pour rester rapide.
- **Rangs estimés** : défis finis / peu de games ; plafonds dans `RankReplayService`.
- **Icônes** : Community Dragon / Data Dragon via constantes `ddragon-constants.ts` + proxy backend champions.
- **README racine** : un peu daté (`/` → my-challenges). Ce fichier fait foi pour les redirects actuels.
- **Node local** : versions impaires possibles ; le build Angular 22 tourne quand même.
- **PowerShell** : `mvn` n’est pas toujours dans le PATH (Maven sous `%LOCALAPPDATA%\apache-maven-3.9.9`).

---

## 14. Checklist ultra-courte avant un écran UI

1. Est-ce que `page-shell` + une modale globale suffisent ?
2. Est-ce que les couleurs/spacing viennent des tokens ?
3. Chaînes dans `translations.ts` (fr **et** en) ?
4. Mobile 1024 / 899 vérifiés, tooltips `appClampTooltip` ?
5. Règle métier encore côté backend ?
6. Budget SCSS composant < 16 kB ?
)
