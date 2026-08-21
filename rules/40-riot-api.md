# Intégration Riot Games API

Applies to: `backend/**/riot/**`, `backend/**/synchronization/**`.

## Principe général

L'API Riot est une dépendance externe **non fiable** : latence variable, échecs, rate limits, données manquantes. La traiter comme telle — jamais d'appel direct depuis le frontend, jamais d'hypothèse optimiste côté backend.

## Sécurité

La clé API Riot est **backend-only**. Jamais exposée à Angular, jamais en dur dans le code, les tests, les logs ou un fichier committé. Elle vient uniquement de la config/environnement (`RIOT_API_KEY`).

## Identification de compte

Résoudre le Riot ID vers le **PUUID** (identifiant stable) et persister le PUUID, pas seulement le nom affiché — les Riot ID peuvent changer.

## Synchronisation des matchs

Incrémentale, jamais un re-téléchargement complet à chaque refresh :

1. identifier les participants du challenge à synchroniser ;
2. lister les matchs récents via l'API Riot ;
3. ignorer les matchs déjà persistés (dédup par `riot_match_id`) ;
4. récupérer le détail des nouveaux matchs uniquement ;
5. filtrer : queue **420** (Ranked Solo/Duo) uniquement, et `gameStart` dans `[startAt, endAt)` du challenge (comparaison en UTC) ;
6. persister, mettre à jour les stats.

## Rate limits

Timeout configuré (connect/read) sur le client HTTP. Sur 429 : retry borné en respectant `Retry-After`, pas de boucle agressive. Un pic de 429 ne doit pas bloquer indéfiniment le pool de threads de sync — préférer un backoff avec jitter à un simple `Thread.sleep` répété si le comportement actuel devient un goulot d'étranglement observé en prod.

## Détection DuoQ

Un duo ne compte une game que si les deux membres apparaissent dans le **même** match éligible. Le serveur détermine ça, jamais le client (voir `DuoEligibilityService`, règle détaillée dans `00-project.md` §3.3).

## Cache et persistance

Persister les données Riot utiles en Postgres pour qu'une vue de page ne déclenche jamais d'appel Riot. Une page challenge lit exclusivement la base.

## Tests

Ne jamais faire d'appel réseau réel dans un test unitaire. Mocker les clients Riot (`RiotAccountClient`, `RiotMatchClient`, `RiotLeagueClient`, `RiotSummonerClient`) avec des réponses représentatives : compte valide, compte manquant, match valide, match dupliqué, rate limit, timeout, réponse malformée, duo dans le même match, duo dans des matchs différents.
