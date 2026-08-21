# Frontend — Angular

Applies to: `frontend/**`. Voir `00-project.md` pour les règles métier.

## Stack

Angular **22**, standalone components, TypeScript strict, Signals, Angular Router, HttpClient, Vitest via `ng test` (jsdom, pas Karma). Pas de lib UI (pas de Material/Bootstrap/Tailwind) et pas de nouvelle lib « pour faire joli ». Utiliser la version Angular déjà en place — ne pas l'upgrader sans raison explicite.

## Architecture

```
core/       — auth, i18n, models, services API, guards, interceptors, utils, theme, seo
shared/     — layout, cartes, leaderboard, tooltips, skeletons — composants réutilisables
features/   — auth/, home/, challenge/, settings/
```

Pas de logique métier dans les composants. Utils purs dans `core/utils/`. HTTP uniquement dans les services (`core/services/*-api.service.ts`).

## Change detection et état

- **`ChangeDetectionStrategy.OnPush`** sur tout nouveau composant. L'état de l'application est géré en Signals (`signal`/`computed`) quasi partout, ce qui rend OnPush gratuit — pas besoin de `markForCheck()` manuel, un signal lu dans le template redéclenche le check tout seul même sous OnPush.
- RxJS reste utilisé pour les flux HTTP/événementiels (recherche typeahead, interceptors) ; les résultats sont pausés dans des signals via `.subscribe()`.
- Toujours nettoyer `setInterval`/`setTimeout`/listeners globaux dans `ngOnDestroy`.

## Routing

Routes chargées en **lazy** (`loadComponent`) pour toutes les pages, pas seulement les moins visitées — garde le bundle initial petit. Un nouveau écran routé suit ce pattern par défaut.

## HTTP

- `auth.interceptor.ts` attache le token Supabase.
- `retry.interceptor.ts` retente automatiquement les **GET** en échec réseau/gateway transitoire (status 0, 502/503/504), jamais les mutations (POST/PATCH/DELETE — un retry y créerait un doublon).
- Debounce sur les champs de recherche (`summoner-typeahead`).

## i18n, thème, dates

- Fichier unique : `core/i18n/translations.ts`. Pipe `{{ 'nav.login' | t }}`. FR et EN doivent avoir **les mêmes clés** (`translations.spec.ts` le vérifie — ne jamais ajouter une clé dans une langue sans l'autre).
- `ThemeService` pose `data-theme="dark|light"` sur le document. Variables CSS dans `styles.scss`. Dark par défaut.
- Dates challenges : `core/utils/challenge-date.ts` + pipe `challenge-date`. UTC en base, conversion locale **uniquement à l'affichage**.

## Référencement (SEO)

- `SeoService` + `AppTitleStrategy` mettent à jour title/description/canonical par route (voir `data.seo` dans `app.routes.ts`) — toute nouvelle route doit déclarer ses métadonnées SEO, pas de valeur générique par défaut.
- La page de détail challenge (`/challenges/:shareSlug`) est servie différemment aux bots connus (Discord/Twitter/Googlebot…) via un rewrite Vercel vers une page statique backend, pour compenser l'absence de SSR côté Angular. C'est un compromis assumé pour l'instant, pas un pattern à reproduire ailleurs sans réflexion — il expose à un risque de « cloaking » si le contenu diverge trop entre bot et visiteur humain.
- Sitemap : généré dynamiquement par le backend (`GET /api/sitemap.xml`, rewrite Vercel sur `/sitemap.xml`). Ne pas recréer de `sitemap.xml` statique dans `public/` — il primerait sur le rewrite et masquerait les URLs de challenges.
- Un seul `<h1>` par page. Ne pas dupliquer un composant qui rend déjà un `<h1>` sur une page qui en a déjà un.

## Design system

### Source de vérité CSS

1. `styles.scss` — tokens, reset, boutons/inputs globaux, modales, utilitaires.
2. `features/challenge/_challenge-form-shared.scss` — formulaires create/edit.
3. SCSS de composant local uniquement pour le layout spécifique.

Ne pas dupliquer une couleur en dur (ex. `#c5a059`) — toujours `var(--…)`.

### Tokens (extrait)

| Token | Usage |
|---|---|
| `--gold`, `--gold-bright`, `--gold-dim`, `--gold-a06`…`--gold-a35` | Accent marque, fonds/hover/borders teintés |
| `--bg`, `--bg-deep`, `--app-background` | Page |
| `--surface`, `--surface-elevated`, `--surface-hover` | Cartes, panneaux |
| `--text`, `--text-muted`, `--text-subtle` | Hiérarchie texte |
| `--border`, `--border-strong` | Contours |
| `--danger`, `--success` | Erreurs / positif (LP+, victoire) |
| `--radius-sm/md/lg/pill` | 8 / 12 / 16 / pill |
| `--z-tooltip` | 2400 — tooltips au-dessus de tout |
| `--font-family` | Plus Jakarta Sans (corps) |
| `--font-brand` | LEMON MILK (titres marque uniquement) |

Tester dark **et** light si tu touches une couleur.

### Composants à réutiliser (ne pas recréer)

| Besoin | Composant |
|---|---|
| Cadre de page | `page-shell` |
| Carte challenge | `challenge-card` + `challenge-card-skeleton` |
| Badge SOLOQ/DUOQ | `challenge-badge` |
| Joueur | `player-identity` + `player-avatar` + `rank-emblem` |
| Historique champions | `match-history-strip` |
| Typeahead Riot ID | `summoner-typeahead` |
| Loader / skeletons | `loader`, `skeleton`, `leaderboard-skeleton`, `challenge-list-skeleton` |
| Footer | `site-footer` |
| Tooltip clampé écran | directive `appClampTooltip` |
| Modale détail game | `game-detail-modal` + service (global) |

### Pattern modale (obligatoire)

```html
<div class="modal-overlay" role="dialog" aria-modal="true" (click)="close()">
  <section class="modal-panel" (click)="$event.stopPropagation()">…</section>
</div>
```

Classes globales : `modal-overlay`, `modal-panel`, `modal-panel--wide|--settings|--edit|--create`, `modal-saving-overlay`. Service avec un signal `isOpen()`, composant monté globalement dans `app.ts`. Overlay clique = fermer, stop propagation sur le panneau. Ne pas recréer un overlay local si une modale globale existe déjà.

### Formulaires challenge

Réutiliser `_challenge-form-shared.scss` (`.challenge-form`, `.field-label`, `.challenge-form__datetime-row`). Étendre ce fichier plutôt que d'ajouter du CSS one-off pour un nouveau champ.

### Accessibilité

Vrais `<button>`, jamais de div cliquable. Labels sur tous les inputs. Ne jamais communiquer un état uniquement par la couleur (victoire = icône + classe + texte). Tout élément cliquable qui change l'état de la page (expand/collapse, toggle) doit être activable au clavier, pas seulement au clic souris.

### Layout et responsive

Toute page authentifiée/liste/détail passe par `app-page-shell`. Breakpoints historiques : **1024px** (nav desktop vs mobile), **899px** (leaderboard détail challenge). Éviter le scroll horizontal sur l'expérience principale. Budget build prod : `anyComponentStyle` warning **16 kB** / error **24 kB** par composant (voir `angular.json`) — un composant qui s'en approche doit factoriser vers les tokens globaux plutôt qu'empiler du SCSS local.

### Naming CSS

BEM léger : `block__element--modifier`. Pas de modules CSS, pas de styled-components.
