# Encargo 57 A: severidad de cargas de perfil

Base main 1ec7e77; StatusBarPresenter 62 líneas. isError busca subcadenas en todo el mensaje. DukptCoordinator y PaymentsController emiten module.payments.status.profileLoaded ({0} profile loaded / Perfil de {0} cargado); Tr31Coordinator emite Loaded TR-31 profile: {nombre}. PaymentProfileManager incluye nombres Invalid KSN, Invalid BDK, Invalid Mode, Invalid Algorithm, Invalid PAN/IMK/Length/Key: son perfiles de laboratorio válidos que cargan escenarios negativos. Cargar datos no ejecuta su validación. El nombre se inserta literalmente y activa invalid en ambos idiomas.

Prueba previa: ProfileStatusSeverityUITest, EN/ES, cargas reales desde Laboratory, perfil positivo y negativos, pseudo-clase/error/icono y ejecución real de KSN inválido con banner. Los textos originales deben conservarse. Fixture FXML común aislada, AppSettings/Shelf restaurados.

Fallo anotado ANTES del arreglo, producción intacta de main: 4 casos, 2 fallos (EN/ES). «DUKPT TDES - Invalid KSN profile loaded» / «Perfil de DUKPT TDES - Invalid KSN cargado» aparece con × y pseudo-clase error. Las dos ejecuciones de KSN realmente inválido sí muestran banner y se clasifican como error. El primer intento de fixture no compilaba por inferencia Object en FXMLLoader.load; se corrigió declarando Parent, antes del pase válido.
