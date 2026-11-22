# Plan hacia la 1.0

**Estado actual (v0.8):** las funciones principales están completas. Hay 69 tests de lógica, base de datos (con migraciones v1→v7) y capturas de pantalla, y el CI publica releases firmados.
**Lo que falta para llamarla 1.0** no son funciones, sino **confianza**:
- la app **nunca se ha ejecutado en un teléfono real ni en un emulador**;
- el lector de notificaciones no se ha probado con textos reales;
- no se ha medido el rendimiento con muchos datos.

Por eso las fases parten por estabilidad y terminan en pulido y lanzamiento.

Esfuerzo: **S** (horas) · **M** (1–2 días) · **L** (varios días). Cada fase es una versión que se publica y se usa unos días antes de pasar a la siguiente.

---

## Fase 1 — v0.9 · Estabilidad y pruebas reales ⭐ (implementada; falta tu prueba en el teléfono y tus textos de notificaciones)

Objetivo: tener la certeza de que la app funciona en un teléfono y de que los datos nunca se descuadran.

| Tarea | Por qué | Esfuerzo |
|---|---|---|
| **Tests en emulador dentro del CI** (GitHub Actions con emulador Android) | Hoy todo se prueba en la JVM. Un emulador ejecuta la app de verdad: abrir, crear cuentas, agregar una compra en cuotas, pagar la tarjeta, bloqueo, widget. Así se detectan bugs que no veo desde aquí. | M |
| **Lista de prueba manual** (`docs/QA.md`) para tu teléfono | Notificaciones reales, permisos de Android 13–15, widget, tile, accesos directos y huella solo se pueden validar en un teléfono físico | S |
| **Lector con notificaciones reales** de tus bancos y de Google Wallet + set de casos en los tests | Es la función más frágil: hoy está ajustada con ejemplos inventados | M (necesita tus textos) |
| **"Enséñale al lector"**: si una notificación no se entendió bien, marcas el monto y el comercio y queda guardado como regla para esa app | Lo que no reconoce hoy se pierde; con esto mejora con el uso | M |
| **Registro de errores local** (Ajustes → Diagnóstico → Compartir) | Si la app falla en tu teléfono, puedes mandarme el detalle. Sin servicios externos | S |
| **Detectar transferencias y pagos de tarjeta** en las notificaciones ("Transferencia enviada", "Pago de tarjeta") | Hoy todo se interpreta como gasto o ingreso | S |
| Pendientes detectados que vencen solos (por ejemplo a los 30 días) y notificaciones agrupadas | Que la bandeja no se llene | S |

**Para cerrar la fase:** tests en emulador pasando en el CI, la lista manual completa en tu teléfono, y 1–2 semanas de uso diario sin descuadres.

---

## Fase 2 — v0.10 · Datos: cargar, exportar y no perder nada

| Tarea | Por qué | Esfuerzo |
|---|---|---|
| **Importar cartolas (CSV/Excel del banco)**: eliges las columnas (fecha, descripción, monto), ves una vista previa y la app marca los duplicados | Es la forma más rápida de pasar tu historial sin escribirlo a mano | L |
| **Exportar a CSV/Excel** (por mes, cuenta o categoría) | Para planillas o para el contador | S |
| **Respaldos cifrados con contraseña** | El respaldo JSON tiene todos tus datos financieros en texto plano, y puede quedar en Drive | M |
| **"Mes financiero"**: el mes de presupuestos y reportes empieza el día que llega tu sueldo (ej. 25 → 24) | En Chile el sueldo suele llegar a fin de mes; con el mes calendario, presupuestos y reportes quedan desfasados | M |
| **Deshacer en todas las eliminaciones** (cuentas, recurrentes, metas, presupuestos) | Hoy solo existe para movimientos | S |
| **Ordenar cuentas** arrastrándolas | El orden ya se guarda en la base, pero no hay forma de cambiarlo | S |
| **Recurrentes de monto variable** (luz, agua): monto estimado que confirmas con el real | Hoy un recurrente siempre propone el mismo monto | S |
| **Abonos extraordinarios a créditos** (descuentan capital) | Hoy cada pago al crédito cuenta como una cuota | S |

---

## Fase 3 — v0.11 · Rendimiento

Hoy, cada cambio vuelve a calcular todos los saldos, tarjetas, reportes y favoritos en memoria. Con cientos de movimientos es instantáneo, pero no se ha medido con años de historial.

| Tarea | Meta | Esfuerzo |
|---|---|---|
| **Test con 10.000 movimientos sintéticos** que mida cuánto tarda el recálculo | Tener un número de referencia; menos de 50 ms en la JVM | S |
| **Cálculo incremental**: separar lo que cambia poco (saldos, tarjetas) de lo derivado (reportes, favoritos, proyección), y calcular reportes y favoritos solo cuando se abren | Que agregar un gasto no recalcule todo | M |
| **Lista de movimientos paginada** (Paging 3) | Abrir y desplazarse sin cargar años de datos | M |
| **Baseline Profiles generados en el CI** con el emulador de la fase 1 | Arranque y desplazamiento ~20–30% más fluidos | M |
| **Medir el arranque** (Macrobenchmark en el CI) y revisar las recomposiciones de Compose | Detectar regresiones de una versión a otra | S |
| Revisar el tamaño del APK (hoy 3,4 MB) | Que no crezca sin control | S |

---

## Fase 4 — v0.12 · Pulido, accesibilidad y vistas nuevas

| Tarea | Por qué | Esfuerzo |
|---|---|---|
| **Revisión de accesibilidad**: TalkBack (montos y gráficos legibles), contraste, letra grande, tamaño de botones | Hoy no se ha revisado, y varios gráficos no tienen descripción | M |
| **Evolución del patrimonio**: gráfico mes a mes calculado desde tus movimientos | Ver el progreso en el tiempo, no solo el total de hoy | M |
| **Resumen anual** (gastos e ingresos por mes y por categoría, tasa de ahorro del año) | Visión de largo plazo | S |
| **Inicio personalizable**: ocultar y ordenar secciones | Inicio ya tiene muchas secciones (proyección, presupuestos, metas, próximos…) | M |
| **Filtros avanzados en Movimientos**: rango de fechas y de montos, varias categorías | Encontrar un gasto puntual | S |
| **Pantallas vacías y animaciones**: ilustraciones, animación al cumplir una meta o pagar la tarjeta completa | Sensación de app terminada | S |
| Tablets y modo horizontal | Que no se vea estirada | M |
| Widgets: botón 1×1 de "+ gasto" y presupuesto del mes | Más accesos rápidos | S |

---

## Fase 5 — v1.0 · Lanzamiento

| Tarea | Por qué | Esfuerzo |
|---|---|---|
| **Ordenar el código**: dividir archivos grandes (`AddTransactionSheet.kt` tiene 750 líneas, `SettingsScreens.kt` 570), un ViewModel por pantalla, lint/detekt en el CI | Que sea fácil seguir agregando funciones después de la 1.0 | M |
| **Actualizar dependencias** (Kotlin, AGP, Compose, Room) | Base actualizada antes de lanzar | S |
| **Nombre e ícono definitivos** | Hoy es "Finanzas" / `personal_finances` | S |
| **Aviso de actualización** en la app (consulta los releases de GitHub) | Como se instala fuera de Play Store, hoy no sabes cuándo hay versión nueva | S |
| **CHANGELOG.md y notas de versión** automáticas | Historial claro de cambios | S |
| **Política de privacidad** (necesaria si se publica en Play Store) | Requisito de la tienda, sobre todo por la lectura de notificaciones | S |
| **Release candidate** (v1.0-rc) → 1–2 semanas de uso → **v1.0** | Lanzar con confianza | — |

---

## Después de la 1.0 (fuera de alcance por ahora)

- Sincronizar entre dispositivos.
- Importar estados de cuenta en PDF.
- Inversiones con valor automático (por ejemplo la API de Fintual).
- Pago mínimo e interés rotativo de tarjetas.
- Gastos compartidos.
- Base de datos cifrada (SQLCipher).
- Versión en inglés: hoy hay ~215 textos escritos directamente en el código.
- Wear OS.

---

## Decisiones pendientes

1. **Dónde se distribuye la 1.0:** solo GitHub Releases (como ahora), Google Play (requiere política de privacidad, declarar el uso de notificaciones y una revisión de Google) o F-Droid.
2. **Nombre definitivo** (ideas: Luka, Cupo, Gamba, Chaucha).
3. **Prioridad de la importación de cartolas:** si te ahorra mucho trabajo, se puede adelantar a la fase 1.
