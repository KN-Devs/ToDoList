# Déploiement sur Render

Ce projet inclut un fichier [`render.yaml`](render.yaml) qui décrit les trois ressources nécessaires (base de données PostgreSQL, service backend, site statique frontend) sous forme de *Blueprint* Render. La création du compte et la première synchronisation du Blueprint se font sur le dashboard Render ; le reste (build, déploiement, redéploiements suivants) est automatique à chaque push sur `main`.

Le backend et le frontend ont chacun besoin de connaître l'URL de l'autre, mais aucune des deux URLs n'existe avant le premier déploiement. La procédure se fait donc en deux passes.

## Première passe

1. Créer un compte sur [render.com](https://render.com) et connecter le compte GitHub qui héberge ce dépôt.
2. Dans le dashboard, choisir **New > Blueprint** et sélectionner ce dépôt. Render détecte `render.yaml` et propose de créer les trois ressources (`todolist-db`, `todolist-backend`, `todolist-frontend`).
3. Avant de valider, Render demande une valeur pour les variables marquées `sync: false` :
   - `JWT_SECRET` sur `todolist-backend` : générer une valeur avec `openssl rand -base64 64` et la coller telle quelle.
   - `CORS_ALLOWED_ORIGINS` et `FRONTEND_URL` sur `todolist-backend` : laisser vide pour l'instant, on les renseignera après le premier déploiement.
   - `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` sur `todolist-backend` : une adresse Gmail et un mot de passe d'application dédié (https://myaccount.google.com/apppasswords), jamais le mot de passe du compte Google. `MAIL_FROM` peut reprendre la même adresse que `MAIL_USERNAME`.
   - `API_BASE_URL` sur `todolist-frontend` : laisser vide également.
4. Valider. Render construit et déploie les trois ressources. Le frontend échouera à contacter le backend et le backend refusera les requêtes cross-origin du frontend — c'est attendu à ce stade.
5. Noter les deux URLs attribuées par Render (visibles sur la page de chaque service), par exemple :
   - `https://todolist-backend-xxxx.onrender.com`
   - `https://todolist-frontend-xxxx.onrender.com`

## Seconde passe

1. Sur `todolist-backend`, onglet **Environment**, renseigner :
   ```
   CORS_ALLOWED_ORIGINS=https://todolist-frontend-xxxx.onrender.com
   FRONTEND_URL=https://todolist-frontend-xxxx.onrender.com
   ```
2. Sur `todolist-frontend`, onglet **Environment**, renseigner :
   ```
   API_BASE_URL=https://todolist-backend-xxxx.onrender.com/api
   ```
3. Redéployer manuellement les deux services (bouton **Manual Deploy**) pour que le backend prenne en compte la nouvelle origine autorisée et que le frontend reconstruise son bundle avec la bonne URL d'API.

L'application est alors accessible sur l'URL du frontend, avec le backend et la base de données provisionnés automatiquement.

## Points d'attention

- **Base de données gratuite** : le plan `free` de Render PostgreSQL est temporaire (expiration après une période définie par Render). Pour un usage au-delà d'une démonstration, passer sur un plan payant avant l'expiration.
- **Mise en veille** : un service web gratuit Render se met en veille après une période d'inactivité ; la première requête qui le réveille peut prendre plusieurs dizaines de secondes.
- **Redéploiements suivants** : une fois les deux variables d'environnement renseignées, tout nouveau push sur `main` redéploie automatiquement les deux services sans étape manuelle supplémentaire.
