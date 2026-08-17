# RiftRace

Plateforme de **races** League of Legends (Angular + Spring Boot + PostgreSQL).

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
- app : `riftrace` / `riftrace` / base `riftrace`

Création (déjà faite si vous suivez l'option A) :

```powershell
$env:PGPASSWORD='postgres'
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -c "CREATE ROLE riftrace LOGIN PASSWORD 'riftrace';"
& "C:\Program Files\PostgreSQL\17\bin\createdb.exe" -U postgres -h localhost -O riftrace riftrace
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

En attendant l’auth réelle, le backend accepte l’en-tête `X-Dev-User-Id: <uuid>` pour simuler un utilisateur connecté.

## Routes UI

| Route | Visiteur | Connecté |
|---|---|---|
| `/` | Races publiques | → `/my-races` |
| `/my-races` | — | Mes races |
| `/public-races` | — | Races publiques |
| `/races/:shareSlug` | Détail + lien partageable | idem |

## API (MVP)

- `GET /api/races/public`
- `GET /api/races/mine` (auth)
- `GET /api/races/share/{shareSlug}`
- `POST /api/races` (auth)

## Déploiement

Voir [docs/DEPLOY.md](docs/DEPLOY.md) — backend Render, frontend Vercel, base Supabase.
