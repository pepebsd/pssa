/*
 * 
 * @file PolizaEnesaCargaController.java
 * @package PolizaEnesaCargaController 
 *
 * @author jduran5
 * @since 21-oct-2024
 */
package es.tragsatec.pssa.controller.carga;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.TabChangeEvent;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

import com.ibm.icu.text.MessageFormat;

import es.tragsatec.pssa.bo.carga.CargaBO;
import es.tragsatec.pssa.bo.carga.CargaEnesaBO;
import es.tragsatec.pssa.bo.consulta.PolizaEnesaBO;
import es.tragsatec.pssa.bo.pago.DocumentoBO;
import es.tragsatec.pssa.common.PssaError;
import es.tragsatec.pssa.common.PssaException;
import es.tragsatec.pssa.constantes.Constantes;
import es.tragsatec.pssa.dto.ComparativaCargaDto;
import es.tragsatec.pssa.events.CustomEventPublisher;
import es.tragsatec.pssa.events.EndCargaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroEnesaAgricolaEvent;
import es.tragsatec.pssa.events.EndSubirFicheroEnesaRetiradaEvent;
import es.tragsatec.pssa.events.StartCargaEvent;
import es.tragsatec.pssa.events.StartSubirFicheroEnesaAgricolaEvent;
import es.tragsatec.pssa.events.StartSubirFicheroEnesaRetiradaEvent;
import es.tragsatec.pssa.model.Carga;
import es.tragsatec.pssa.model.CargaTipoCodigo;
import es.tragsatec.pssa.model.Documento;
import es.tragsatec.pssa.model.ErrorCargaEnesa;
import es.tragsatec.pssa.model.FicheroBf;
import es.tragsatec.pssa.model.PolizaEnesaAuxiliarAgricola;
import es.tragsatec.pssa.model.PolizaEnesaAuxiliarGanadera;
import es.tragsatec.pssa.model.PolizaEnesaAuxiliarRetiradaDestruccion;
import es.tragsatec.pssa.model.TipoExplotacionCodigo;
import es.tragsatec.pssa.utilidades.AppContext;


/**
 * The Class PolizaEnesaCargaController.
 */
@ManagedBean(name = "polizaEnesaCargaController")
@ViewScoped
public class PolizaEnesaCargaController implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 8750224739226085931L;

	/** The Constant LOGGER. */
	private static final Logger LOGGER = LogManager.getLogger(PolizaEnesaCargaController.class);

	/** The bundle. */
	@ManagedProperty("#{Msg}")
	private ResourceBundle bundle;

	/** The carga enesa BO. */
	@ManagedProperty(value = "#{cargaEnesaBO}")
	private CargaEnesaBO cargaEnesaBO;

	/** The carga BO. */
	@ManagedProperty(value = "#{cargaBO}")
	private CargaBO cargaBO;

	/** The documento BO. */
	@ManagedProperty(value = "#{documentoBO}")
	private DocumentoBO documentoBO;

	/** The poliza enesa BO. */
	@ManagedProperty(value = "#{polizaEnesaBO}")
	private PolizaEnesaBO polizaEnesaBO;

	/** The event publisher. */
	@ManagedProperty(value = "#{customEventPublisher}")
	private CustomEventPublisher eventPublisher;

	/** The app context. */
	@ManagedProperty(value = "#{appContext}")
	private AppContext appContext;

	/** The listado agricola. */
	private LazyDataModel<PolizaEnesaAuxiliarAgricola> listadoAgricola;
	
	/** The listado ganadera. */
	private LazyDataModel<PolizaEnesaAuxiliarGanadera> listadoGanadera;
	
	/** The listado retirada destruccion. */
	private LazyDataModel<PolizaEnesaAuxiliarRetiradaDestruccion> listadoRetiradaDestruccion;

	/** The listado documento agricola. */
	private List<Documento> listadoDocumentoAgricola;
	
	/** The listado documento retirada destruccion. */
	private List<Documento> listadoDocumentoRetiradaDestruccion;

	/** The errores. */
	private List<ErrorCargaEnesa> errores;

	/** The ultima carga enesa. */
	private Carga ultimaCargaEnesa;

    private List<ComparativaCargaDto> datos;     // Filas dinámicas
    private List<String> columnasDinamicas; // Ej: "CARGA22|PLAN2020"
    
    private List<ComparativaCargaDto> datosNuevaCarga;     // Filas dinámicas
    private List<String> columnasNuevaCarga; // Ej: "CARGA22|PLAN2020"
    
    private List<String> numerosCarga;
    private String numeroCargaSeleccionado;

	/**
	 * Inits the.
	 */
	@PostConstruct
	public void init() {
		updateListadoAgricola();
		listadoDocumentoAgricola = cargaEnesaBO.getListadoDocumentoAgricola();
		updateListadoGanadera();
		updateListadoRetiradaDestruccion();
		listadoDocumentoRetiradaDestruccion = cargaEnesaBO.getListadoDocumentoRetiradaDestruccion();
		ultimaCargaEnesa = cargaBO.getMaxCargaByTipo(CargaTipoCodigo.ENESA.name());
		errores = cargaEnesaBO.getErroresCarga();
	}

	/**
	 * Consolidar.
	 */
	public void consolidar() {
		LOGGER.info(Constantes.MENSAJE_CONSOLIDANDO_CARGA);
		// Comprobamos que estÃ¡n cargados todos los ficheros y que no se estÃ¡
		// consolidando
		if (!listadoDocumentoAgricola.isEmpty() && !listadoDocumentoRetiradaDestruccion.isEmpty() && !appContext
						.isRunningCargaEnesa()) {
			eventPublisher.publish(new StartCargaEvent(this));
			try {
				// eliminamos los datos de la tabla de errores
				cargaEnesaBO.deleteAllErrorCarga();
				// Comprobamos que los planes, lÃ­neas y mÃ³dulos existen en la
				// BD
				if (cargaEnesaBO.isCargaValida()) {
					if (!cargaEnesaBO.isConvocatoriasNuevas()) {
						cargaEnesaBO.consolidar();
						init();
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_INFO, bundle.getString(
										"carga.poliza.enesa.mensajes.success.consolidar.title"),
								bundle.getString(
										"carga.poliza.enesa.mensajes.success.consolidar.msg")));
						if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
							FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
									FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
									cargaEnesaBO.getListStringErroresExpReg().toString()));
						}
					}
					else {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, bundle.getString(
										"carga.poliza.enesa.mensajes.validate.carga.error.convocatoria.title"),
								bundle.getString(
										"carga.poliza.enesa.mensajes.validate.carga.error.convocatoria.msg")));
					}
				}
				else {
					errores = cargaEnesaBO.getErroresCarga();
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									"carga.poliza.enesa.mensajes.validate.carga.error.title"),
							bundle.getString(
									"carga.poliza.enesa.mensajes.validate.carga.error.msg")));
				}
			}
			catch (PssaException e) {
				LOGGER.error(e.getMessage(), e);
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e
								.getMessage()));
			}
			finally {
				eventPublisher.publish(new EndCargaEvent(this));
			}
		}
		else {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, bundle.getString(
							"carga.poliza.enesa.mensajes.validate.listado.error.title"), bundle
									.getString(
											"carga.poliza.enesa.mensajes.validate.listado.error.msg")));
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
		eventPublisher.publish(new StartSubirFicheroEnesaAgricolaEvent(this));
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
				documento = cargaEnesaBO.subirFicheroEnesaAgricolaCsv(event.getFile());

				// Guardamos los registros en una tabla auxiliar
				cargaEnesaBO.cargarFicheroEnesaAgricolaCsv(event, documento.getId());

				// Actualizamos los listados
				updateListadoAgricola();
				listadoDocumentoAgricola = cargaEnesaBO.getListadoDocumentoAgricola();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
						bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
				if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
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
			/** Si hay algÃºn error al subir el documento o al cargar los registros, eliminamos todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaEnesaBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaEnesaBO.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getMessage(), ofNullable(e.getParams())
									.orElse(""))));
			if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroEnesaAgricolaEvent(this));
	}

	/**
	 * Upload fichero agricola csv.
	 *
	 * @param event the event
	 * @throws Exception the exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void uploadFicheroAgricolaOds(FileUploadEvent event) throws Exception, IOException {

		// Obtenemos el nombre del fichero
		String fileName = event.getFile().getFileName();
		ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance()
				.getExternalContext().getContext();
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroEnesaAgricolaEvent(this));
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
				documento = cargaEnesaBO.subirFicheroEnesaAgricolaCsv(event.getFile());

				// Guardamos los registros en una tabla auxiliar
				cargaEnesaBO.cargarFicheroEnesaAgricolaOds(event, documento.getId());

				// Actualizamos los listados
				updateListadoAgricola();
				listadoDocumentoAgricola = cargaEnesaBO.getListadoDocumentoAgricola();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
						bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
				if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
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
			/** Si hay algÃºn error al subir el documento o al cargar los registros, eliminamos todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaEnesaBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaEnesaBO.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getMessage(), ofNullable(e.getParams())
									.orElse(""))));
			if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroEnesaAgricolaEvent(this));
	}


	/**
	 * Upload fichero agricola csv.
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
		eventPublisher.publish(new StartSubirFicheroEnesaRetiradaEvent(this));
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
				documento = cargaEnesaBO.subirFicheroEnesaRetiradaCsv(event.getFile());

				// Guardamos los registros en una tabla auxiliar
				cargaEnesaBO.cargarFicheroEnesaRetiradaCsv(event, documento.getId());

				// Actualizamos los listados
				updateListadoRetiradaDestruccion();
				
				listadoDocumentoRetiradaDestruccion = cargaEnesaBO.getListadoDocumentoRetiradaDestruccion();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.poliza.enesa.mensajes.success.upload.fichero.retirada.destruccion.title"),
						bundle.getString(
								"carga.poliza.enesa.mensajes.success.upload.fichero.retirada.destruccion.msg")));
				if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
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
			/** Si hay algÃºn error al subir el documento o al cargar los registros, eliminamos todo lo que se haya podido guardar en la BD */
			if (documento != null) {
				cargaEnesaBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaEnesaBO.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
					.getMessageParam(e.getParams())));
			if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroEnesaRetiradaEvent(this));
	}

	
	/**
	 * Upload fichero agricola csv.
	 *
	 * @param event the event
	 * @throws Exception the exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void uploadFicheroCsv(FileUploadEvent event) throws Exception, IOException {

		// Obtenemos el nombre del fichero
		String fileName = event.getFile().getFileName();
		ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance()
				.getExternalContext().getContext();
		Documento documento = null;
		boolean isRepeated = Boolean.FALSE;
		eventPublisher.publish(new StartSubirFicheroEnesaAgricolaEvent(this));
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
				documento = cargaEnesaBO.subirFicheroEnesaAgricolaCsv(event.getFile());

				// Guardamos los registros en una tabla auxiliar
				cargaEnesaBO.cargarFicheroEnesaAgricolaCsv(event, documento.getId());

				// Actualizamos los listados
				updateListadoAgricola();
				listadoDocumentoAgricola = cargaEnesaBO.getListadoDocumentoAgricola();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
						bundle.getString(
								Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
				if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
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
			// Si hay algÃºn error al subir el documento o al cargar los
			// registros, eliminamos todo lo que se haya podido guardar en la BD
			if (documento != null) {
				cargaEnesaBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaEnesaBO.getListStringErroresExpReg().toString()));
					}
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							Constantes.STRING_FORMAT_S_S, e.getMessage(), ofNullable(e.getParams())
									.orElse(""))));
			if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
								.getListStringErroresExpReg().toString()));
			}
		}
		eventPublisher.publish(new EndSubirFicheroEnesaAgricolaEvent(this));
	}

	/**
	 * Upload fichero agricola.
	 *
	 * @param event the event
	 * @throws Exception the exception
	 */
	public void uploadFicheroAgricola(FileUploadEvent event) throws Exception {
		String extensionFichero = (event.getFile().getFileName().substring(event.getFile()
				.getFileName().lastIndexOf(".") + 1));
		if (extensionFichero.equals("csv")) {
			uploadFicheroAgricolaCsv(event);

		}
		if (extensionFichero.equals("ods")) {
			uploadFicheroAgricolaOds(event);

		}

		else { // El fichero es xlsx
			Documento documento = null;
			boolean isRepeated = Boolean.FALSE;
			eventPublisher.publish(new StartSubirFicheroEnesaAgricolaEvent(this));
			try {
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
					documento = cargaEnesaBO.subirFicheroEnesaAgricola(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaEnesaBO.cargarFicheroEnesaAgricola(event.getFile(), documento.getId());

					// Actualizamos los listados
					updateListadoAgricola();
					listadoDocumentoAgricola = cargaEnesaBO.getListadoDocumentoAgricola();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_TITLE),
							bundle.getString(
									Constantes.CARGA_POLIZA_ENESA_MENSAJES_SUCCESS_UPLOAD_FICHERO_AGRICOLA_MSG)));
					if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaEnesaBO.getListStringErroresExpReg().toString()));
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
				// Si hay algÃºn error al subir el documento o al cargar los
				// registros, eliminamos todo lo que se haya podido guardar en
				// la BD
				if (documento != null) {
					cargaEnesaBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
					try {
						documentoBO.eliminarDocumentoById(documento);
					}
					catch (PssaException e1) {
						LOGGER.error(e1.getError().getMessage(), e1);
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
										.getError().getMessage()));
						if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
							FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
									FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
									cargaEnesaBO.getListStringErroresExpReg().toString()));
						}
					}
				}
				LOGGER.error(e.getError().getMessage(), e);
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO,e.getError()
						.getMessageParam(e.getParams())));
				if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
									.getListStringErroresExpReg().toString()));
				}
			}
			eventPublisher.publish(new EndSubirFicheroEnesaAgricolaEvent(this));
		}
	}

	

	public void prueba(){
		eventPublisher.publish(new StartCargaEvent(this));
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
		eventPublisher.publish(new StartSubirFicheroEnesaRetiradaEvent(this));
		try {
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
				documento = cargaEnesaBO.subirFicheroEnesaRetiradaDestruccion(event.getFile());

				// Guardamos los registros en una tabla auxiliar
				cargaEnesaBO.cargarFicheroEnesaRetiradaDestruccion(event.getFile(), documento
						.getId());

				// Actualizamos los listados
				updateListadoRetiradaDestruccion();
				
				listadoDocumentoRetiradaDestruccion = cargaEnesaBO
						.getListadoDocumentoRetiradaDestruccion();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.poliza.enesa.mensajes.success.upload.fichero.retirada.destruccion.title"),
						bundle.getString(
								"carga.poliza.enesa.mensajes.success.upload.fichero.retirada.destruccion.msg")));
				if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
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
			// Si hay algï¿½n error al subir el documento o al cargar los
			// registros, eliminamos todo lo que se haya podido guardar en la BD
			if (documento != null) {
				cargaEnesaBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
					if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
						FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
								FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS,
								cargaEnesaBO.getListStringErroresExpReg().toString()));
					}

				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
					.getMessageParam(e.getParams())));
			if (!cargaEnesaBO.getListStringErroresExpReg().isEmpty()) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, Constantes.ERRORES_DETECTADOS, cargaEnesaBO
								.getListStringErroresExpReg().toString()));
			}

		}
		eventPublisher.publish(new EndSubirFicheroEnesaRetiradaEvent(this));
		}
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
				cargaEnesaBO.eliminarPolizaAuxiliarAgricolaByDocumento(documento);
				cargaEnesaBO.eliminarPolizaAuxiliarGanaderaByDocumento(documento);
				cargaEnesaBO.eliminarPolizaAuxiliarRetiradaDestruccionByDocumento(documento);
				documentoBO.eliminarDocumentoById(documento);

				listadoDocumentoAgricola = cargaEnesaBO.getListadoDocumentoAgricola();
				listadoDocumentoRetiradaDestruccion = cargaEnesaBO
						.getListadoDocumentoRetiradaDestruccion();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.poliza.enesa.mensajes.eliminar.documento.title"),
						MessageFormat.format(bundle.getString(
								"carga.poliza.enesa.mensajes.validar.eliminar.documento.msg"),
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
	@SuppressWarnings({ "serial" })
	private void updateListadoAgricola() {
		listadoAgricola = new LazyDataModel<PolizaEnesaAuxiliarAgricola>() {

			@Override
			public List<PolizaEnesaAuxiliarAgricola> load(int first, int pageSize, String sortField,
					SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaEnesaBO.getRowCountListadoAgricolaAux(filters);

				listadoAgricola.setRowCount(rowCount);
				return polizaEnesaBO.getListadoAgricolaAux(filters, sortField, sortOrder.name(),
						rowBounds);
			}
		};
	}

	/**
	 * Update listado ganadera.
	 */
	@SuppressWarnings({ "serial" })
	private void updateListadoGanadera() {
		listadoGanadera = new LazyDataModel<PolizaEnesaAuxiliarGanadera>() {

			@Override
			public List<PolizaEnesaAuxiliarGanadera> load(int first, int pageSize, String sortField,
					SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaEnesaBO.getRowCountListadoGanaderaAux(filters);

				listadoGanadera.setRowCount(rowCount);
				return polizaEnesaBO.getListadoGanaderaAux(filters, sortField, sortOrder.name(),
						rowBounds);
			}
		};
	}

	/**
	 * Update listado retirada destruccion.
	 */
	@SuppressWarnings({ "serial" })
	private void updateListadoRetiradaDestruccion() {
		listadoRetiradaDestruccion = new LazyDataModel<PolizaEnesaAuxiliarRetiradaDestruccion>() {

			@Override
			public List<PolizaEnesaAuxiliarRetiradaDestruccion> load(int first, int pageSize,
					String sortField, SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = polizaEnesaBO.getRowCountListadoRetiradaDestruccionAux(filters);

				listadoRetiradaDestruccion.setRowCount(rowCount);
				return polizaEnesaBO.getListadoRetiradaDestruccionAux(filters, sortField, sortOrder
						.name(), rowBounds);
			}
		};
	}
	
	public void onTabChange(TabChangeEvent event) {

		if (event != null && event.getTab() != null && event.getTab().getId() != null) {
			if (event.getTab().getId().equals("tabComparativa")) {
				onNumeroCargaChange();
			} 
		}
	}

	public void onNumeroCargaChange(){
		List<Map<String, Integer>> cargaActualAgricola = polizaEnesaBO.getAllPolizaAuxAgricolaByPlan(); 
		List<Map<String, Integer>> cargaActualGanadera = polizaEnesaBO.getAllPolizaAuxGanaderaByPlan(); 
		numerosCarga = IntStream.rangeClosed(1, ultimaCargaEnesa.getNumeroCarga())
				.mapToObj((int i) -> String.valueOf(ultimaCargaEnesa.getNumeroCarga() - i + 1))
                .collect(Collectors.toList());
		List<Map<String, Integer>> cargaUltima = null;
		List<Map<String, Integer>> cargaUltimaGan = null;
		if(numeroCargaSeleccionado != null){
			cargaUltima = polizaEnesaBO.getAllPolizaByPlanTipo(Integer.valueOf(numeroCargaSeleccionado), TipoExplotacionCodigo.AGRICOLA.getCodigo());
			cargaUltimaGan = polizaEnesaBO.getAllPolizaByPlanTipo(Integer.valueOf(numeroCargaSeleccionado), TipoExplotacionCodigo.GANADO_NUEVO.getCodigo());
		} else {
			cargaUltima = polizaEnesaBO.getAllPolizaByPlanTipo(ultimaCargaEnesa.getNumeroCarga(), TipoExplotacionCodigo.AGRICOLA.getCodigo());
			cargaUltimaGan = polizaEnesaBO.getAllPolizaByPlanTipo(ultimaCargaEnesa.getNumeroCarga(), TipoExplotacionCodigo.GANADO_NUEVO.getCodigo());
		}
		
		 columnasDinamicas = new ArrayList<>();
		 columnasNuevaCarga = new ArrayList<>();

        datos = new ArrayList<>();

        ComparativaCargaDto fila1 = new ComparativaCargaDto(TipoExplotacionCodigo.AGRICOLA);
        ComparativaCargaDto fila2 = new ComparativaCargaDto(TipoExplotacionCodigo.GANADO_NUEVO);
        fila1.load(cargaUltima);
        fila2.load(cargaUltimaGan);
        // Generar claves de columnas
	    columnasDinamicas = cargaUltima.stream()
	    		    .map(mapa -> String.valueOf(mapa.get(Constantes.PLAN)))
	    		    .collect(Collectors.toList());
        datos.add(fila1);
        datos.add(fila2);

        // Generar claves de columnas
	    columnasNuevaCarga = cargaActualAgricola.stream()
	    		    .map(mapa -> String.valueOf(mapa.get(Constantes.PLAN)))
	    		    .collect(Collectors.toList());

        datosNuevaCarga = new ArrayList<>();
        ComparativaCargaDto fila3 = new ComparativaCargaDto(TipoExplotacionCodigo.AGRICOLA);
        fila3.load(cargaActualAgricola);
        ComparativaCargaDto fila4 = new ComparativaCargaDto(TipoExplotacionCodigo.GANADO_NUEVO);
        fila4.load(cargaActualGanadera);
        datosNuevaCarga.add(fila3);
        datosNuevaCarga.add(fila4);
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
	 * Gets the poliza enesa BO.
	 *
	 * @return the poliza enesa BO
	 */
	public PolizaEnesaBO getPolizaEnesaBO() {
		return polizaEnesaBO;
	}

	/**
	 * Sets the poliza enesa BO.
	 *
	 * @param polizaEnesaBO the new poliza enesa BO
	 */
	public void setPolizaEnesaBO(PolizaEnesaBO polizaEnesaBO) {
		this.polizaEnesaBO = polizaEnesaBO;
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
	public LazyDataModel<PolizaEnesaAuxiliarAgricola> getListadoAgricola() {
		return listadoAgricola;
	}

	/**
	 * Sets the listado agricola.
	 *
	 * @param listadoAgricola the new listado agricola
	 */
	public void setListadoAgricola(LazyDataModel<PolizaEnesaAuxiliarAgricola> listadoAgricola) {
		this.listadoAgricola = listadoAgricola;
	}

	/**
	 * Gets the listado ganadera.
	 *
	 * @return the listado ganadera
	 */
	public LazyDataModel<PolizaEnesaAuxiliarGanadera> getListadoGanadera() {
		return listadoGanadera;
	}

	/**
	 * Sets the listado ganadera.
	 *
	 * @param listadoGanadera the new listado ganadera
	 */
	public void setListadoGanadera(LazyDataModel<PolizaEnesaAuxiliarGanadera> listadoGanadera) {
		this.listadoGanadera = listadoGanadera;
	}

	/**
	 * Gets the listado retirada destruccion.
	 *
	 * @return the listado retirada destruccion
	 */
	public LazyDataModel<PolizaEnesaAuxiliarRetiradaDestruccion> getListadoRetiradaDestruccion() {
		return listadoRetiradaDestruccion;
	}

	/**
	 * Sets the listado retirada destruccion.
	 *
	 * @param listadoRetiradaDestruccion the new listado retirada destruccion
	 */
	public void setListadoRetiradaDestruccion(
			LazyDataModel<PolizaEnesaAuxiliarRetiradaDestruccion> listadoRetiradaDestruccion) {
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
	public List<ErrorCargaEnesa> getErrores() {
		return errores;
	}

	/**
	 * Sets the errores.
	 *
	 * @param errores the new errores
	 */
	public void setErrores(List<ErrorCargaEnesa> errores) {
		this.errores = errores;
	}

	/**
	 * Gets the ultima carga enesa.
	 *
	 * @return the ultima carga enesa
	 */
	public Carga getUltimaCargaEnesa() {
		return ultimaCargaEnesa;
	}

	/**
	 * Sets the ultima carga enesa.
	 *
	 * @param ultimaCargaEnesa the new ultima carga enesa
	 */
	public void setUltimaCargaEnesa(Carga ultimaCargaEnesa) {
		this.ultimaCargaEnesa = ultimaCargaEnesa;
	}

	/**
	 * Gets the carga enesa BO.
	 *
	 * @return the carga enesa BO
	 */
	public CargaEnesaBO getCargaEnesaBO() {
		return cargaEnesaBO;
	}

	/**
	 * Sets the carga enesa BO.
	 *
	 * @param cargaEnesaBO the new carga enesa BO
	 */
	public void setCargaEnesaBO(CargaEnesaBO cargaEnesaBO) {
		this.cargaEnesaBO = cargaEnesaBO;
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

	public List<ComparativaCargaDto> getDatos() {
		return datos;
	}

	public void setDatos(List<ComparativaCargaDto> datos) {
		this.datos = datos;
	}

	public List<String> getColumnasDinamicas() {
		return columnasDinamicas;
	}

	public void setColumnasDinamicas(List<String> columnasDinamicas) {
		this.columnasDinamicas = columnasDinamicas;
	}

	public List<ComparativaCargaDto> getDatosNuevaCarga() {
		return datosNuevaCarga;
	}

	public void setDatosNuevaCarga(List<ComparativaCargaDto> datosNuevaCarga) {
		this.datosNuevaCarga = datosNuevaCarga;
	}

	public List<String> getColumnasNuevaCarga() {
		return columnasNuevaCarga;
	}

	public void setColumnasNuevaCarga(List<String> columnasNuevaCarga) {
		this.columnasNuevaCarga = columnasNuevaCarga;
	}

	public List<String> getNumerosCarga() {
		return numerosCarga;
	}

	public void setNumerosCarga(List<String> numerosCarga) {
		this.numerosCarga = numerosCarga;
	}

	public String getNumeroCargaSeleccionado() {
		return numeroCargaSeleccionado;
	}

	public void setNumeroCargaSeleccionado(String numeroCargaSeleccionado) {
		this.numeroCargaSeleccionado = numeroCargaSeleccionado;
	}
	
	public String getNumeroCarga(){
		if(!StringUtils.isEmpty(numeroCargaSeleccionado)){
			Carga carga = cargaBO.getCargaByTipoNumero(CargaTipoCodigo.ENESA.name(), Integer.valueOf(numeroCargaSeleccionado));
			return numeroCargaSeleccionado + " - " + carga.getFechaCargaFormat();
		}else {
			return ultimaCargaEnesa.getNumeroCarga().toString() + " - " + ultimaCargaEnesa.getFechaCargaFormat();
		}
	}

}
