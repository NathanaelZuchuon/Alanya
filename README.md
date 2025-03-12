# Serveur Java Multi-Clients avec Gestion des Threads
Ce dépôt contient le code source d'un serveur Java simple mais robuste, capable de gérer plusieurs clients simultanément grâce à l'utilisation d'un `ThreadPoolExecutor`. Chaque client est traité dans un thread séparé, assurant une réponse rapide et empêchant le blocage du serveur. Un identifiant unique (UUID) est attribué à chaque client pour une meilleure traçabilité.

## Fonctionnalités
* **Multi-threading:** Gestion efficace de plusieurs clients concurrents grâce à un `ThreadPoolExecutor` configurable. La taille du pool de threads est définie par la constante `POOL_SIZE`.
* **Identifiants Clients Uniques:** Chaque client se voit attribuer un identifiant UUID unique, facilitant le suivi et le débogage.
* **Gestion des Erreurs:** Le code gère les exceptions `IOException` pour une robustesse accrue.
* **Fermeture Gracieuse:** Le serveur ferme proprement le `ServerSocket` et arrête le `ThreadPoolExecutor` avant de terminer son exécution.
* **Formatage Amélioré:** Utilisation de `String.formatted()` pour un code plus lisible et une meilleure gestion du formatage des chaînes de caractères.
* **Simple et Clair:** Le code est conçu pour être facile à comprendre et à maintenir.

## Architecture
Le serveur utilise un modèle client-serveur classique. Il écoute sur un port spécifié (8080 par défaut) et accepte les connexions entrantes. Chaque connexion client est gérée par une instance de la classe interne `ClientHandler` exécutée dans un thread distinct du pool.

## Utilisation
1. **Compilation:** Compilez le code Java à l'aide d'un compilateur Java (javac).
2. **Exécution:** Exécutez le fichier `.class` (ou un fichier JAR si vous créez un fichier JAR).
3. **Clients:** Utilisez un client (tel qu'un client Telnet ou un programme client personnalisé) pour vous connecter au serveur sur le port 8080. Chaque ligne envoyée par le client sera traitée par le serveur et une réponse sera renvoyée.

## Configuration
La taille du pool de threads (`POOL_SIZE`) peut être modifiée pour ajuster la capacité du serveur à gérer un nombre important de clients.

## Améliorations Potentielles
* **Intégration d'une base de données:** Pour une persistance des données clients.
* **Protocole de communication plus robuste:** L'utilisation d'un protocole plus sophistiqué (comme TCP) permettrait une gestion plus fiable des connexions.
* **Authentification des utilisateurs:** Ajout d'un mécanisme d'authentification pour sécuriser le serveur.
* **Gestion des déconnexions inattendues:** Amélioration de la gestion des déconnexions brutales des clients.

## Licence
MIT License
