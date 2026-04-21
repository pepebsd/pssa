package es.tragsatec.pssa.controller.carga;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.ibatis.session.RowBounds;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

import com.ibm.icu.text.MessageFormat;

import es.tragsatec.pssa.bo.carga.CargaSimulacionBO;
import es.tragsatec.pssa.bo.carga.PolizaSimulacionBO;
import es.tragsatec.pssa.bo.mantenimiento.ConvocatoriaBO;
import es.tragsatec.pssa.bo.mantenimiento.PlanBO;
import es.tragsatec.pssa.bo.pago.DocumentoBO;
import es.tragsatec.pssa.common.PssaError;
import es.tragsatec.pssa.common.PssaException;
import es.tragsatec.pssa.constantes.Constantes;
import es.tragsatec.pssa.events.CustomEventPublisher;
import es.tragsatec.pssa.events.EndCargaSimulacionEvent;
import es.tragsatec.pssa.events.EndSubirFicheroAgroseguroAgricolaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroAgroseguroRetiradaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroSimulacionAgricolaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroSimulacionGanaderaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroSimulacionRetiradaEvent;
import es.tragsatec.pssa.events.StartCargaSimulacionEvent;
import es.tragsatec.pssa.events.StartSubirFicheroAgroseguroRetiradaEvent;
import es.tragsatec.pssa.events.StartSubirFicheroSimulacionAgricolaEvent;
import es.tragsatec.pssa.events.StartSubirFicheroSimulacionGanaderaEvent;
import es.tragsatec.pssa.events.StartSubirFicheroSimulacionRetiradaEvent;
import es.tragsatec.pssa.model.Convocatoria;
import es.tragsatec.pssa.model.Documento;
import es.tragsatec.pssa.model.ErrorCargaSimulacion;
import es.tragsatec.pssa.model.FicheroBf;
import es.tragsatec.pssa.model.PolizaSimulacionAuxiliarAgricola;
import es.tragsatec.pssa.model.PolizaSimulacionAuxiliarGanadera;
import es.tragsatec.pssa.model.PolizaSimulacionAuxiliarRetiradaDestruccion;
import es.tragsatec.pssa.utilidades.AppContext;

@ManagedBean(name = "polizaSimulacionCargaController")
@ViewScoped
public class PolizaSimulacionCargaController implements Serializable {

	private static final long serialVersionUID = -2963185802459760973L;

	private static final Logger LOGGER = LogManager.getLogger(PolizaSimulacionCargaController.class);

	@ManagedProperty("#{Msg}")
	private ResourceBundle bundle;

	@ManagedProperty(value = "#{cargaSimulacionBO}")
	private CargaSimulacionBO cargaBO;

	@ManagedProperty(value = "#{documentoBO}")
	private DocumentoBO documentoBO;

	@ManagedProperty(value = "#{polizaSimulacionBO}")
	private PolizaSimulacionBO polizaSimulacionBO;

	@ManagedProperty(value = "#{customEventPublisher}")
	private CustomEventPublisher eventPublisher;

	@ManagedProperty(value = "#{appContext}")
	private AppContext appContext;

	@ManagedProperty(value = "#{convocatoriaBO}")
	ConvocatoriaBO convocatoriaBO;

	@ManagedProperty(value = "#{planBO}")
	PlanBO planBO;

	private String convocatoria;

	private boolean btnConsolidarDisabled = false;

	private LazyDataModel<PolizaSimulacionAuxiliarAgricola> listadoAgricola;
	private LazyDataModel<PolizaSimulacionAuxiliarGanadera> listadoGanadera;
	private LazyDataModel<PolizaSimulacionAuxiliarRetiradaDestruccion> listadoRetiradaDestruccion;

	private List<Documento> listadoDocumentoAgricola;
	private List<Documento> listadoDocumentoGanadera;
	private List<Documento> listadoDocumentoRetiradaDestruccion;

	private List<ErrorCargaSimulacion> errores;

	@PostConstruct
	public void init() {
		updateListadoAgricola();
		listadoDocumentoAgricola = cargaBO.getListadoDocumentoAgricola();

		updateListadoGanadera();
		listadoDocumentoGanadera = cargaBO.getListadoDocumentoGanadera();

		updateListadoRetiradaDestruccion();
		listadoDocumentoRetiradaDestruccion = cargaBO.getListadoDocumentoRetiradaDestruccion();

		errores = cargaBO.getErroresCarga();

		convocatoria = mostrarConvocatoriaASimular(this.convocatoriaBO
				.getConvocatoriasPorcentajesNoCalculados());
	}

	public void onload() {
		checkCargaValidaConvocatorias();
	}

	public void consolidar() {
		LOGGER.info(Constantes.MENSAJE_CONSOLIDANDO_CARGA);
		if (errores.isEmpty() && !listadoDocumentoAgricola.isEmpty() && !listadoDocumentoRetiradaDestruccion.isEmpty()
				&& !appContext.isRunningCargaSimulacion() && !appContext
						.isRunningSubirSimulacionAgricola() && !appContext
								.isRunningSubirSimulacionGanadera() && !appContext
										.isRunningSubirSimulacionRetirada()
				&& !btnConsolidarDisabled) {
			eventPublisher.publish(new StartCargaSimulacionEvent(this));
			try {
				// Comprobamos que los planes, lineas y modulos existen en la BD
				cargaBO.consolidar();
				// cargamos los datos de todas las listas
				init();
				// eliminamos los datos de la tabla de errores
				cargaBO.deleteAllErrorCarga();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.poliza.simulacion.mensajes.success.consolidar.title"), bundle
										.getString(
												"carga.poliza.simulacion.mensajes.success.consolidar.msg")));
				if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
									.getListStringErroresExpReg().toString()));
				}

			}
			catch (PssaException e) {
				LOGGER.error(e.getMessage(), e);
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
								.getMessage()));
			}
			finally {
				eventPublisher.publish(new EndCargaSimulacionEvent(this));
			}
		}
		else {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, bundle.getString(
							"carga.poliza.simulacion.mensajes.validate.listado.error.title"), bundle
									.getString(
											"carga.poliza.simulacion.mensajes.validate.listado.error.msg")));
		}
		LOGGER.info("Fin consolidar carga");
	}

	private void checkCargaValidaConvocatorias() {
		errores = cargaBO.getErroresCarga();
		if (CollectionUtils.isNotEmpty(errores)) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, bundle.getString(
							"carga.poliza.agroseguro.mensajes.validate.carga.error.msg"), ""));
		}
		Integer convNueva = cargaBO.isConvocatoriasNuevas();
		if (convNueva != null) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, MessageFormat.format(bundle.getString(
							"carga.poliza.agroseguro.mensajes.validate.carga.error.convocatoria.msg"),
							convNueva), ""));
		}

		PrimeFaces.current().ajax().update("@form");
	}

	/**
	 * Metodo donde se busca el codigo de la convocatoria no simulada. En caso
	 * de estar simulada se muestra un mensaje generico.
	 * 
	 * @param listaConvocatorias
	 * @return
	 */
	public String mostrarConvocatoriaASimular(List<Convocatoria> listaConvocatorias) {

		String codConvocatoria = "";

		if (listaConvocatorias.isEmpty()) {

			int planMaximo = planBO.getPlanMaximo().getPlan();
			
			int convocatoriaMaxima = Optional.ofNullable(convocatoriaBO.getConvocatoriaMaxima())
                    .map(Convocatoria::getCodigo)
                    .orElse(planMaximo+1); // o un valor por defecto
			

			if (convocatoriaMaxima > 0 && planMaximo > 0) {
				convocatoriaMaxima++;
				planMaximo++;
			}

			// montamos el mensaje.
			codConvocatoria = "Para realizar la simulación de los porcentajes por línea, pertenecientes al PLAN "
					+ planMaximo + ", es necesario crear primero la CONVOCATORIA "
					+ convocatoriaMaxima;

			btnConsolidarDisabled = true;
		}
		else {
			codConvocatoria = listaConvocatorias.get(0).getCodigo().toString();
		}
		return codConvocatoria;
	}

	public void uploadFicheroAgricola(FileUploadEvent event) throws IOException, Exception {
		String extensionFichero = (event.getFile().getFileName().substring(event.getFile()
				.getFileName().lastIndexOf(".") + 1));
		if (extensionFichero.equals("csv")) {
			uploadFicheroAgricolaCsv(event);
		}
		else { // El fichero es xlsx
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroSimulacionAgricolaEvent(this));
		try {
			if (!btnConsolidarDisabled) {
				if (!listadoDocumentoAgricola.isEmpty()) {
					for (Documento doc : listadoDocumentoAgricola) {
						if (doc.getDesDocumento().equals(event.getFile().getFileName())) {
							isRepeated = Boolean.TRUE;
							break;
						}
					}
				}
				if (!isRepeated) {
					// Subimos el fichero al directorio de oracle

					documento = cargaBO.subirFicheroSimulacionAgricola(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaBO.cargarFicheroSimulacionAgricola(event.getFile(), documento.getId());

					// Actualizamos los listados
					updateListadoAgricola();
					listadoDocumentoAgricola = cargaBO.getListadoDocumentoAgricola();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.poliza.simulacion.mensajes.success.upload.fichero.agricola.title"),
							MessageFormat.format(bundle.getString(
									"carga.poliza.simulacion.mensajes.success.upload.fichero.agricola.msg"),
									event.getFile().getFileName())));
					if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
										.getListStringErroresExpReg().toString()));
					}
					cargaBO.isCargaValida();
					checkCargaValidaConvocatorias();
				}
				else {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
							MessageFormat.format(bundle.getString(
									"carga.poliza.simulacion.mensajes.validate.upload.fichero.duplicado.msg"),
									event.getFile().getFileName())));
				}
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_TITLE),
						MessageFormat.format(bundle.getString(
								Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_MSG),
								event.getFile().getFileName())));
			}
		}
	
		catch (PssaException e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaBO.eliminarPolizaAuxiliarSimulacionAgricolaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
										.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getError().getMessage(), ofNullable(e.getParams()).orElse(
									""))));
		}
		}
		eventPublisher.publish(new EndSubirFicheroSimulacionAgricolaEvent(this));
		}
	
	

	
	
	/**
	 * Upload fichero agricola csv.
	 *
	 * @param event the event
	 * @throws Exception the exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void uploadFicheroAgricolaCsv(FileUploadEvent event) throws Exception, IOException {

		// Obtenemos el nombre del fichero
		String fileName = event.getFile().getFileName();
		ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance()
				.getExternalContext().getContext();
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroSimulacionAgricolaEvent(this));
		if (!listadoDocumentoAgricola.isEmpty()) {
			for (Documento doc : listadoDocumentoAgricola) {
				if (doc.getDesDocumento().equals(event.getFile().getFileName())) {
					isRepeated = Boolean.TRUE;
					break;
				}
			}
		}
		//Si no existe fichero igual subido 
		if (!isRepeated) {

			// Subimos el fichero al directorio de oracle
			documento = cargaBO.subirFicheroSimulacionAgricolaCsv(event.getFile());
					

			// Guardamos los registros en una tabla auxiliar
			cargaBO.cargarFicheroSimulacionAgricolaCsv(event, documento.getId());
			

			// Actualizamos los listados
			updateListadoAgricola();
			listadoDocumentoAgricola = cargaBO.getListadoDocumentoAgricola();

			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_INFO, bundle.getString(
							Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
					bundle.getString(
							Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
			if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
								.getListStringErroresExpReg().toString()));
			}

		}
		else {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, bundle.getString(
							Constantes.CARGA_POLIZA_ENESA_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
					bundle.getString(
							Constantes.CARGA_POLIZA_ENESA_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_MSG)));
		}
		eventPublisher.publish(new EndSubirFicheroAgroseguroAgricolaEvent(this));
	}
	
	
	public void uploadFicheroGanadera(FileUploadEvent event) {
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroSimulacionGanaderaEvent(this));
		try {
			if (!btnConsolidarDisabled) {
				if (!listadoDocumentoGanadera.isEmpty()) {
					for (Documento doc : listadoDocumentoGanadera) {
						if (doc.getDesDocumento().equals(event.getFile().getFileName())) {
							isRepeated = Boolean.TRUE;
							break;
						}
					}
				}
				if (!isRepeated) {
					// Subimos el fichero al directorio de oracle
					documento = cargaBO.subirFicheroSimulacionGanadera(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaBO.cargarFicheroSimulacionGanadera(event.getFile(), documento.getId());

					// Actualizamos los listados
					updateListadoGanadera();
					listadoDocumentoGanadera = cargaBO.getListadoDocumentoGanadera();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.poliza.simulacion.mensajes.success.upload.fichero.ganadera.title"),
							MessageFormat.format(bundle.getString(
									"carga.poliza.simulacion.mensajes.success.upload.fichero.ganadera.msg"),
									event.getFile().getFileName())));
				}
				else {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
							MessageFormat.format(bundle.getString(
									"carga.poliza.simulacion.mensajes.validate.upload.fichero.duplicado.msg"),
									event.getFile().getFileName())));
					if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
										.getListStringErroresExpReg().toString()));
					}
				}
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_TITLE),
						MessageFormat.format(bundle.getString(
								Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_MSG),
								event.getFile().getFileName())));
			}
		}
		catch (PssaException e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaBO.eliminarPolizaAuxiliarSimulacionGanaderaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
										.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getError().getMessage(), ofNullable(e.getParams()).orElse(
									""))));
		}
		eventPublisher.publish(new EndSubirFicheroSimulacionGanaderaEvent(this));
	}

	public void uploadFicheroRetiradaDestruccion(FileUploadEvent event) throws IOException, Exception{
		String extensionFichero = (event.getFile().getFileName().substring(event.getFile()
				.getFileName().lastIndexOf(".") + 1));
		if (extensionFichero.equals("csv")) {
			uploadFicheroRetiradaDestruccionCsv(event);

		}
		else { // El fichero es xlsx

		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroSimulacionRetiradaEvent(this));
		try {
			if (!btnConsolidarDisabled) {
				if (!listadoDocumentoRetiradaDestruccion.isEmpty()) {
					for (Documento doc : listadoDocumentoRetiradaDestruccion) {
						if (doc.getDesDocumento().equals(event.getFile().getFileName())) {
							isRepeated = Boolean.TRUE;
							break;
						}
					}
				}
				if (!isRepeated) {
					// Subimos el fichero al directorio de oracle

					documento = cargaBO.subirFicheroSimulacionRetiradaDestruccion(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaBO.cargarFicheroSimulacionRetiradaDestruccion(event.getFile(), documento
							.getId());

					// Actualizamos los listados
					updateListadoRetiradaDestruccion();
					listadoDocumentoRetiradaDestruccion = cargaBO
							.getListadoDocumentoRetiradaDestruccion();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.poliza.simulacion.mensajes.success.upload.fichero.retirada.destruccion.title"),
							MessageFormat.format(bundle.getString(
									"carga.poliza.simulacion.mensajes.success.upload.fichero.retirada.destruccion.msg"),
									event.getFile().getFileName())));
					if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
										.getListStringErroresExpReg().toString()));
					}
					cargaBO.isCargaValida();
					checkCargaValidaConvocatorias();
				}
				else {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
							MessageFormat.format(bundle.getString(
									Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_MSG),
									event.getFile().getFileName())));
				}
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_TITLE),
						MessageFormat.format(bundle.getString(
								Constantes.CARGA_POLIZA_SIMULACION_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_MSG),
								event.getFile().getFileName())));
			}
		}
		catch (PssaException e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaBO.eliminarPolizaAuxiliarSimulacionRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
										.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getError().getMessage(), ofNullable(e.getParams()).orElse(
									""))));
			if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroSimulacionRetiradaEvent(this));
	}
	}
	
	
	
	/**
	 * Upload fichero retiradaDestruccioncsv.
	 *
	 * @param event the event
	 * @throws Exception the exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void uploadFicheroRetiradaDestruccionCsv(FileUploadEvent event) throws Exception, IOException {

		// Obtenemos el nombre del fichero
		String fileName = event.getFile().getFileName();
		ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance()
				.getExternalContext().getContext();
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroAgroseguroRetiradaEvent(this));
		try {
			if (!listadoDocumentoRetiradaDestruccion.isEmpty()) {
				for (Documento doc : listadoDocumentoRetiradaDestruccion) {
					if (doc.getDesDocumento().equals(event.getFile().getFileName())) {
						isRepeated = Boolean.TRUE;
						break;
					}
				}
			}
			//Si no existe fichero igual subido 
			if (!isRepeated) {

				
				// Subimos el fichero al directorio de oracle
				documento = cargaBO.subirFicheroAgroseguroRetiradaDestruccionCsv(event.getFile());
						

				// Guardamos los registros en una tabla auxiliar
				cargaBO.cargarFicheroAgroseguroRetiradaDestruccionCsv(event, documento.getId());
				

				// Actualizamos los listados
				updateListadoRetiradaDestruccion();
				listadoDocumentoRetiradaDestruccion = cargaBO.getListadoDocumentoRetiradaDestruccion();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
						bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
				if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
									.getListStringErroresExpReg().toString()));
				}

			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
						bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_MSG)));
			}
		}
		catch (PssaException e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaBO.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getMessage(), ofNullable(e.getParams())
									.orElse(""))));
			if (!cargaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroAgroseguroRetiradaEvent(this));
	}

	

	public synchronized void prepDownload(FicheroBf fichero) {
		try {
			byte[] contentFichero = documentoBO.downloadFicheroBfile(null, fichero.getId());
			FacesContext facesContext = FacesContext.getCurrentInstance();
			ExternalContext externalContext = facesContext.getExternalContext();
			externalContext.setResponseHeader(Constantes.CONTENT_TYPE_NAME, fichero.getMimeType());
			externalContext.setResponseHeader(Constantes.CONTENT_LENGTH, fichero.getSize()
					.toString());
			externalContext.setResponseHeader(Constantes.CONTENT_DISPOSITION,
					"attachment;filename=\"" + fichero.getNombreFichero() + "\"");
			externalContext.getResponseOutputStream().write(contentFichero);
			externalContext.getResponseOutputStream().flush();
			facesContext.responseComplete();
		}
		catch (PssaException e) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_INFO, e.getError().getMessage(), ""));
			LOGGER.error(e.getError(), e);
		}
		catch (IOException e) {
			LOGGER.error(PssaError.GENERAL_ERROR_FICHEROS_DESCARGA, e);
		}
	}

	public void eliminarDocumento(Documento documento) {
		try {
			if (documento != null) {
				// Eliminamos las polizas agr, gan y ryd
				cargaBO.eliminarPolizaAuxiliarSimulacionAgricolaByDocumento(documento);
				cargaBO.eliminarPolizaAuxiliarSimulacionGanaderaByDocumento(documento);
				cargaBO.eliminarPolizaAuxiliarSimulacionRetiradaDestruccionByDocumento(documento);
				documentoBO.eliminarDocumentoById(documento);

				listadoDocumentoAgricola = cargaBO.getListadoDocumentoAgricola();
				listadoDocumentoGanadera = cargaBO.getListadoDocumentoGanadera();
				listadoDocumentoRetiradaDestruccion = cargaBO
						.getListadoDocumentoRetiradaDestruccion();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.poliza.simulacion.mensajes.eliminar.documento.title"),
						MessageFormat.format(bundle.getString(
								"carga.poliza.simulacion.mensajes.validar.eliminar.documento.msg"),
								documento.getDesDocumento())));
			}
		}
		catch (PssaException e) {
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
							.getMessage()));
		}
	}

	@SuppressWarnings("serial")
	private void updateListadoAgricola() {
		listadoAgricola = new LazyDataModel<PolizaSimulacionAuxiliarAgricola>() {

			@Override
			public List<PolizaSimulacionAuxiliarAgricola> load(int first, int pageSize,
					String sortField, SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaSimulacionBO.getRowCountListadoSimulacionAgricolaAux(filters);

				listadoAgricola.setRowCount(rowCount);
				return polizaSimulacionBO.getListadoSimulacionAgricolaAux(filters, sortField,
						sortOrder.name(), rowBounds);
			}
		};

	}

	@SuppressWarnings("serial")
	private void updateListadoGanadera() {
		listadoGanadera = new LazyDataModel<PolizaSimulacionAuxiliarGanadera>() {

			@Override
			public List<PolizaSimulacionAuxiliarGanadera> load(int first, int pageSize,
					String sortField, SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaSimulacionBO.getRowCountListadoSimulacionGanaderaAux(filters);

				listadoGanadera.setRowCount(rowCount);
				return polizaSimulacionBO.getListadoSimulacionGanaderaAux(filters, sortField,
						sortOrder.name(), rowBounds);
			}
		};

	}

	@SuppressWarnings("serial")
	private void updateListadoRetiradaDestruccion() {
		listadoRetiradaDestruccion = new LazyDataModel<PolizaSimulacionAuxiliarRetiradaDestruccion>() {

			@Override
			public List<PolizaSimulacionAuxiliarRetiradaDestruccion> load(int first, int pageSize,
					String sortField, SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaSimulacionBO
						.getRowCountListadoSimulacionRetiradaDestruccionAux(filters);

				listadoRetiradaDestruccion.setRowCount(rowCount);
				return polizaSimulacionBO.getListadoSimulacionRetiradaDestruccionAux(filters,
						sortField, sortOrder.name(), rowBounds);
			}
		};

	}

	public ResourceBundle getBundle() {
		return bundle;
	}

	public void setBundle(ResourceBundle bundle) {
		this.bundle = bundle;
	}

	public CargaSimulacionBO getCargaBO() {
		return cargaBO;
	}

	public void setCargaBO(CargaSimulacionBO cargaBO) {
		this.cargaBO = cargaBO;
	}

	public DocumentoBO getDocumentoBO() {
		return documentoBO;
	}

	public void setDocumentoBO(DocumentoBO documentoBO) {
		this.documentoBO = documentoBO;
	}

	public PolizaSimulacionBO getPolizaSimulacionBO() {
		return polizaSimulacionBO;
	}

	public void setPolizaSimulacionBO(PolizaSimulacionBO polizaSimulacionBO) {
		this.polizaSimulacionBO = polizaSimulacionBO;
	}

	public CustomEventPublisher getEventPublisher() {
		return eventPublisher;
	}

	public void setEventPublisher(CustomEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	public AppContext getAppContext() {
		return appContext;
	}

	public void setAppContext(AppContext appContext) {
		this.appContext = appContext;
	}

	public LazyDataModel<PolizaSimulacionAuxiliarAgricola> getListadoAgricola() {
		return listadoAgricola;
	}

	public void setListadoAgricola(
			LazyDataModel<PolizaSimulacionAuxiliarAgricola> listadoAgricola) {
		this.listadoAgricola = listadoAgricola;
	}

	public LazyDataModel<PolizaSimulacionAuxiliarGanadera> getListadoGanadera() {
		return listadoGanadera;
	}

	public void setListadoGanadera(
			LazyDataModel<PolizaSimulacionAuxiliarGanadera> listadoGanadera) {
		this.listadoGanadera = listadoGanadera;
	}

	public LazyDataModel<PolizaSimulacionAuxiliarRetiradaDestruccion> getListadoRetiradaDestruccion() {
		return listadoRetiradaDestruccion;
	}

	public void setListadoRetiradaDestruccion(
			LazyDataModel<PolizaSimulacionAuxiliarRetiradaDestruccion> listadoRetiradaDestruccion) {
		this.listadoRetiradaDestruccion = listadoRetiradaDestruccion;
	}

	public List<Documento> getListadoDocumentoAgricola() {
		return listadoDocumentoAgricola;
	}

	public void setListadoDocumentoAgricola(List<Documento> listadoDocumentoAgricola) {
		this.listadoDocumentoAgricola = listadoDocumentoAgricola;
	}

	public List<Documento> getListadoDocumentoGanadera() {
		return listadoDocumentoGanadera;
	}

	public void setListadoDocumentoGanadera(List<Documento> listadoDocumentoGanadera) {
		this.listadoDocumentoGanadera = listadoDocumentoGanadera;
	}

	public List<Documento> getListadoDocumentoRetiradaDestruccion() {
		return listadoDocumentoRetiradaDestruccion;
	}

	public void setListadoDocumentoRetiradaDestruccion(
			List<Documento> listadoDocumentoRetiradaDestruccion) {
		this.listadoDocumentoRetiradaDestruccion = listadoDocumentoRetiradaDestruccion;
	}

	public List<ErrorCargaSimulacion> getErrores() {
		return errores;
	}

	public void setErrores(List<ErrorCargaSimulacion> errores) {
		this.errores = errores;
	}

	public void setConvocatoriaBO(ConvocatoriaBO convocatoriaBO) {
		this.convocatoriaBO = convocatoriaBO;
	}

	public ConvocatoriaBO getConvocatoriaBO() {
		return convocatoriaBO;
	}

	public void setPlanBO(PlanBO planBO) {
		this.planBO = planBO;
	}

	public PlanBO getPlanBO() {
		return planBO;
	}

	public String getConvocatoria() {
		return convocatoria;
	}

	public void setConvocatoria(String convocatoria) {
		this.convocatoria = convocatoria;
	}

	public void setBtnConsolidarDisabled(boolean btnConsolidarDisabled) {
		this.btnConsolidarDisabled = btnConsolidarDisabled;
	}

	public boolean isBtnConsolidarDisabled() {
		return btnConsolidarDisabled;
	}
}
