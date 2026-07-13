# Firma de actualizaciones

Desde ZenGecko Mobile **v0.1.3**, los APK debug generados por GitHub Actions usan una clave estable almacenada en secretos cifrados del repositorio.

## Certificado

- Alias: `zengecko-update`
- SHA-256: `35:53:50:5C:4F:97:92:03:FC:86:8D:E3:E3:C8:3F:84:4E:B2:BE:59:35:64:DA:3C:B6:76:55:0D:CB:D6:87:82`
- Paquete debug: `com.andres.zengecko.debug`

## Importante

La clave privada no se guarda en Git. El actualizador para Termux instala una copia privada en:

```text
~/.zengecko-signing/ZenGecko-Mobile/
```

También registra el almacén y sus contraseñas como secretos de GitHub Actions. No compartas esa carpeta, el ZIP del actualizador ni los secretos.

Si se pierde la clave y los secretos se eliminan, Android no permitirá actualizar instalaciones firmadas con este certificado.
