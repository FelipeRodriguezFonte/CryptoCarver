# Encargo 57 B: nombre del perfil EMV

Base main 1ec7e77; EMVController 1468 líneas. LaboratoryMenuCoordinator navega a EMV Tool: shell publica Loaded: EMV Tool. EMVController.loadProfile rellena controles ARQC/ARPC y solo escribe el nombre en consola. DukptCoordinator y PaymentsController publican module.payments.status.profileLoaded, con formato localizado {0} profile loaded / Perfil de {0} cargado. Se reutilizará esa clave tras cargar el perfil EMV, sin cambiar navegación ni cálculos.

Prueba previa: EmvProfileStatusUITest EN/ES, menu real de Laboratory, perfiles ARQC, ARPC y negativo; exige el nombre y el mismo formato de otros módulos en texto y accesible. Fixture restaura AppSettings/Shelf.

Fallo anotado ANTES del arreglo, producción intacta de main: 2 casos, 2 fallos EN/ES. Tras cargar EMV ARQC (Option A) el status sigue siendo «Loaded: EMV Tool» y no identifica perfil. No se cambia el texto de otras cargas.

Arreglo B: una publicación de status después de rellenar el perfil EMV, vía StatusReporter actual y la clave de los otros módulos. Sin recursos nuevos ni cambios de cálculo. Pase dirigido -Plow-cpu: 2 casos EN/ES (tres perfiles cada uno), 0 fallos/errores/omisiones. EMVController 1468 → 1469 líneas.
