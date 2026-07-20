# ToDoList

![CI](https://github.com/KN-Devs/ToDoList/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F)
![Angular](https://img.shields.io/badge/Angular-21-DD0031)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED)

Application de gestion de tâches et de projets en équipe, avec authentification JWT, verrouillage progressif du compte, tableau Kanban par glisser-déposer et un modèle de permissions par membre de projet. Développée comme pièce de portfolio pour couvrir, sur un périmètre volontairement complet, ce qu'on attend d'une API REST sécurisée et de son client Angular : contrôle d'accès correctement testé, conteneurisation, intégration continue, documentation d'API et tests de bout en bout.

## Sommaire

- [Aperçu](#aperçu)
- [Fonctionnalités](#fonctionnalités)
- [Stack technique](#stack-technique)
- [Démarrage avec Docker](#démarrage-avec-docker)
- [Développement local](#développement-local)
- [Tests](#tests)
- [Documentation de l'API](#documentation-de-lapi)
- [Sécurité](#sécurité)
- [Intégration continue](#intégration-continue)
- [Structure du dépôt](#structure-du-dépôt)

## Aperçu

| Projets | Tableau Kanban |
|---|---|
| ![Liste des projets](docs/screenshots/projets.jpg) | ![Tableau Kanban](docs/screenshots/kanban.jpg) |

| Détail d'un projet et gestion des membres | Documentation de l'API |
|---|---|
| ![Détail d'un projet](docs/screenshots/projet-detail.jpg) | ![Swagger UI](docs/screenshots/swagger.jpg) |

## Fonctionnalités

**Comptes et authentification**
- Inscription et connexion par JWT (access token de 15 minutes) accompagné d'un refresh token (30 jours, révocable côté serveur) permettant de rester connecté sans réauthentification ; mots de passe hachés avec BCrypt
- Politique de mot de passe (longueur minimale, majuscule, chiffre, caractère spécial)
- Confirmation de l'adresse email par lien à usage unique (24h) ; la connexion est bloquée tant que l'email n'est pas confirmé
- Mot de passe oublié : réinitialisation par lien à usage unique (24h)
- Verrouillage progressif après échecs de connexion répétés (1 min, 5 min, 10 min, puis verrouillage permanent nécessitant une réinitialisation par un administrateur)
- Emails normalisés en minuscules pour éviter les doublons de compte
- Modification du profil (nom, prénom, email)

**Projets et tâches**
- Un projet a un propriétaire, des dates de début/fin et une liste de membres
- Les tâches appartiennent à un projet et se déplacent entre trois statuts (à faire, en cours, terminée) par glisser-déposer ou menu déroulant
- Recherche et filtrage des tâches par statut
- Détail d'une tâche en modale, avec édition et suppression
- Date d'échéance optionnelle, mise en évidence sur la carte si elle est dépassée ou proche (moins de 48h)
- Commentaires sur une tâche, chacun pouvant supprimer les siens
- Pièces jointes (5 Mo maximum par fichier), stockées en base de données pour survivre aux redéploiements

**Permissions et invitations**
- Le propriétaire d'un projet invite des membres par email ; l'invitation (lien à usage unique, 24h) doit être acceptée avant que la personne n'accède au projet
- Le propriétaire peut annuler une invitation en attente ou retirer un membre déjà présent
- Le propriétaire accorde à chaque membre le droit de gérer les tâches ; sans ce droit, un membre peut consulter le projet et déplacer les tâches, mais ne peut ni en créer, ni en modifier le contenu, ni en supprimer
- Un rôle ADMIN a accès en lecture à l'ensemble des projets et peut réinitialiser le mot de passe d'un compte verrouillé

## Stack technique

| Couche | Technologies |
|---|---|
| Backend | Spring Boot 4.1.0, Spring Security, Spring Data JPA, Flyway, JJWT |
| Frontend | Angular 21 (composants standalone, signals, zoneless), Angular CDK (drag-and-drop) |
| Base de données | PostgreSQL 16 |
| Tests backend | JUnit 5, Mockito, Testcontainers |
| Tests frontend | Vitest |
| Tests end-to-end | Playwright |
| Documentation API | springdoc-openapi / Swagger UI |
| Infrastructure | Docker, Docker Compose, GitHub Actions |

## Démarrage avec Docker

Le moyen le plus rapide de lancer l'application complète (backend, frontend, base de données).

```bash
cp .env.example .env
# renseigner JWT_SECRET dans .env, par exemple avec :
openssl rand -base64 64

docker compose up --build
```

L'envoi d'email (confirmation de compte, mot de passe oublié, invitations) est désactivé par défaut : les emails sont simplement loggés. Pour les envoyer réellement, renseigner `MAIL_ENABLED=true` et les identifiants SMTP dans `.env` (voir les commentaires du fichier).

- Frontend : http://localhost:4200
- API : http://localhost:8080/api
- Documentation Swagger : http://localhost:8080/swagger-ui/index.html

Pour un déploiement sur une infrastructure réelle (Render), voir [`DEPLOYMENT.md`](DEPLOYMENT.md).

## Développement local

### Prérequis

- Java 21
- Node.js 22
- Docker (pour PostgreSQL, ou une instance PostgreSQL 16 locale)

### Base de données

```bash
docker run -d --name todolist-db \
  -e POSTGRES_DB=todolist -e POSTGRES_USER=todolist -e POSTGRES_PASSWORD=todolist \
  -p 5432:5432 postgres:16
```

### Backend

```bash
cd Backend
export JWT_SECRET=$(openssl rand -base64 64)
./mvnw spring-boot:run
```

L'application refuse de démarrer si `JWT_SECRET` n'est pas défini. Les migrations Flyway s'appliquent automatiquement au démarrage.

### Frontend

```bash
cd frontend
npm ci
npx ng serve
```

## Tests

```bash
# Backend : 137 tests (unitaires + intégration via Testcontainers)
cd Backend && ./mvnw test

# Frontend : 157 tests (Vitest)
cd frontend && npx ng test --watch=false

# End-to-end : 5 scénarios (Playwright, nécessite le backend et le frontend démarrés)
cd e2e && npm ci && npx playwright test
```

Les tests d'intégration backend démarrent leur propre conteneur PostgreSQL via Testcontainers ; aucune base de données locale n'est requise pour les lancer.

## Documentation de l'API

La documentation interactive est générée à partir du code (annotations OpenAPI) et accessible sur `/swagger-ui/index.html` une fois le backend démarré. Elle permet d'authentifier une requête avec un token JWT et d'exécuter directement les appels depuis le navigateur.

## Sécurité

Le projet a fait l'objet d'un audit orienté OWASP, avec exploitation réelle des failles trouvées avant correction (secret JWT compromis, absence de normalisation des emails, fuite de trace d'exécution sur une erreur non gérée). Le détail est consultable dans l'historique des commits et des pull requests. Points notables de l'implémentation actuelle :

- Le secret de signature JWT est obligatoirement fourni par variable d'environnement, jamais committé
- Contrôle d'accès vérifié à chaque niveau (projet, tâche, membre) plutôt que délégué au seul frontend
- Requêtes paramétrées via JPA/Hibernate, aucune concaténation de SQL
- Gestionnaire d'erreurs global évitant toute fuite de détail d'implémentation dans les réponses HTTP
- Tokens de confirmation d'email, de réinitialisation de mot de passe et d'invitation à usage unique, expirant automatiquement au bout de 24 heures
- Refresh tokens stockés hachés (SHA-256) en base, jamais en clair ; révoqués automatiquement à la déconnexion et lors de tout changement de mot de passe
- Réponses HTTP 401 (authentification manquante ou invalide) distinguées des 403 (authentifié mais non autorisé), pour que le frontend ne rafraîchisse un token que lorsque c'est réellement nécessaire

## Intégration continue

Chaque push et chaque pull request déclenchent quatre jobs indépendants sur GitHub Actions : tests backend, tests frontend, build des images Docker, et suite end-to-end complète (base de données, backend et frontend démarrés dans le pipeline). Le détail est dans [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Structure du dépôt

```
Backend/     API Spring Boot
frontend/    Application Angular
e2e/         Tests Playwright
docs/        Ressources pour la documentation (captures d'écran)
```
