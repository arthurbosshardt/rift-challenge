# Git, CI/CD et déploiement

## Git

Commits petits et ciblés, messages `feat:` / `fix:` / `test:` / `chore:`. Ne pas mélanger un refactor hors-sujet avec une feature.

Branches : `feature/…`, `fix/…`, `chore/…`. `main` doit rester déployable.

## CI (GitHub Actions — `.github/workflows/ci.yml`)

Sur push/PR vers `main` :
- **backend** : compile + tests (`mvn -f backend/pom.xml -B verify`), Java 21 (Temurin).
- **frontend** : install (`npm ci`), tests unitaires (`vitest run`), **build prod** (`npm run build`) — le build échoue si un budget (bundle initial, style de composant) dépasse le seuil `error` défini dans `angular.json`.

CI ne nécessite aucun secret de production.

## Keep-alive (`cloudflare/keep-alive-worker`)

Ping périodique (5 min) de `/actuator/health` sur le backend Render pour limiter le cold start (offre gratuite Render : spin-down après inactivité), via un Cloudflare Worker avec Cron Trigger. Voir `cloudflare/keep-alive-worker/README.md` pour le setup et le déploiement. Remplace l'ancien workflow GitHub Actions, dont le scheduler pouvait sauter des exécutions sous charge.

## Déploiement

| Composant | Hébergeur | Détails |
|---|---|---|
| Frontend | Vercel | SPA statique (`dist/frontend/browser`), pas de SSR |
| Backend API | Render | Docker, plan gratuit → cold start 2-3 min |
| Base de données | Supabase | PostgreSQL managé, session pooler pour Flyway/Hikari |

CORS prod : `https://rift-challenge.com` et `https://www.rift-challenge.com` (variable `CORS_ORIGINS` côté Render, jamais en dur dans le code).

Guides détaillés (opérationnels, humains) : [`docs/DEPLOY.md`](../docs/DEPLOY.md), [`docs/GOOGLE_OAUTH.md`](../docs/GOOGLE_OAUTH.md).

## Environnements

Config locale/dev/prod séparée. Jamais de secret de prod commité — `.env` local est gitignoré, `frontend/src/environments/environment.prod.ts` versionné reste un placeholder (les vraies valeurs sont injectées au build Vercel par `scripts/write-production-env.mjs`).
