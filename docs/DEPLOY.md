# Déploiement Rift Challenge (Render + Vercel)

## Vue d'ensemble

| Composant | Hébergeur | Rôle |
|---|---|---|
| Backend Spring Boot | [Render](https://render.com) | API, Riot, PostgreSQL |
| Frontend Angular | [Vercel](https://vercel.com) | UI statique + SPA |
| Base de données | [Supabase](https://supabase.com) | PostgreSQL managé |

> **Domaine custom** : optionnel pour l’instant. Render et Vercel fournissent des URLs par défaut au premier déploiement. Quand tu achèteras un nom de domaine, tu l’ajouteras dans Render/Vercel et tu mettras à jour `CORS_ORIGINS` + les redirect URLs Supabase/Google.

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
   - Runtime : **Docker**
   - Dockerfile : `backend/Dockerfile`
   - Health check : `/actuator/health`

3. Variables d'environnement :

   | Variable | Valeur |
   |---|---|
   | `DATABASE_URL` | JDBC **Session pooler** Supabase (voir §1) |
   | `DB_USER` | `postgres.VOTRE_PROJECT_REF` |
   | `DB_PASSWORD` | *(mot de passe Supabase)* |
   | `SUPABASE_URL` | `https://xxx.supabase.co` |
   | `SUPABASE_PUBLISHABLE_KEY` | `eyJ...` |
   | `RIOT_API_KEY` | *(clé dev/prod Riot)* |
   | `RIOT_PLATFORM` | `euw1` |
   | `RIOT_REGIONAL_ROUTING` | `europe` |
   | `CORS_ORIGINS` | URL publique du front (voir §4) |

4. Après déploiement : `https://<ton-service>.onrender.com/actuator/health`

## 3. Frontend sur Vercel

1. Importer le dépôt GitHub sur Vercel.
2. **Root Directory** : `frontend`
3. **Framework Settings** — activer **Override** :

   | Réglage | Valeur |
   |---|---|
   | Build Command | `npm run build` |
   | Output Directory | `dist/frontend/browser` |
   | Install Command | `npm ci` |

4. Variables d'environnement :

   | Variable | Valeur |
   |---|---|
   | `SUPABASE_URL` | URL Supabase |
   | `SUPABASE_PUBLISHABLE_KEY` | Clé publishable |
   | `API_BASE_URL` | URL publique du backend Render (sans slash final) |

5. Déployer (**Redeploy** sans cache si besoin).

## 4. CORS et auth

Après le premier déploiement, récupère les **URLs réelles** affichées par Vercel et Render, puis :

1. Render → `CORS_ORIGINS` = URL du front Vercel (ex. `https://xxx.vercel.app`)
2. Vercel → `API_BASE_URL` = URL du backend Render (ex. `https://xxx.onrender.com`)
3. Auth Google : [docs/GOOGLE_OAUTH.md](GOOGLE_OAUTH.md) avec ces mêmes URLs

Quand tu achèteras un domaine custom, remplace ces URLs par tes domaines dans Render, Vercel, Supabase et Google — pas besoin de toucher au code.

Redirect URLs Supabase (minimum au départ) :
- URL front Vercel + `/**`
- `http://localhost:4200/**` (dev local)

## 5. Riot API en production

- Utiliser une clé **production** Riot (pas la clé dev 24 h).
- La clé reste **uniquement** côté backend (variable Render).

## 6. Vérifications post-déploiement

- [ ] Health backend OK
- [ ] Connexion / inscription Supabase
- [ ] Création de challenge
- [ ] Page publique `/challenges/:shareSlug`
- [ ] Refresh manuel (cooldown 2 min)
- [ ] Icônes de profil joueur visibles
