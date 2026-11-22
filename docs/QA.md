# Lista de prueba en tu teléfono

Lo que no se puede probar en el emulador del CI: notificaciones reales, permisos, widgets, huella.
Marca cada punto y avísame cualquier cosa rara (Ajustes → Diagnóstico → Registro de errores → Compartir).

## Instalación
- [ ] Se instala desde el APK del release (encima de la versión anterior, sin perder datos)
- [ ] La splash aparece y la app abre sin pantalla en blanco

## Cuentas y tarjetas
- [ ] Crear una tarjeta con "¿Cómo está hoy tu tarjeta?" → disponible, facturado y periodo actual coinciden con el banco
- [ ] "Cuadrar tarjeta" desde el detalle corrige una diferencia
- [ ] Pagar la tarjeta desde la hoja de pago → baja el saldo del banco y sube el cupo
- [ ] Compra en 3 cuotas → aparece en "Próximos estados de cuenta" repartida
- [ ] Compra en US$ en tarjeta en pesos → convierte (o usa el monto cobrado)
- [ ] Crédito hipotecario en UF → deuda en pesos razonable; "Pagar cuota" prellena el monto

## Notificaciones (lo más importante)
- [ ] Activar detección, dar acceso a notificaciones y permiso de avisos
- [ ] Marcar tu banco y Google Wallet
- [ ] Una compra real genera el aviso "Gasto de $X en COMERCIO" con Registrar / Ignorar
- [ ] La cuenta sugerida es la correcta (crédito vs débito). Si no, configura los últimos 4 dígitos en cada cuenta
- [ ] Una notificación que no se entendió aparece en Ajustes → "Enséñale al lector"; al enseñarle, la siguiente igual se reconoce sola
- [ ] Una misma compra avisada por el banco y por Wallet aparece una sola vez
- [ ] Con 2+ pendientes, las notificaciones se agrupan

## Accesos rápidos
- [ ] Widget "Finanzas rápido": muestra patrimonio y los botones abren la hoja de gasto/ingreso
- [ ] Widget "Tarjetas": cupo y próximo pago
- [ ] Tile de ajustes rápidos "Nuevo gasto"
- [ ] Mantener presionado el ícono → "Nuevo gasto" / "Nuevo ingreso"

## Seguridad
- [ ] Activar bloqueo con huella → pide huella al abrir y tras el tiempo elegido
- [ ] "Proteger pantalla" → la app se ve en negro en recientes y no deja sacar captura
- [ ] Con bloqueo activo, los widgets ocultan los montos

## Automático (revisar al día siguiente)
- [ ] Cuenta remunerada: aparece "Rentabilidad <mes>" sumando cada día
- [ ] Recurrente con fecha de hoy → llega aviso para confirmar (o se registra solo si es automático)
- [ ] Recordatorio de tarjeta 3 días antes del vencimiento (entre 8:00 y 22:00)
- [ ] Respaldo automático semanal aparece en la carpeta elegida
