# Déploiement Rift Challenge (Render + Vercel)

## Vue d'ensemble

| Composant | Hébergeur | Rôle |
|---|---|---|
| Backend Spring Boot | [Render](https://render.com) | API, Riot, PostgreSQL |
| Frontend Angular | [Vercel](https://vercel.com) | UI statique + SPA |
| Base de données | [Supabase](https://supabase.com) | PostgreSQL managé |

**Production** : [https://rift-challenge.com](https://rift-challenge.com)  
(`www.rift-challenge.com` redirige vers l’adresse sans `www`.)

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
   | `CORS_ORIGINS` | `https://rift-challenge.com,https://www.rift-challenge.com` |
   | `HIKARI_MAX_POOL_SIZE` | `3` (le session pooler Supabase plafonne ~15 clients) |

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

4. **Brancher ton nom de domaine** (Vercel → ton projet → **Settings** → **Domains**) :

   Tu as acheté **rift-challenge.com**. Il faut dire à Vercel d’afficher le site à cette adresse.

   **Étape A — Ajouter les deux adresses du site**

   Clique **Add**, puis ajoute **une par une** :
   - `rift-challenge.com` → c’est l’adresse **sans** `www` (celle que tu veux comme adresse principale)
   - `www.rift-challenge.com` → c’est la variante **avec** `www`

   **Étape B — Configurer le DNS chez ton registrar**

   Là où tu as acheté le domaine (OVH, Cloudflare, Google Domains, etc.), ouvre la gestion **DNS** / **Zone DNS**.

   Vercel affiche des **enregistrements à copier** (souvent un enregistrement **A** ou **CNAME**). Recopie-les tels quels.

   En résumé :
   - pour `rift-challenge.com` → Vercel te dira quoi mettre (souvent type **A**, valeur une IP, ou **CNAME** `cname.vercel-dns.com`)
   - pour `www.rift-challenge.com` → en général un **CNAME** vers `cname.vercel-dns.com`

   Attends quelques minutes (parfois plus). Vercel passe le domaine en **Valid** quand c’est bon.

   **Étape C — Adresse principale et redirection www**

   - Dans Vercel, mets **`rift-challenge.com`** comme domaine **Primary** (principal).
   - Le fichier `frontend/vercel.json` du repo redirige déjà `www.rift-challenge.com` vers `rift-challenge.com` : si quelqu’un tape le `www`, il arrive quand même sur la bonne adresse.

   **Résultat attendu** : le site répond sur `https://rift-challenge.com`, et `https://www.rift-challenge.com` renvoie vers la même chose.

5. Variables d'environnement :

   | Variable | Valeur |
   |---|---|
   | `SUPABASE_URL` | URL Supabase |
   | `SUPABASE_PUBLISHABLE_KEY` | Clé publishable |
   | `API_BASE_URL` | URL publique du backend Render (sans slash final) |

6. Déployer (**Redeploy** sans cache si besoin).

> **En cas de blocage DNS** : dans Vercel, onglet Domains → clique sur le domaine → lis les valeurs exactes à mettre chez ton registrar. Ne devine pas : copie ce que Vercel affiche.

## 4. CORS et auth

1. Render → `CORS_ORIGINS` = `https://rift-challenge.com,https://www.rift-challenge.com`
2. Vercel → `API_BASE_URL` = URL du backend Render (ex. `https://xxx.onrender.com`)
3. Auth Google : [docs/GOOGLE_OAUTH.md](GOOGLE_OAUTH.md)

Redirect URLs Supabase (prod + dev) :

- `https://rift-challenge.com`
- `https://rift-challenge.com/**`
- `https://rift-challenge.com/auth/callback`
- `https://www.rift-challenge.com`
- `https://www.rift-challenge.com/**`
- `https://www.rift-challenge.com/auth/callback`
- `http://localhost:4200/**` (dev local)

Supabase → **Site URL** : `https://rift-challenge.com`

## 5. Riot API en production

- Utiliser une clé **production** Riot (pas la clé dev 24 h).
- La clé reste **uniquement** côté backend (variable Render).

## 6. Vérifications post-déploiement

- [ ] Health backend OK
- [ ] `https://rift-challenge.com` fonctionne, et `https://www.rift-challenge.com` redirige vers la même adresse (sans `www`)
- [ ] Connexion / inscription Supabase
- [ ] Google OAuth depuis le domaine custom
- [ ] Création de challenge
- [ ] Page publique `/challenges/:shareSlug`
- [ ] Refresh manuel (cooldown 2 min)
- [ ] Icônes de profil joueur visibles
