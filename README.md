# Rift Challenge

Plateforme de **challenges** League of Legends (Angular + Spring Boot + PostgreSQL). Les joueurs créent un défi sur une période donnée,s'inscrit avec son compte RIOT, et comparent la progression (LP / rang / V-D) avec ses amis. Un leaderboard global sur la saison et les 7 derniers jours est également disponible pour se comparer aux autres joueurs du monde.

**Prod** : [https://rift-challenge.com](https://rift-challenge.com)

## Structure

- `frontend/` — Angular
- `backend/` — Spring Boot
- `rules/` — règles projet mutualisées (produit, backend, frontend, base de données, API Riot, tests, delivery) — **à lire avant tout changement significatif**, humain ou IA

## Versions

| Composant | Version |
|---|---|
| Java | 21 (Temurin) |
| Spring Boot | 3.3.7 |
| Maven | 3.9.9 |
| Angular | 22.1.x |
| TypeScript | 6.0.x |
| Node.js | 22 LTS (utilisé en CI — une version locale plus récente/impaire fonctionne généralement aussi) |
| Vitest | 4.x (via `ng test`, pas Karma) |
| PostgreSQL | 17 en local / géré par Supabase en prod |

## Prérequis locaux

Installés via winget sur cette machine :
- Java 21 (Temurin)
- PostgreSQL 17
- Maven 3.9.9 → `%LOCALAPPDATA%\apache-maven-3.9.9`

## Base de données

PostgreSQL local :
- superuser : `postgres` / `postgres`
- app : `riftchallenge` / `riftchallenge` / base `riftchallenge`

Création (déjà faite si vous suivez l'option A) :

```powershell
$env:PGPASSWORD='postgres'
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -c "CREATE ROLE riftchallenge LOGIN PASSWORD 'riftchallenge';"
& "C:\Program Files\PostgreSQL\17\bin\createdb.exe" -U postgres -h localhost -O riftchallenge riftchallenge
```

Variables dans `.env` à la racine (gitignored). Les migrations Flyway s'exécutent au démarrage du backend (`V1` → `V24` actuellement).

## Lancer

```powershell
. .\scripts\load-env.ps1

# Terminal 1 — backend
cd backend
mvn spring-boot:run

# Terminal 2 — frontend
cd frontend
npm start
```

- Back : http://localhost:8080
- Front : http://localhost:4200
- Health : http://localhost:8080/actuator/health

Tests :

```bash
# backend
cd backend && mvn test

# frontend
cd frontend && npm test        # ng test (Vitest, jsdom)
```

Build de prod frontend (vérifie aussi les budgets de bundle/style) :

```bash
cd frontend && npm run build
```

## Riot API (secrets)

Ne jamais committer la clé API. Utiliser le fichier `.env` à la racine (gitignored, voir [`.env.example`](.env.example) pour la liste complète des variables) :

```env
RIOT_API_KEY=votre_cle
RIOT_REGIONAL_ROUTING=europe
SPRING_PROFILES_ACTIVE=local
```

La plateforme Riot (`euw1`, etc.) est choisie **par challenge** en base, pas via une variable d'environnement globale.

Sous Windows PowerShell avant le lancement :

```powershell
Get-Content ..\.env | ForEach-Object {
  if ($_ -match '^(?<k>[^=]+)=(?<v>.*)$') { Set-Item -Path "env:$($matches.k)" -Value $matches.v }
}
cd backend
mvn spring-boot:run
```

Les clés dev Riot expirent toutes les 24 h — regénérer sur le portail si besoin.

## Routes UI (repères)

| Route | Accès |
|---|---|
| `/`, `/home` | Landing publique |
| `/challenges` | Liste des challenges + classement global |
| `/challenges/:shareSlug` | Détail d'un challenge (public via lien de partage) |
| `/challenges/new` | Création (connecté) |
| `/my-challenges` | Challenges rejoints (connecté + compte Riot lié) |
| `/settings` | Paramètres (connecté) |
| `/login`, `/auth/callback`, `/auth/reset-password` | Flux d'auth |

Table de routage source : `frontend/src/app/app.routes.ts`.

## API (repères)

Voir [`rules/10-backend.md`](rules/10-backend.md) pour la liste complète des endpoints, l'architecture backend et les mécanismes de throttling.

## Déploiement

**Production** : [https://rift-challenge.com](https://rift-challenge.com)

| Composant | Hébergeur |
|---|---|
| Frontend | Vercel (`rift-challenge.com`) |
| Backend API | Render |
| PostgreSQL | Supabase |

Guides détaillés :

- [docs/DEPLOY.md](docs/DEPLOY.md) — Render, Vercel, Supabase, CORS, domaine custom
- [docs/GOOGLE_OAUTH.md](docs/GOOGLE_OAUTH.md) — Google + Supabase pour la prod

### Checklist rapide (domaine custom)

1. **Vercel → Settings → Domains** — ajouter `rift-challenge.com` puis `www.rift-challenge.com` ; copier les enregistrements DNS affichés par Vercel dans la zone DNS chez ton registrar (OVH, Cloudflare, etc.)
2. **Vercel** — domaine principal : `rift-challenge.com` ; le `www` redirige automatiquement (config dans `frontend/vercel.json`)
3. **Render** — `CORS_ORIGINS=https://rift-challenge.com,https://www.rift-challenge.com`
4. **Vercel → Environment Variables** — `API_BASE_URL` = URL publique du backend Render (ex. `https://ton-backend.onrender.com`)
5. **Supabase** — Site URL `https://rift-challenge.com` + redirect URLs (voir [GOOGLE_OAUTH.md](docs/GOOGLE_OAUTH.md))
6. **Google Cloud** — autoriser `https://rift-challenge.com` et `https://www.rift-challenge.com` dans les origins OAuth
7. **Cloudflare Worker anti cold-start** — voir `cloudflare/keep-alive-worker/README.md` (ping périodique de `/actuator/health` sur le backend Render)
8. Redéployer front + backend, puis tester login, création de challenge et lien partageable
