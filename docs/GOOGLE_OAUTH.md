# Google OAuth — RiftRace

L’app utilise **Supabase Auth** pour Google. Le front redirige vers Google, Supabase gère le callback OAuth, puis renvoie l’utilisateur sur `/auth/callback`.

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
     https://rift-race-beta.vercel.app
     ```
     *(ajoute ton domaine custom plus tard)*
   - **Authorized redirect URIs** — **important : URL Supabase, pas Vercel** :
     ```
     https://VOTRE_PROJECT_REF.supabase.co/auth/v1/callback
     ```
     Tu la trouves dans Supabase → **Authentication → Providers → Google** (copier l’URL callback affichée).
5. Copie **Client ID** et **Client Secret**

## 2. Supabase

1. **Authentication → Providers → Google**
   - Active Google
   - Colle **Client ID** et **Client Secret**
   - Save
2. **Authentication → URL Configuration**
   - **Site URL** : `https://rift-race-beta.vercel.app`
   - **Redirect URLs** :
     ```
     https://rift-race-beta.vercel.app/**
     http://localhost:4200/**
     ```

## 3. Vérification

1. Ouvre `https://rift-race-beta.vercel.app/login`
2. Clique **Continuer avec Google**
3. Après Google → retour sur `/auth/callback` → redirection `/my-races`

En cas d’erreur, le message s’affiche sur la page login.

## Dépannage

| Erreur | Cause probable |
|---|---|
| `redirect_uri_mismatch` | Redirect URI Google ≠ `https://xxx.supabase.co/auth/v1/callback` |
| Retour login sans session | Redirect URLs Supabase manquantes pour Vercel |
| `Access blocked: app not verified` | App Google en mode Testing → ajoute ton email en test user |
| Compte créé sans username | Normal : le backend dérive un username depuis le nom Google ou l’email |

## Secrets

- **Client Secret Google** → uniquement dans Supabase (pas Vercel, pas Git)
- **Client ID** → Supabase uniquement
