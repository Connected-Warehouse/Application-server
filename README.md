# 📦 AbsolutWarehouse — Serveur TCP (Java)

## 📝 Description

AbsolutWarehouse Server est un serveur TCP Java dédié à la gestion d’un pseudo entrepôt.  
Il prend en charge :

- Gestion des items  
- Système de permissions (`R-`, `-W`, `RW`)  
- Serveur socket multi-thread  
- Gestion centralisée des commandes (ADD / READ / MODIFY / DELETE)  
- Query builder interne + utilitaires DB  

---

## 🗂 Architecture du projet

```
src
└── main
    ├── java
    │   └── io
    │       └── absolutwarehouse
    │           ├── Main.java
    │           │
    │           ├── config
    │           │   ├── .gitignore
    │           │   ├── ActionConfig.java
    │           │   ├── ServerConfig.java
    │           │   └── ServerExampleConfig.java
    │           │
    │           ├── manager
    │           │   ├── ClientManager.java
    │           │   ├── DatabaseManager.java
    │           │   └── SocketServerManager.java
    │           │
    │           ├── network
    │           │   ├── Client.java
    │           │   ├── SocketServer.java
    │           │   └── listener
    │           │       ├── ClientListener.java
    │           │       └── MyListener.java
    │           │
    │           └── utils
    │               └── DbUtils.java
    │
    └── resources
        └── server_config.json
```

---

## 🔐 Système de permissions

Chaque terminal possède un `permission_code` :

| Code | Lecture | Écriture |
|------|---------|----------|
| RW   | ✔ | ✔ |
| R-   | ✔ | ❌ |
| -W   | ❌ | ✔ |
| --   | ❌ | ❌ |

Permissions définies dans `server_config.json` :

```json
{
  "actions": {
    "ADD": ["-W", "RW"],
    "MODIFY": ["RW"],
    "DELETE": ["RW"],
    "READ": ["R-", "RW"]
  }
}
```

Le système utilise les regex :  
- `-` devient `.` → caractère libre  
- Exemple : `-W` → `.W` → accepte `-W` et `RW`

---

## 🖧 Serveur TCP

- Multi-thread  
- Un thread par client  
- Listeners personnalisables  
- Configuration via `ServerConfig`  

Classe principale : `Main.java`

---

## 🛠 Configuration du serveur


```java
public final class ServerExampleConfig {

    public static String SERVER_NAME = "SERVER_NAME";
    public static String ip = "127.0.0.1";
    public static int PORT = 8080;

    public static int MAX_CONCURRENT_CONNECTIONS = 1;

    public static int MAX_PACKET_SIZE = 1024; // in Bytes

    public static String DB_HOSTNAME = "postgre-...";
    public static String DB_NAME = "my_db_name";
    public static String DB_USERNAME = "myUsername";
    public static String DB_PASSWORD = "myPassword";
    public static int DB_PORT = 5432;

}
```

---

## 🗃 Gestion de la base de données

Exemple SELECT :

```java
ResultSet rs = db.from("item")
    .select("*")
    .where("item_id = ?", id)
    .execute();
```

Exemple UPDATE :

```java
db.update("item")
  .set("item_status", "in_transit")
  .where("item_id = ?", itemId)
  .execute();
```

---


## Lancement du serveur

```
java -jar AbsolutWarehouse-Server.jar
```

---

## Remarques

* Il est important de configurer votre fichier de config au démarrage dans le code source !
* Le serveur applicatif dépend énormément de DB à laquel vous le connectez. Attention à vos données !