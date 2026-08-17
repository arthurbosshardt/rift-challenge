# Google OAuth — Rift Challenge

L’app utilise **Supabase Auth** pour Google. Le front redirige vers Google, Supabase gère le callback OAuth, puis renvoie l’utilisateur sur `/auth/callback`.

Utilise les **URLs réelles** de ton déploiement (Vercel, puis domaine custom plus tard si tu en achètes un).

## 1. Google Cloud Console

1. Ouvre [Google Cloud Console](https://console.cloud.google.com/)
2. Crée un projet (ou utilise un existant)
3. **APIs & Services → OAuth consent screen**
   - Type : **External** (ou Internal si Google Workspace)
   - Remplis nom app, email support
   - Scopes : `email`, `profile`, `openid` (défaut suffit)
   - Utilisateurs test : ajoute ton email si l’app est en mode « Testing »
4. **APIs & Services → Credentials → Create Credentials → OAuth client ID**
   - Type : **Web application**
   - **Authorized JavaScript origins** :
     ```
     http://localhost:4200
     https://TON-URL-VERCEL.vercel.app
     ```
     *(ajoute ton domaine custom ici quand tu l’auras)*
   - **Authorized redirect URIs** — **important : URL Supabase, pas l’URL Vercel** :
     ```
     https://VOTRE_PROJECT_REF.supabase.co/auth/v1/callback
     ```
5. Copie **Client ID** et **Client Secret**

## 2. Supabase

1. **Authentication → Providers → Google**
   - Active Google
   - Colle **Client ID** et **Client Secret**
   - Save
2. **Authentication → URL Configuration**
   - **Site URL** : URL de prod du front (Vercel pour l’instant)
   - **Redirect URLs** — une URL par ligne :
     ```
     https://TON-URL-VERCEL.vercel.app
     https://TON-URL-VERCEL.vercel.app/**
     https://TON-URL-VERCEL.vercel.app/auth/callback
     http://localhost:4200
     http://localhost:4200/**
     http://localhost:4200/auth/callback
     ```

   Quand tu auras un domaine custom, ajoute les mêmes entrées avec ce domaine (sans retirer localhost).

## 3. Vérification

1. Ouvre l’URL de prod → login
2. **Continuer avec Google**
3. Retour sur `/auth/callback` → redirection `/my-challenges`

## Dépannage

| Erreur | Cause probable |
|---|---|
| `redirect_uri_mismatch` | Redirect URI Google ≠ `https://xxx.supabase.co/auth/v1/callback` |
| Retour login sans session | Redirect URLs Supabase manquantes pour l’URL du front |
| `Access blocked: app not verified` | App Google en Testing → ajoute ton email en test user |

## Secrets

- **Client Secret Google** → uniquement dans Supabase (pas dans le front, pas Git)
- **Client ID** → Supabase uniquement
