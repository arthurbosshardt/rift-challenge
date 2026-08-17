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
3. Récupérer la connection string JDBC :
   - Project Settings → Database → Connection string → **URI**
   - Convertir en JDBC pour Spring Boot :
     ```
     jdbc:postgresql://db.YOUR_PROJECT.supabase.co:5432/postgres?sslmode=require
     ```
   - Utilisateur / mot de passe : ceux indiqués dans Supabase.

Les migrations Flyway s'exécutent au démarrage du backend.

## 2. Backend sur Render

1. Connecter le dépôt GitHub à Render.
2. **New → Blueprint** (si `render.yaml` est détecté) ou **Web Service** manuel :
   - Root directory : `backend`
   - Runtime : **Docker** (Render ne propose pas de runtime Java natif)
   - Dockerfile : `backend/Dockerfile`
   - Health check : `/actuator/health`

   > Le Blueprint `render.yaml` configure déjà `runtime: docker` — pas besoin de commandes build/start Maven.

3. Variables d'environnement :

   | Variable | Exemple |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require` |
   | `DB_USER` | `postgres` |
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
3. Vercel lit `frontend/vercel.json` (build + rewrite SPA).
4. Variables d'environnement (Settings → Environment Variables) :

   | Variable | Valeur |
   |---|---|
   | `SUPABASE_URL` | URL Supabase |
   | `SUPABASE_PUBLISHABLE_KEY` | Clé publishable |
   | `API_BASE_URL` | URL Render du backend (sans slash final) |

5. Déployer. Le script `scripts/write-production-env.mjs` génère `environment.prod.ts` au build.

## 4. CORS et auth

Après le premier déploiement front, mettre à jour `CORS_ORIGINS` sur Render avec l'URL Vercel réelle (y compris le domaine de preview si besoin).

Configurer l'auth Google dans Supabase (Redirect URLs) :
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
