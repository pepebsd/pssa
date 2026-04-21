/*
 * 
 * @file PolizaAgroseguroCargaController.java
 * @package PolizaAgroseguroCargaController 
 *
 * @author jduran5
 * @since 21-oct-2024
 */
package es.tragsatec.pssa.controller.carga;

import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
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
import org.primefaces.model.UploadedFile;

import com.ibm.icu.text.MessageFormat;
import com.opencsv.exceptions.CsvException;

import es.tragsatec.pssa.bo.carga.CargaAgroseguroBO;
import es.tragsatec.pssa.bo.carga.CargaBO;
import es.tragsatec.pssa.bo.consulta.PolizaAgroseguroBO;
import es.tragsatec.pssa.bo.mantenimiento.ConvocatoriaBO;
import es.tragsatec.pssa.bo.mantenimiento.PlanBO;
import es.tragsatec.pssa.bo.pago.DocumentoBO;
import es.tragsatec.pssa.common.PssaError;
import es.tragsatec.pssa.common.PssaException;
import es.tragsatec.pssa.constantes.Constantes;
import es.tragsatec.pssa.events.CustomEventPublisher;
import es.tragsatec.pssa.events.EndCargaAgroseguroEvent;
import es.tragsatec.pssa.events.EndSubirFicheroAgroseguroAgricolaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroAgroseguroGanaderaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroAgroseguroRetiradaEvent;
import es.tragsatec.pssa.events.StartCargaAgroseguroEvent;
import es.tragsatec.pssa.events.StartSubirFicheroAgroseguroAgricolaEvent;
import es.tragsatec.pssa.events.StartSubirFicheroAgroseguroGanaderaEvent;
import es.tragsatec.pssa.events.StartSubirFicheroAgroseguroRetiradaEvent;
import es.tragsatec.pssa.model.Documento;
import es.tragsatec.pssa.model.ErrorCargaAgroseguro;
import es.tragsatec.pssa.model.FicheroBf;
import es.tragsatec.pssa.model.PolizaAgroseguroAuxiliarAgricola;
import es.tragsatec.pssa.model.PolizaAgroseguroAuxiliarGanadera;
import es.tragsatec.pssa.model.PolizaAgroseguroAuxiliarRetiradaDestruccion;
import es.tragsatec.pssa.utilidades.AppContext;
import es.tragsatec.pssa.utilidades.UtilidadesXSSFWorkbook;


/**
 * The Class PolizaAgroseguroCargaController.
 */
@ManagedBean(name = "polizaAgroseguroCargaController")
@ViewScoped
public class PolizaAgroseguroCargaController implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -2963185802459760973L;

	/** The Constant LOGGER. */
	private static final Logger LOGGER = LogManager.getLogger(PolizaAgroseguroCargaController.class);

	/** The bundle. */
	@ManagedProperty("#{Msg}")
	private ResourceBundle bundle;

	/** The carga agroseguro BO. */
	@ManagedProperty(value = "#{cargaAgroseguroBO}")
	private CargaAgroseguroBO cargaAgroseguroBO;

	/** The carga BO. */
	@ManagedProperty(value = "#{cargaBO}")
	private CargaBO cargaBO;

	/** The documento BO. */
	@ManagedProperty(value = "#{documentoBO}")
	private DocumentoBO documentoBO;

	/** The poliza agroseguro BO. */
	@ManagedProperty(value = "#{polizaAgroseguroBO}")
	private PolizaAgroseguroBO polizaAgroseguroBO;

	/** The event publisher. */
	@ManagedProperty(value = "#{customEventPublisher}")
	private CustomEventPublisher eventPublisher;

	/** The app context. */
	@ManagedProperty(value = "#{appContext}")
	private AppContext appContext;

	/** The convocatoria BO. */
	@ManagedProperty(value = "#{convocatoriaBO}")
	ConvocatoriaBO convocatoriaBO;

	/** The plan BO. */
	@ManagedProperty(value = "#{planBO}")
	PlanBO planBO;

	/** The convocatoria. */
	private String convocatoria;

	/** The btn consolidar disabled. */
	private boolean btnConsolidarDisabled = false;

	/** The listado agricola. */
	private LazyDataModel<PolizaAgroseguroAuxiliarAgricola> listadoAgricola;
	
	/** The listado ganadera. */
	private LazyDataModel<PolizaAgroseguroAuxiliarGanadera> listadoGanadera;
	
	/** The listado retirada destruccion. */
	private LazyDataModel<PolizaAgroseguroAuxiliarRetiradaDestruccion> listadoRetiradaDestruccion;

	/** The listado documento agricola. */
	private List<Documento> listadoDocumentoAgricola;
	
	/** The listado documento ganadera. */
	private List<Documento> listadoDocumentoGanadera;
	
	/** The listado documento retirada destruccion. */
	private List<Documento> listadoDocumentoRetiradaDestruccion;

	/** The errores. */
	private List<ErrorCargaAgroseguro> errores;

	/**
	 * Onload.
	 */
	public void onload() {
		checkCargaValidaConvocatorias();
	}

	/**
	 * Inits the.
	 */
	@PostConstruct
	public void init() {
		updateListadoAgricola();
		listadoDocumentoAgricola = cargaAgroseguroBO.getListadoDocumentoAgricola();

		updateListadoGanadera();
		listadoDocumentoGanadera = cargaAgroseguroBO.getListadoDocumentoGanadera();

		updateListadoRetiradaDestruccion();
		listadoDocumentoRetiradaDestruccion = cargaAgroseguroBO
				.getListadoDocumentoRetiradaDestruccion();

		errores = cargaAgroseguroBO.getErroresCarga();

		convocatoria = cargaBO.getConvocatoriaCargaAgroseguro().toString();

	}

	/**
	 * Consolidar.
	 */
	public void consolidar() {
		LOGGER.info(Constantes.MENSAJE_CONSOLIDANDO_CARGA);
		if (!listadoDocumentoAgricola.isEmpty() && !listadoDocumentoRetiradaDestruccion.isEmpty()
				&& !appContext.isRunningCargaAgroseguro() && !appContext
						.isRunningSubirAgroseguroAgricola() && !appContext
								.isRunningSubirAgroseguroGanadera() && !appContext
										.isRunningSubirAgroseguroRetirada()
				&& !btnConsolidarDisabled) {
			eventPublisher.publish(new StartCargaAgroseguroEvent(this));
			try {

				cargaAgroseguroBO.consolidar();

				// cargamos los datos de todas las listas
				init();

				// eliminamos los datos de la tabla de errores
				cargaAgroseguroBO.deleteAllErrorCarga();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.poliza.agroseguro.mensajes.success.consolidar.title"), bundle
										.getString(
												"carga.poliza.agroseguro.mensajes.success.consolidar.msg")));
				if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
									.getListStringErroresExpReg().toString()));
				}

			}
			catch (PssaException e) {
				LOGGER.error(e.getMessage(), e);
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e
								.getMessage()));
			}
			finally {
				eventPublisher.publish(new EndCargaAgroseguroEvent(this));
			}
		}
		else {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, bundle.getString(
							"carga.poliza.agroseguro.mensajes.validate.listado.error.title"), bundle
									.getString(
											"carga.poliza.agroseguro.mensajes.validate.listado.error.msg")));
		}
		LOGGER.info("Fin consolidar carga");
	}


	/**
	 * Upload fichero agricola.
	 *
	 * @param event the event
	 * @throws Exception 
	 * @throws IOException 
	 */
	public void uploadFicheroAgricola(FileUploadEvent event) throws IOException, Exception {
		String extensionFichero = (event.getFile().getFileName().substring(event.getFile()
				.getFileName().lastIndexOf(".") + 1));
		if (extensionFichero.equals("csv")) {
			uploadFicheroAgricolaCsv(event);

		}
		else { // El fichero es xlsx
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroAgroseguroAgricolaEvent(this));
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

					documento = cargaAgroseguroBO.subirFicheroAgroseguroAgricola(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaAgroseguroBO.cargarFicheroAgroseguroAgricola(event.getFile(), documento
							.getId());

					// Actualizamos los listados
					updateListadoAgricola();
					listadoDocumentoAgricola = cargaAgroseguroBO.getListadoDocumentoAgricola();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.poliza.agroseguro.mensajes.success.upload.fichero.agricola.title"),
							MessageFormat.format(bundle.getString(
									"carga.poliza.agroseguro.mensajes.success.upload.fichero.agricola.msg"),
									event.getFile().getFileName())));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}
					cargaAgroseguroBO.isCargaValida();
					checkCargaValidaConvocatorias();
				}
				else {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
							MessageFormat.format(bundle.getString(
									Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_MSG),
									event.getFile().getFileName())));
				}
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_TITLE),
						MessageFormat.format(bundle.getString(
								Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_MSG),
								event.getFile().getFileName())));
			}
		}
		catch (PssaException e) {
			/** Si hay algun error al subir el documento o al cargar losregistros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaAgroseguroBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}

				}
			}
			LOGGER.error(e.getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
							.getMessageParam(e.getParams())));
			if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
								.getListStringErroresExpReg().toString()));
			}

		}
		catch (Exception e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaAgroseguroBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage());
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}

				}
			}
		}
		eventPublisher.publish(new EndSubirFicheroAgroseguroAgricolaEvent(this));
		}
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
		eventPublisher.publish(new StartSubirFicheroAgroseguroAgricolaEvent(this));
		try {
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
				documento = cargaAgroseguroBO.subirFicheroAgroseguroAgricolaCsv(event.getFile());
						

				// Guardamos los registros en una tabla auxiliar
				cargaAgroseguroBO.cargarFicheroAgroseguroAgricolaCsv(event, documento.getId());
				

				// Actualizamos los listados
				updateListadoAgricola();
				listadoDocumentoAgricola = cargaAgroseguroBO.getListadoDocumentoAgricola();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
						bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
				if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
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
				cargaAgroseguroBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getMessage(), ofNullable(e.getParams())
									.orElse(""))));
			if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroAgroseguroAgricolaEvent(this));
	}

	
	
	
	/**
	 * Upload fichero ganadera.
	 *
	 * @param event the event
	 */
	public void uploadFicheroGanadera(FileUploadEvent event) {
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroAgroseguroGanaderaEvent(this));
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
					documento = cargaAgroseguroBO.subirFicheroAgroseguroGanadera(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaAgroseguroBO.cargarFicheroAgroseguroGanadera(event.getFile(), documento
							.getId());

					// Actualizamos los listados
					updateListadoGanadera();
					listadoDocumentoGanadera = cargaAgroseguroBO.getListadoDocumentoGanadera();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.poliza.agroseguro.mensajes.success.upload.fichero.ganadera.title"),
							MessageFormat.format(bundle.getString(
									"carga.poliza.agroseguro.mensajes.success.upload.fichero.ganadera.msg"),
									event.getFile().getFileName())));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}
				}
				else {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
							MessageFormat.format(bundle.getString(
									Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_MSG),
									event.getFile().getFileName())));
				}
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_TITLE),
						MessageFormat.format(bundle.getString(
								Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_MSG),
								event.getFile().getFileName())));
			}
		}
		catch (PssaException e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaAgroseguroBO.eliminarPolizaAuxiliarGanaderaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage());
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}

				}
			}
			LOGGER.error(e.getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getError().getMessage(), ofNullable(e.getParams()).orElse(
									""))));
			if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
								.getListStringErroresExpReg().toString()));
			}

		}
		catch (Exception e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaAgroseguroBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage());
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}

				}
			}
		}
		eventPublisher.publish(new EndSubirFicheroAgroseguroGanaderaEvent(this));
	}

	/**
	 * Upload fichero retirada destruccion.
	 *
	 * @param event the event
	 * @throws Exception 
	 * @throws IOException 
	 */
	public void uploadFicheroRetiradaDestruccion(FileUploadEvent event) throws IOException, Exception {
		String extensionFichero = (event.getFile().getFileName().substring(event.getFile()
				.getFileName().lastIndexOf(".") + 1));
		if (extensionFichero.equals("csv")) {
			uploadFicheroRetiradaDestruccionCsv(event);

		}
		else { // El fichero es xlsx
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroAgroseguroRetiradaEvent(this));
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

					documento = cargaAgroseguroBO.subirFicheroAgroseguroRetiradaDestruccion(event
							.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaAgroseguroBO.cargarFicheroAgroseguroRetiradaDestruccion(event.getFile(),
							documento.getId());

					// Actualizamos los listados
					updateListadoRetiradaDestruccion();
					listadoDocumentoRetiradaDestruccion = cargaAgroseguroBO
							.getListadoDocumentoRetiradaDestruccion();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.poliza.agroseguro.mensajes.success.upload.fichero.retirada.destruccion.title"),
							MessageFormat.format(bundle.getString(
									"carga.poliza.agroseguro.mensajes.success.upload.fichero.retirada.destruccion.msg"),
									event.getFile().getFileName())));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}
					cargaAgroseguroBO.isCargaValida();
					checkCargaValidaConvocatorias();
				}
				else {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_TITLE),
							MessageFormat.format(bundle.getString(
									Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_DUPLICADO_MSG),
									event.getFile().getFileName())));
				}
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_TITLE),
						MessageFormat.format(bundle.getString(
								Constantes.CARGA_POLIZA_AGROSEGURO_MENSAJES_VALIDATE_UPLOAD_FICHERO_CONVOCATORIA_NUEVA_MSG),
								event.getFile().getFileName())));
			}
		}
		catch (PssaException e) {
			/** Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaAgroseguroBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage());
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}

				}
			}
			LOGGER.error(e.getError().getMessage());
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
							.getMessageParam(e.getParams())));
			if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
								.getListStringErroresExpReg().toString()));
			}

		}
		catch (Exception e) {
			/** 
			 * Si hay algun error al subir el documento o al cargar los registros, eliminamos -todo lo que se haya podido guardar en la BD 
			 * */
			if (documento != null) {
				cargaAgroseguroBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage());
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}

				}
			}
			LOGGER.error(e.getMessage(), e);
			if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO,
						PssaError.GENERAL_UNEXPECTED.getMessage()));
			}
		}
		}
		eventPublisher.publish(new EndSubirFicheroAgroseguroRetiradaEvent(this));
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
				documento = cargaAgroseguroBO.subirFicheroAgroseguroRetiradaDestruccionCsv(event.getFile());
						

				// Guardamos los registros en una tabla auxiliar
				cargaAgroseguroBO.cargarFicheroAgroseguroRetiradaDestruccionCsv(event, documento.getId());
				

				// Actualizamos los listados
				updateListadoRetiradaDestruccion();
				listadoDocumentoRetiradaDestruccion = cargaAgroseguroBO.getListadoDocumentoRetiradaDestruccion();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
						bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
				if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
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
				cargaAgroseguroBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaAgroseguroBO.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getMessage(), ofNullable(e.getParams())
									.orElse(""))));
			if (!cargaAgroseguroBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaAgroseguroBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroAgroseguroRetiradaEvent(this));
	}

	

	/**
	 * Upload fichero csv.
	 *
	 * @param event the event
	 * @throws URISyntaxException the URI syntax exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws CsvException the csv exception
	 */
	public void uploadFicheroCsv(FileUploadEvent event) throws URISyntaxException, IOException, CsvException {
		UtilidadesXSSFWorkbook utilidadesXSSFWorkbook  = new UtilidadesXSSFWorkbook (); 
		UploadedFile ficheroCsv = event.getFile();
		String fileName  = event.getFile().getFileName();
		ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		String pathSalidaCsv = servletContext.getRealPath("") + File.separator ;
		String ficheroSalidaCsvConPath = servletContext.getRealPath("") + File.separator + "upload" +    File.separator+ fileName;
		String fileNameSinExtension = fileName.substring(0,fileName.lastIndexOf("."));

		utilidadesXSSFWorkbook.ConversorCsv2XlsxFile(event, pathSalidaCsv, fileNameSinExtension);
		
	}	
	
	/**
	 * Prep download.
	 *
	 * @param fichero the fichero
	 */
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

	/**
	 * Eliminar documento.
	 *
	 * @param documento the documento
	 */
	public void eliminarDocumento(Documento documento) {
		try {
			if (documento != null) {
				// Eliminamos las polizas agr, gan y ryd
				cargaAgroseguroBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				cargaAgroseguroBO.eliminarPolizaAuxiliarGanaderaByDocumento(documento);
				cargaAgroseguroBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				documentoBO.eliminarDocumentoById(documento);

				listadoDocumentoAgricola = cargaAgroseguroBO.getListadoDocumentoAgricola();
				listadoDocumentoGanadera = cargaAgroseguroBO.getListadoDocumentoGanadera();
				listadoDocumentoRetiradaDestruccion = cargaAgroseguroBO
						.getListadoDocumentoRetiradaDestruccion();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.poliza.agroseguro.mensajes.eliminar.documento.title"),
						MessageFormat.format(bundle.getString(
								"carga.poliza.agroseguro.mensajes.validar.eliminar.documento.msg"),
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

	/**
	 * Update listado agricola.
	 */
	@SuppressWarnings("serial")
	private void updateListadoAgricola() {
		listadoAgricola = new LazyDataModel<PolizaAgroseguroAuxiliarAgricola>() {

			@Override
			public List<PolizaAgroseguroAuxiliarAgricola> load(int first, int pageSize,
					String sortField, SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaAgroseguroBO.getRowCountListadoAgricolaAux(filters);

				listadoAgricola.setRowCount(rowCount);
				return polizaAgroseguroBO.getListadoAgricolaAux(filters, sortField, sortOrder
						.name(), rowBounds);
			}
		};

	}

	/**
	 * Update listado ganadera.
	 */
	@SuppressWarnings("serial")
	private void updateListadoGanadera() {
		listadoGanadera = new LazyDataModel<PolizaAgroseguroAuxiliarGanadera>() {

			@Override
			public List<PolizaAgroseguroAuxiliarGanadera> load(int first, int pageSize,
					String sortField, SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaAgroseguroBO.getRowCountListadoGanaderaAux(filters);

				listadoGanadera.setRowCount(rowCount);
				return polizaAgroseguroBO.getListadoGanaderaAux(filters, sortField, sortOrder
						.name(), rowBounds);
			}
		};

	}

	/**
	 * Update listado retirada destruccion.
	 */
	@SuppressWarnings("serial")
	private void updateListadoRetiradaDestruccion() {
		listadoRetiradaDestruccion = new LazyDataModel<PolizaAgroseguroAuxiliarRetiradaDestruccion>() {

			@Override
			public List<PolizaAgroseguroAuxiliarRetiradaDestruccion> load(int first, int pageSize,
					String sortField, SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaAgroseguroBO.getRowCountListadoRetiradaDestruccionAux(filters);

				listadoRetiradaDestruccion.setRowCount(rowCount);
				return polizaAgroseguroBO.getListadoRetiradaDestruccionAux(filters, sortField,
						sortOrder.name(), rowBounds);
			}
		};

	}

	/**
	 * Check carga valida convocatorias.
	 */
	private void checkCargaValidaConvocatorias() {
		errores = cargaAgroseguroBO.getErroresCarga();
		if (CollectionUtils.isNotEmpty(errores)) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, bundle.getString(
							"carga.poliza.agroseguro.mensajes.validate.carga.error.msg"), ""));
		}
		Integer convNueva = cargaAgroseguroBO.isConvocatoriasNuevas();
		if (convNueva != null) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, MessageFormat.format(bundle.getString(
							"carga.poliza.agroseguro.mensajes.validate.carga.error.convocatoria.msg"),
							convNueva), ""));
		}

		PrimeFaces.current().ajax().update("@form");
	}

	/**
	 * Gets the bundle.
	 *
	 * @return the bundle
	 */
	public ResourceBundle getBundle() {
		return bundle;
	}

	/**
	 * Sets the bundle.
	 *
	 * @param bundle the new bundle
	 */
	public void setBundle(ResourceBundle bundle) {
		this.bundle = bundle;
	}

	/**
	 * Gets the documento BO.
	 *
	 * @return the documento BO
	 */
	public DocumentoBO getDocumentoBO() {
		return documentoBO;
	}

	/**
	 * Sets the documento BO.
	 *
	 * @param documentoBO the new documento BO
	 */
	public void setDocumentoBO(DocumentoBO documentoBO) {
		this.documentoBO = documentoBO;
	}

	/**
	 * Gets the poliza agroseguro BO.
	 *
	 * @return the poliza agroseguro BO
	 */
	public PolizaAgroseguroBO getPolizaAgroseguroBO() {
		return polizaAgroseguroBO;
	}

	/**
	 * Sets the poliza agroseguro BO.
	 *
	 * @param polizaAgroseguroBO the new poliza agroseguro BO
	 */
	public void setPolizaAgroseguroBO(PolizaAgroseguroBO polizaAgroseguroBO) {
		this.polizaAgroseguroBO = polizaAgroseguroBO;
	}

	/**
	 * Gets the event publisher.
	 *
	 * @return the event publisher
	 */
	public CustomEventPublisher getEventPublisher() {
		return eventPublisher;
	}

	/**
	 * Sets the event publisher.
	 *
	 * @param eventPublisher the new event publisher
	 */
	public void setEventPublisher(CustomEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Gets the app context.
	 *
	 * @return the app context
	 */
	public AppContext getAppContext() {
		return appContext;
	}

	/**
	 * Sets the app context.
	 *
	 * @param appContext the new app context
	 */
	public void setAppContext(AppContext appContext) {
		this.appContext = appContext;
	}

	/**
	 * Gets the listado agricola.
	 *
	 * @return the listado agricola
	 */
	public LazyDataModel<PolizaAgroseguroAuxiliarAgricola> getListadoAgricola() {
		return listadoAgricola;
	}

	/**
	 * Sets the listado agricola.
	 *
	 * @param listadoAgricola the new listado agricola
	 */
	public void setListadoAgricola(
			LazyDataModel<PolizaAgroseguroAuxiliarAgricola> listadoAgricola) {
		this.listadoAgricola = listadoAgricola;
	}

	/**
	 * Gets the listado ganadera.
	 *
	 * @return the listado ganadera
	 */
	public LazyDataModel<PolizaAgroseguroAuxiliarGanadera> getListadoGanadera() {
		return listadoGanadera;
	}

	/**
	 * Sets the listado ganadera.
	 *
	 * @param listadoGanadera the new listado ganadera
	 */
	public void setListadoGanadera(
			LazyDataModel<PolizaAgroseguroAuxiliarGanadera> listadoGanadera) {
		this.listadoGanadera = listadoGanadera;
	}

	/**
	 * Gets the listado retirada destruccion.
	 *
	 * @return the listado retirada destruccion
	 */
	public LazyDataModel<PolizaAgroseguroAuxiliarRetiradaDestruccion> getListadoRetiradaDestruccion() {
		return listadoRetiradaDestruccion;
	}

	/**
	 * Sets the listado retirada destruccion.
	 *
	 * @param listadoRetiradaDestruccion the new listado retirada destruccion
	 */
	public void setListadoRetiradaDestruccion(
			LazyDataModel<PolizaAgroseguroAuxiliarRetiradaDestruccion> listadoRetiradaDestruccion) {
		this.listadoRetiradaDestruccion = listadoRetiradaDestruccion;
	}

	/**
	 * Gets the listado documento agricola.
	 *
	 * @return the listado documento agricola
	 */
	public List<Documento> getListadoDocumentoAgricola() {
		return listadoDocumentoAgricola;
	}

	/**
	 * Sets the listado documento agricola.
	 *
	 * @param listadoDocumentoAgricola the new listado documento agricola
	 */
	public void setListadoDocumentoAgricola(List<Documento> listadoDocumentoAgricola) {
		this.listadoDocumentoAgricola = listadoDocumentoAgricola;
	}

	/**
	 * Gets the listado documento ganadera.
	 *
	 * @return the listado documento ganadera
	 */
	public List<Documento> getListadoDocumentoGanadera() {
		return listadoDocumentoGanadera;
	}

	/**
	 * Sets the listado documento ganadera.
	 *
	 * @param listadoDocumentoGanadera the new listado documento ganadera
	 */
	public void setListadoDocumentoGanadera(List<Documento> listadoDocumentoGanadera) {
		this.listadoDocumentoGanadera = listadoDocumentoGanadera;
	}

	/**
	 * Gets the listado documento retirada destruccion.
	 *
	 * @return the listado documento retirada destruccion
	 */
	public List<Documento> getListadoDocumentoRetiradaDestruccion() {
		return listadoDocumentoRetiradaDestruccion;
	}

	/**
	 * Sets the listado documento retirada destruccion.
	 *
	 * @param listadoDocumentoRetiradaDestruccion the new listado documento retirada destruccion
	 */
	public void setListadoDocumentoRetiradaDestruccion(
			List<Documento> listadoDocumentoRetiradaDestruccion) {
		this.listadoDocumentoRetiradaDestruccion = listadoDocumentoRetiradaDestruccion;
	}

	/**
	 * Gets the errores.
	 *
	 * @return the errores
	 */
	public List<ErrorCargaAgroseguro> getErrores() {
		return errores;
	}

	/**
	 * Sets the errores.
	 *
	 * @param errores the new errores
	 */
	public void setErrores(List<ErrorCargaAgroseguro> errores) {
		this.errores = errores;
	}

	/**
	 * Sets the convocatoria BO.
	 *
	 * @param convocatoriaBO the new convocatoria BO
	 */
	public void setConvocatoriaBO(ConvocatoriaBO convocatoriaBO) {
		this.convocatoriaBO = convocatoriaBO;
	}

	/**
	 * Gets the convocatoria BO.
	 *
	 * @return the convocatoria BO
	 */
	public ConvocatoriaBO getConvocatoriaBO() {
		return convocatoriaBO;
	}

	/**
	 * Gets the convocatoria.
	 *
	 * @return the convocatoria
	 */
	public String getConvocatoria() {
		return convocatoria;
	}

	/**
	 * Sets the convocatoria.
	 *
	 * @param convocatoria the new convocatoria
	 */
	public void setConvocatoria(String convocatoria) {
		this.convocatoria = convocatoria;
	}

	/**
	 * Sets the btn consolidar disabled.
	 *
	 * @param btnConsolidarDisabled the new btn consolidar disabled
	 */
	public void setBtnConsolidarDisabled(boolean btnConsolidarDisabled) {
		this.btnConsolidarDisabled = btnConsolidarDisabled;
	}

	/**
	 * Checks if is btn consolidar disabled.
	 *
	 * @return true, if is btn consolidar disabled
	 */
	public boolean isBtnConsolidarDisabled() {
		return btnConsolidarDisabled;
	}

	/**
	 * Sets the plan BO.
	 *
	 * @param planBO the new plan BO
	 */
	public void setPlanBO(PlanBO planBO) {
		this.planBO = planBO;
	}

	/**
	 * Gets the plan BO.
	 *
	 * @return the plan BO
	 */
	public PlanBO getPlanBO() {
		return planBO;
	}

	/**
	 * Gets the carga agroseguro BO.
	 *
	 * @return the carga agroseguro BO
	 */
	public CargaAgroseguroBO getCargaAgroseguroBO() {
		return cargaAgroseguroBO;
	}

	/**
	 * Sets the carga agroseguro BO.
	 *
	 * @param cargaAgroseguroBO the new carga agroseguro BO
	 */
	public void setCargaAgroseguroBO(CargaAgroseguroBO cargaAgroseguroBO) {
		this.cargaAgroseguroBO = cargaAgroseguroBO;
	}

	/**
	 * Gets the carga BO.
	 *
	 * @return the carga BO
	 */
	public CargaBO getCargaBO() {
		return cargaBO;
	}

	/**
	 * Sets the carga BO.
	 *
	 * @param cargaBO the new carga BO
	 */
	public void setCargaBO(CargaBO cargaBO) {
		this.cargaBO = cargaBO;
	}
}
