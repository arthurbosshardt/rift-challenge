# Déploiement RiftRace (Render + Vercel)

## Vue d'ensemble

| Composant | Hébergeur | Rôle |
|---|---|---|
| Backend Spring Boot | [Render](https://render.com) | API, Riot, PostgreSQL |
| Frontend Angular | [Vercel](https://vercel.com) | UI statique + SPA |
| Base de données | [Supabase](https://supabase.com) | PostgreSQL managé |

## 1. Supabase

1. Créer un projet Supabase.
2. Noter l'URL du projet et la clé **publishable** (Project Settings → API).
3. Récupérer la connection string JDBC pour **Render** (connexion externe) :
   - Supabase → **Connect** → **Session pooler** (pas « Direct connection »)
   - Render ne supporte pas bien l’IPv6 Supabase → la connexion directe (`db.xxx.supabase.co`) échoue souvent avec `Network unreachable`
   - Exemple JDBC Session pooler :
     ```
     jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
     ```
   - `DB_USER` = `postgres.VOTRE_PROJECT_REF` (format pooler Supabase)
   - `DB_PASSWORD` = mot de passe base Supabase

   > Flyway a besoin du **Session pooler** (port 5432), pas du Transaction pooler (port 6543).

Les migrations Flyway s'exécutent au démarrage du backend.

## 2. Backend sur Render

1. Connecter le dépôt GitHub à Render.
2. **New → Blueprint** (si `render.yaml` est détecté) ou **Web Service** manuel :
   - Root directory : `backend`
   - Runtime : **Docker** (Render ne propose pas de runtime Java natif)
   - Dockerfile : `backend/Dockerfile`
   - Health check : `/actuator/health`

   > Avec `rootDir: backend`, les chemins Docker sont relatifs à ce dossier : `./Dockerfile` et `.` (pas `./backend/...`).

3. Variables d'environnement :

   | Variable | Exemple |
   |---|---|
   | `DATABASE_URL` | JDBC **Session pooler** Supabase (voir §1) |
   | `DB_USER` | `postgres.VOTRE_PROJECT_REF` |
   | `DB_PASSWORD` | *(mot de passe Supabase)* |
   | `SUPABASE_URL` | `https://xxx.supabase.co` |
   | `SUPABASE_PUBLISHABLE_KEY` | `eyJ...` |
   | `RIOT_API_KEY` | *(clé dev/prod Riot)* |
   | `RIOT_PLATFORM` | `euw1` |
   | `RIOT_REGIONAL_ROUTING` | `europe` |
   | `CORS_ORIGINS` | `https://votre-app.vercel.app` |

4. Attendre le déploiement et vérifier : `https://votre-backend.onrender.com/actuator/health`

## 3. Frontend sur Vercel

1. Importer le dépôt GitHub sur Vercel.
2. **Root Directory** : `frontend`
3. **Framework Settings** (Settings → Build & Deployment) — activer **Override** et configurer :

   | Réglage | Valeur |
   |---|---|
   | Framework Preset | **Other** (ou Angular + overrides ci-dessous) |
   | Build Command | `npm run build` |
   | Output Directory | `dist/frontend/browser` |
   | Install Command | `npm ci` |

   > La prod peut rester bloquée sur d’anciens **Production Overrides** (`dist/frontend`). Si l’écran affiche « Overridden » avec l’ancienne valeur, mets à jour **Project Settings** puis **Redeploy** sans cache.

4. Variables d'environnement (Settings → Environment Variables) :

   | Variable | Valeur |
   |---|---|
   | `SUPABASE_URL` | URL Supabase |
   | `SUPABASE_PUBLISHABLE_KEY` | Clé publishable |
   | `API_BASE_URL` | URL Render du backend (sans slash final) |

5. Déployer (**Redeploy** → décocher « Use existing Build Cache »).

## 4. CORS et auth

Après le premier déploiement front, mettre à jour `CORS_ORIGINS` sur Render avec l'URL Vercel réelle (y compris le domaine de preview si besoin).

Configurer l'auth Google : voir [docs/GOOGLE_OAUTH.md](GOOGLE_OAUTH.md).

Redirect URLs Supabase minimales :
- `https://votre-app.vercel.app/**`
- `http://localhost:4200/**` (dev)

## 5. Riot API en production

- Utiliser une clé **production** Riot (pas la clé dev 24 h).
- La clé reste **uniquement** côté backend (variable Render).

## 6. Vérifications post-déploiement

- [ ] Health backend OK
- [ ] Connexion / inscription Supabase
- [ ] Création de race
- [ ] Page publique `/races/:shareSlug`
- [ ] Refresh manuel (cooldown 2 min)
- [ ] Icônes de profil joueur visibles
