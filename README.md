# PapuCraftNametags

Bridge para PapuCraft Gamma (Paper 26.2) entre TAB + PlaceholderAPI + el plugin de clanes.

## Regla

- Los miembros del mismo clan SIEMPRE ven el nametag entre ellos.
- Los jugadores externos obedecen la preferencia del jugador observado:
  - `/nametag on`
  - `/nametag off`
  - `/nametag toggle`
  - `/nametag status`

La preferencia se guarda por UUID en `plugins/PapuCraftNametags/players.yml`.

## Por qué usa PlaceholderAPI en vez de la API de ApexClan

Así el bridge no queda amarrado a un plugin de clanes. Si cambias ApexClan en el futuro, solo cambia `clan-placeholder` en `config.yml`.

Para ApexClan:

```yaml
clan-placeholder: "%apex_clan_name%"
has-clan-placeholder: "%apex_has_clan%"
```

## Dependencias del servidor

- Paper 26.2
- Java 25
- TAB
- PlaceholderAPI
- ApexClan (o cualquier plugin de clan con un placeholder de nombre de clan)

En TAB, `scoreboard-teams.enabled` debe estar en `true` y `invisible-nametags` en `false`.

## Compilar sin instalar nada local

1. Crea un repositorio nuevo en GitHub.
2. Sube el contenido de esta carpeta a la raíz del repo.
3. Ve a **Actions** -> **Build PapuCraftNametags** -> **Run workflow**.
4. Abre el workflow terminado.
5. Descarga el artifact `PapuCraftNametags-jar`.
6. Extrae `PapuCraftNametags-1.0.0.jar` y súbelo a `/plugins` en PebbleHost.
7. Reinicia el servidor.

## Compilar con Maven

Requiere JDK 25 y Maven:

```bash
mvn clean package
```

El JAR aparecerá en:

```text
target/PapuCraftNametags-1.0.0.jar
```

## LuckPerms

```text
lp group jugador permission set papucraft.nametag.use true
```

Fundator ya tendrá el permiso administrativo si conserva `*`.
