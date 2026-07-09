# shared

Esta carpeta queda reservada para codigo que realmente sea comun entre proyectos.

Contenido actual:

- `firebase-functions/`: backend Firebase Functions usado como servicio compartido por las aplicaciones que llaman funciones remotas.

No se movieron archivos Kotlin mixtos a `shared/` porque varios todavia mezclan pantallas de la app principal, Taller, inventario, IA y sincronizacion en el mismo archivo. Moverlos aqui sin partirlos antes dejaria una dependencia comun falsa y mantendria el problema de rendimiento/diagnostico.
