# Roadmap

Esfuerzo estimado: **S** (horas) · **M** (1–2 días) · **L** (varios días).
Marca con `[x]` lo que se vaya haciendo.

---

## Fase 0 — Dejarla lista para uso diario (prioridad máxima)

Antes de cargar datos reales, hay que asegurar que nunca se pierdan y que lo básico funcione en un teléfono de verdad.

- [x] **Migraciones de Room** (S). La base está en versión 1 y no tiene migraciones: el primer cambio de esquema haría que la app se cierre al abrir. Usar `AutoMigration` (el esquema ya se exporta en `app/schemas`) y agregar un test de migración.
- [x] **Clave de firma propia + releases en GitHub** (S). *Listo el código; falta crear la clave y cargar los secretos (ver README).* Hoy el APK de release usa la clave debug. Crear un keystore guardado como secreto de GitHub y publicar el APK en *Releases* en cada tag `v*`. Sin esto, cambiar de clave más adelante obliga a desinstalar y perder los datos.
- [x] **Bloqueo con huella o PIN** (S). `BiometricPrompt` al abrir la app o al volver después de N minutos, más `FLAG_SECURE` opcional para que no se vea en las apps recientes ni en capturas.
- [ ] **Probar en un teléfono real** (M): notificaciones (Android 13–15), widget, tile, accesos directos, modo oscuro y tamaños de letra grandes.
- [ ] **Ajustar el lector con notificaciones reales** (M). Juntar textos reales (anonimizados) de BancoEstado, Santander, BCI, Banco de Chile, Scotiabank, Itaú, Falabella, Tenpo, Mach, Mercado Pago y Google Wallet en `NotificationParserTest`.
- [ ] **Bugs encontrados al revisar el código**:
  - [x] Editar una compra antigua ("ya pagué N cuotas") vuelve a calcular la primera cuota contando desde el mes actual, así que las cuotas se corren al editar en otro mes. Guardar el periodo de la primera cuota en la transacción.
  - [x] Eliminar una cuenta también borra las transferencias que *llegaron* desde otras cuentas, lo que cambia el saldo de esas otras cuentas. Mejor archivar por defecto, o convertir esas transferencias en gastos.
  - [x] Duplicados entre apps: si Google Wallet y el banco avisan la misma compra, hoy aparecen dos pendientes. Comparar entre todas las apps vigiladas (mismo monto en ±3 min).
  - [x] La categoría "Rentabilidad" se busca por su nombre; si el usuario la renombra, se pierde. Usar una marca fija (`systemKey`).
  - [x] La rentabilidad atrasada (días sin abrir la app) se calcula con el saldo actual, no con el saldo de cada día. Calcular con el saldo de cada día.

## Fase 1 — Tarjetas de crédito (lo que diferencia la app)

- [x] ~~**Facturación dual CLP/USD**~~ — no aplica: tu banco convierte a CLP. En su lugar: compras en otra moneda con conversión y monto cobrado opcional (v0.4). Muy común en Chile: las compras internacionales van en un estado de cuenta separado en dólares, con su propio cupo. Cada tarjeta tendría un cupo nacional y uno internacional.
- [x] **Recordatorios** (S): avisar 3 días antes del vencimiento y el día del cierre ("cierra hoy, lo que compres desde mañana va al próximo mes").
- [ ] **Prepago de cuotas** (S): pagar por adelantado el saldo pendiente de una compra.
- [ ] **Pago mínimo y deuda rotativa** (M): si pagas menos del total, estimar el interés del saldo que queda con la tasa rotativa de la tarjeta.
- [ ] **Comisión de mantención y cargos fijos** (S), cobrados automáticamente cada periodo.
- [ ] **Cuadrar con el estado de cuenta** (S): "el banco dice que debo $X" crea un ajuste con la diferencia.
- [ ] **Simulador** (S): "si compro $X en N cuotas, ¿cómo quedan mi cupo y mis próximos meses?" antes de comprar.
- [ ] **Avance en efectivo / súper avance en cuotas** (S): ya funciona como transferencia desde la tarjeta; falta permitir cuotas e interés.
- [ ] Importar el estado de cuenta en PDF, leído en el teléfono (L, más adelante).

## Carga de datos pasados (v0.4) ✓

- [x] Saldo "de hoy" al crear una cuenta: los movimientos con fecha anterior quedan como historial y no lo cambian.
- [x] Corregir el saldo actual en el editor recalcula desde ahí (re-ancla).
- [x] Tarjetas: "último estado de cuenta pagado" para cargar compras antiguas con su fecha real.
- [x] Recurrentes con fecha pasada no generan pendientes atrasados.
- [x] Editar un movimiento conserva su fecha de creación.
- [x] "Guardar y otro" para cargar muchos movimientos seguidos.

## Fase 2 — Registrar más rápido

- [x] **Movimientos recurrentes** (M): sueldo, arriendo, suscripciones, cuentas básicas. Se crean como pendientes o automáticos, con recordatorio.
- [x] **Favoritos en la hoja rápida** (S): "☕ Café $3.500" con un toque; sugeridos según lo más frecuente.
- [ ] **"Enséñale" al lector** (M): desde una notificación que no se reconoció, marcar cuál es el monto y cuál el comercio, y guardarlo como regla para esa app.
- [ ] **Registro automático opcional para comercios de confianza** (S), con aviso y "Deshacer".
- [ ] **Dividir un gasto** entre categorías, y **gastos compartidos** ("me deben $X") (M).
- [ ] **Foto de la boleta** adjunta al movimiento (S).
- [~] Más widgets (M) — v0.7: widget de tarjetas: botón 1×1 de "+ gasto", resumen de una tarjeta (cupo y próximo pago), presupuesto del mes.
- [x] Deslizar un movimiento para borrarlo o duplicarlo, con "Deshacer" (S).

## Fase 3 — Entender en qué se va la plata

- [x] **Proyección de saldo** (M) ⭐: saldo futuro de tus cuentas sumando sueldo y recurrentes y restando las cuotas que vienen. Responde "¿me alcanza a fin de mes?" y "¿cuándo quedo libre de cuotas?".
- [x] **Presupuestos por categoría** (M), con barra de avance y aviso al llegar al 80% y al 100%.
- [x] **Reportes** (M): tendencia mensual, comparación con el mes anterior, gasto diario promedio, estimación de cierre de mes, top comercios.
- [ ] **Evolución del patrimonio** (S): guardar el total cada día y graficarlo.
- [x] **Metas de ahorro** (S) ligadas a una cuenta remunerada: "con tu ritmo y la tasa de Tenpo, llegas a $X en N meses".
- [ ] Etiquetas y subcategorías; búsqueda por monto o rango de fechas (S).
- [ ] Calendario mensual con vencimientos, cierres y recurrentes (M).

## Fase 4 — Más tipos de cuentas para Chile

- [x] **UF automática** (S): actualizar USD/EUR/UF una vez al día con WorkManager en vez de solo con el botón.
- [x] **Créditos de consumo e hipotecarios** (M): tabla de pagos, dividendo en UF, cuotas pendientes y cuánto falta.
- [ ] **Historial de tasas** de cuentas remuneradas (S): cuando Tenpo cambia la tasa, que los cálculos pasados no cambien.
- [ ] **Inversiones** (L): fondos mutuos, APV, acciones o cripto con valor manual; investigar la API pública de Fintual.

## Fase 5 — Datos y migración

- [ ] **Importar desde Bluecoins** (M): exportar a CSV desde Bluecoins y asignar cuentas y categorías. Así puedes migrar sin partir de cero.
- [x] **Respaldo automático programado** (S): guardar el JSON cada semana en una carpeta elegida (por ejemplo, una carpeta de Google Drive vía SAF), sin necesitar la API de Drive.
- [ ] Exportar a CSV o Excel (S).
- [ ] Cifrar la base con SQLCipher (M): a cambio, el APK pesa ~3 MB más y la app es un poco más lenta.
- [ ] Sincronizar entre dispositivos (L, solo si llega a hacer falta).

---

## Optimizaciones técnicas

**Rendimiento**
- [ ] **Baseline Profiles** (M): arranque y scroll ~20–30% más fluidos. Se generan en GitHub Actions con un emulador administrado (Gradle Managed Devices).
- [x] **Splash Screen API** y no hacer trabajo pesado al abrir (S).
- [ ] **Cálculo incremental** (M): hoy cada cambio recalcula todo en memoria. Funciona bien hasta unos ~10.000 movimientos; después conviene calcular los saldos con consultas SQL y paginar la lista de movimientos (Paging 3).
- [ ] Revisar recomposiciones con los reportes del compilador de Compose; marcar modelos como `@Immutable` y separar el estado por pantalla (S).
- [ ] `accrueYield` vuelve a leer todas las transacciones por cada cuenta; leerlas una sola vez (S).

**Animaciones y UX**
- [x] **Shared element transitions** (M): la tarjeta de la cuenta se expande hasta la pantalla de detalle.
- [x] **Predictive back** (S), Android 14+.
- [x] **Onboarding de 3 pasos** (S): moneda → primera cuenta → detección por notificaciones.
- [ ] Haptics más variados, y confetti discreto al pagar una tarjeta completa 🎉 (S).

**Calidad del código**
- [ ] Actualizar dependencias (S): Kotlin 2.2, AGP 8.x, Compose BOM, Room 2.7, Navigation con rutas tipadas.
- [ ] Un ViewModel por pantalla; tests de UI de los flujos críticos (agregar compra en cuotas, pagar tarjeta) (M).
- [ ] Mover los textos de Kotlin a `strings.xml` para poder traducir la app (M).
- [ ] Lint, ktlint/detekt en CI; Dependabot/Renovate (S).
- [ ] Accesibilidad: TalkBack para montos y gráficos, contraste, letra grande (S).
- [ ] Diseño para tablets y plegables (M).

---

## Nombre

Ideas con identidad chilena. Hay que revisar que no estén ocupadas en Play Store:

- **Luka** — "una luka" = mil pesos. Corto y memorable.
- **Cupo** — directo al punto fuerte de la app.
- **Gamba** — "una gamba" = cien pesos.
- **Chaucha** — juego con "ahorrar cada chaucha".
- **Cuotas** / **Al día** — más descriptivos.
