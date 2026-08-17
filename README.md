# Rift Challenge

Plateforme de **challenges** League of Legends (Angular + Spring Boot + PostgreSQL).

## Structure

- `frontend/` — Angular
- `backend/` — Spring Boot 3.3 / Java 21
- `.cursor/rules/` — règles projet

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

Variables dans `.env` à la racine (gitignored).

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

## Riot API (secrets)

Ne jamais committer la clé API. Utiliser le fichier `.env` à la racine (gitignored) :

```env
RIOT_API_KEY=votre_cle
RIOT_APP_ID=871406
RIOT_REGIONAL_ROUTING=europe
RIOT_PLATFORM=euw1
SPRING_PROFILES_ACTIVE=local
```

Sous Windows PowerShell avant le lancement :

```powershell
Get-Content ..\.env | ForEach-Object {
  if ($_ -match '^(?<k>[^=]+)=(?<v>.*)$') { Set-Item -Path "env:$($matches.k)" -Value $matches.v }
}
cd backend
mvn spring-boot:run
```

Les clés dev Riot expirent toutes les 24 h — regénérer sur le portail si besoin.

## Routes UI

| Route | Visiteur | Connecté |
|---|---|---|
| `/` | Challenges publics | → `/my-challenges` |
| `/my-challenges` | — | Mes challenges |
| `/public-challenges` | — | Challenges publics |
| `/challenges/:shareSlug` | Détail + lien partageable | idem |

## API (MVP)

- `GET /api/challenges/public`
- `GET /api/challenges/mine` (auth)
- `GET /api/challenges/share/{shareSlug}`
- `POST /api/challenges` (auth)

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
7. Redéployer front + backend, puis tester login, création de challenge et lien partageable

