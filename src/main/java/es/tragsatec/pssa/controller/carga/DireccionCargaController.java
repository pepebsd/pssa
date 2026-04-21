package es.tragsatec.pssa.controller.carga;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
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

import org.apache.ibatis.session.RowBounds;
import org.apache.logging.log4j.LogManager;import org.apache.logging.log4j.Logger;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

import com.ibm.icu.text.MessageFormat;

import es.tragsatec.pssa.bo.carga.CargaDireccionBO;
import es.tragsatec.pssa.bo.carga.DireccionBO;
import es.tragsatec.pssa.bo.pago.DocumentoBO;
import es.tragsatec.pssa.common.PssaError;
import es.tragsatec.pssa.common.PssaException;
import es.tragsatec.pssa.constantes.Constantes;
import es.tragsatec.pssa.events.CustomEventPublisher;
import es.tragsatec.pssa.events.EndGuardarFicheroDireccionEvent;
import es.tragsatec.pssa.events.EndSubirFicheroDireccionEvent;
import es.tragsatec.pssa.events.StartSubirFicheroDireccionEvent;
import es.tragsatec.pssa.model.DireccionAuxiliarGIRO;
import es.tragsatec.pssa.model.DireccionAuxiliarSAAC;
import es.tragsatec.pssa.model.Documento;
import es.tragsatec.pssa.model.FicheroBf;
import es.tragsatec.pssa.model.ProcedenciaDireccion;
import es.tragsatec.pssa.model.ProcedenciaDireccionCodigo;
import es.tragsatec.pssa.model.TipoDocumento;
import es.tragsatec.pssa.model.TipoDocumentoCodigo;
import es.tragsatec.pssa.utilidades.AppContext;

@ManagedBean(name = "direccionCargaController")
@ViewScoped
public class DireccionCargaController implements Serializable {

	private static final long serialVersionUID = 7104991799914023788L;

	public static final Logger LOGGER = LogManager.getLogger(DireccionCargaController.class);

	@ManagedProperty("#{Msg}")
	private ResourceBundle bundle;

	@ManagedProperty(value = "#{cargaDireccionBO}")
	private CargaDireccionBO cargaDireccionBO;

	@ManagedProperty(value = "#{direccionBO}")
	private DireccionBO direccionBO;

	@ManagedProperty(value = "#{documentoBO}")
	private DocumentoBO documentoBO;

	@ManagedProperty(value = "#{customEventPublisher}")
	private CustomEventPublisher eventPublisher;

	@ManagedProperty(value = "#{appContext}")
	private AppContext appContext;

	private List<Documento> listadoDocumento;

	private LazyDataModel<DireccionAuxiliarGIRO> listadoDireccionGIRO;

	private LazyDataModel<DireccionAuxiliarSAAC> listadoDireccionSAAC;

	private String procedencia;
	private List<ProcedenciaDireccion> procedencias;

	@PostConstruct
	public void init() {
		updateListadoDireccion();
		listadoDocumento = cargaDireccionBO.getListadoDocumento();

		procedencias = getProcedenciasCombo();

		if (!listadoDocumento.isEmpty()) {
			TipoDocumento tipoDocumento = listadoDocumento.get(0).getTipoDoc();
			if (tipoDocumento.getCodTipoDocumento().equals(TipoDocumentoCodigo.DIR_GIRO.name())) {
				procedencia = ProcedenciaDireccionCodigo.GIRO.name();
			}
			if (tipoDocumento.getCodTipoDocumento().equals(TipoDocumentoCodigo.DIR_SAAC.name())) {
				procedencia = ProcedenciaDireccionCodigo.FICHERO_SAAC.name();
			}
		}
	}

	public void uploadFicheroDireccion(FileUploadEvent event) {
		Documento documento = null;
		eventPublisher.publish(new StartSubirFicheroDireccionEvent(this));
		try {
			if (listadoDocumento.isEmpty()) {
				if (procedencia.equals(ProcedenciaDireccionCodigo.GIRO.name())) {
					// Subimos el fichero al directorio de oracle
					documento = cargaDireccionBO.subirFicheroGiro(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaDireccionBO.cargarFicheroGIRO(event.getFile(), documento.getId());
				}
				if (procedencia.equals(ProcedenciaDireccionCodigo.FICHERO_SAAC.name())) {
					// Subimos el fichero al directorio de oracle
					documento = cargaDireccionBO.subirFicheroSAAC(event.getFile());

					// Guardamos los registros en una tabla auxiliar
					cargaDireccionBO.cargarFicheroSAAC(event.getFile(), documento.getId());
				}
				// Actualizamos los listados
				updateListadoDireccion();
				listadoDocumento = cargaDireccionBO.getListadoDocumento();

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_INFO, bundle.getString(
								"carga.direccion.mensajes.success.upload.fichero.title"),
						MessageFormat.format(bundle.getString(
								"carga.direccion.mensajes.success.upload.fichero.msg"), event
										.getFile().getFileName())));
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								"carga.direccion.mensajes.validate.upload.max.ficheros.title"),
						MessageFormat.format(bundle.getString(
								"carga.direccion.mensajes.validate.upload.max.ficheros.msg"), event
										.getFile().getFileName())));
			}
		}
		catch (PssaException e) {
			// Si hay algún error al subir el documento o al cargar los
			// registros, eliminamos todo lo que se haya podido guardar en la BD
			if (documento != null) {
				cargaDireccionBO.eliminarDireccionGIROByDocumento(documento);
				cargaDireccionBO.eliminarDireccionSAACByDocumento(documento);
				try {
					documentoBO.eliminarDocumentoById(documento);
				}
				catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e1
									.getError().getMessage()));
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							"%s %s", e.getError().getMessage(), ofNullable(e.getParams()).orElseGet(
									""))));
		}
		eventPublisher.publish(new EndSubirFicheroDireccionEvent(this));
	}

	public void consolidar() {
		try {
			// Comprobamos si hay alguna dirección para guardar y que no se esté
			// subiendo ningún fichero
			if (!appContext.isRunningSubirFicheroDireccion()) {
				if (!listadoDocumento.isEmpty()) {
					// Guardamos las direcciones
					cargaDireccionBO.consolidar();

					// Eliminamos las tablas auxiliares
					cargaDireccionBO.eliminarAllDireccionGIRO();
					cargaDireccionBO.eliminarAllDireccionSAAC();

					// Eliminamos los documentos
					documentoBO.eliminarDocumentoByTipo(TipoDocumentoCodigo.DIR_GIRO,
							TipoDocumentoCodigo.DIR_SAAC);

					// Actualizamos los listados
					updateListadoDireccion();
					listadoDocumento = cargaDireccionBO.getListadoDocumento();
					procedencia = null;

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.direccion.mensajes.success.consolidar.direccion.title"),
							bundle.getString(
									"carga.direccion.mensajes.success.consolidar.direccion.msg")));

				}
				else {
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_WARN, bundle.getString(
									"carga.direccion.mensajes.guardar.no.fichero.error.title"),
							bundle.getString(
									"carga.direccion.mensajes.guardar.no.fichero.error.msg")));
				}

			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								"carga.direccion.mensajes.subir.fichero.error.title"), bundle
										.getString(
												"carga.direccion.mensajes.subir.fichero.error.msg")));
			}
		}
		catch (PssaException e) {
			LOGGER.error(e.getError().getMessage(), e);
			eventPublisher.publish(new EndGuardarFicheroDireccionEvent(this));
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, String.format(
							"%s %s", e.getError().getMessage(), ofNullable(e.getParams()).orElse(
									""))));
		}

		eventPublisher.publish(new EndGuardarFicheroDireccionEvent(this));
	}

	// Listener usado para habilitar/deshabilitar la subida de ficheros.
	public void onProcedenciaChange() {
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
			if (!appContext.isRunningSubirFicheroDireccion() && !appContext
					.isRunningGuardarFicheroDireccion()) {
				if (documento != null) {
					cargaDireccionBO.eliminarDireccionGIROByDocumento(documento);
					cargaDireccionBO.eliminarDireccionSAACByDocumento(documento);
					documentoBO.eliminarDocumentoById(documento);

					listadoDocumento = cargaDireccionBO.getListadoDocumento();

					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, bundle.getString(
									"carga.direccion.mensajes.eliminar.documento.title"),
							MessageFormat.format(bundle.getString(
									"carga.direccion.mensajes.eliminar.documento.msg"), documento
											.getDesDocumento())));
				}
			}
			else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
						FacesMessage.SEVERITY_WARN, bundle.getString(
								"carga.direccion.mensajes.eliminar.fichero.error.title"), bundle
										.getString(
												"carga.direccion.mensajes.eliminar.fichero.error.msg")));
			}
		}
		catch (PssaException e) {
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
							.getMessage()));
		}
	}

	private List<ProcedenciaDireccion> getProcedenciasCombo() {
		List<ProcedenciaDireccion> procedencias = new ArrayList<>();
		procedencias.add(direccionBO.getProcedenciaByCodigo(ProcedenciaDireccionCodigo.GIRO
				.name()));
		procedencias.add(direccionBO.getProcedenciaByCodigo(ProcedenciaDireccionCodigo.FICHERO_SAAC
				.name()));
		return procedencias;
	}

	@SuppressWarnings("serial")
	private void updateListadoDireccion() {
		listadoDireccionGIRO = new LazyDataModel<DireccionAuxiliarGIRO>() {

			@Override
			public List<DireccionAuxiliarGIRO> load(int first, int pageSize, String sortField,
					SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = direccionBO.getRowCountListadoDireccionGIROAux(filters);

				listadoDireccionGIRO.setRowCount(rowCount);
				return direccionBO.getListadoDireccionGIROAux(filters, sortField, sortOrder.name(),
						rowBounds);
			}
		};

		listadoDireccionSAAC = new LazyDataModel<DireccionAuxiliarSAAC>() {

			@Override
			public List<DireccionAuxiliarSAAC> load(int first, int pageSize, String sortField,
					SortOrder sortOrder, Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = direccionBO.getRowCountListadoDireccionSAACAux(filters);

				listadoDireccionSAAC.setRowCount(rowCount);
				return direccionBO.getListadoDireccionSAACAux(filters, sortField, sortOrder.name(),
						rowBounds);
			}
		};
	}

	public ResourceBundle getBundle() {
		return bundle;
	}

	public void setBundle(ResourceBundle bundle) {
		this.bundle = bundle;
	}

	public CargaDireccionBO getCargaDireccionBO() {
		return cargaDireccionBO;
	}

	public void setCargaDireccionBO(CargaDireccionBO cargaDireccionBO) {
		this.cargaDireccionBO = cargaDireccionBO;
	}

	public DireccionBO getDireccionBO() {
		return direccionBO;
	}

	public void setDireccionBO(DireccionBO direccionBO) {
		this.direccionBO = direccionBO;
	}

	public DocumentoBO getDocumentoBO() {
		return documentoBO;
	}

	public void setDocumentoBO(DocumentoBO documentoBO) {
		this.documentoBO = documentoBO;
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

	public List<Documento> getListadoDocumento() {
		return listadoDocumento;
	}

	public void setListadoDocumento(List<Documento> listadoDocumento) {
		this.listadoDocumento = listadoDocumento;
	}

	public LazyDataModel<DireccionAuxiliarGIRO> getListadoDireccionGIRO() {
		return listadoDireccionGIRO;
	}

	public void setListadoDireccionGIRO(LazyDataModel<DireccionAuxiliarGIRO> listadoDireccionGIRO) {
		this.listadoDireccionGIRO = listadoDireccionGIRO;
	}

	public LazyDataModel<DireccionAuxiliarSAAC> getListadoDireccionSAAC() {
		return listadoDireccionSAAC;
	}

	public void setListadoDireccionSAAC(LazyDataModel<DireccionAuxiliarSAAC> listadoDireccionSAAC) {
		this.listadoDireccionSAAC = listadoDireccionSAAC;
	}

	public String getProcedencia() {
		return procedencia;
	}

	public void setProcedencia(String procedencia) {
		this.procedencia = procedencia;
	}

	public List<ProcedenciaDireccion> getProcedencias() {
		return procedencias;
	}

	public void setProcedencias(List<ProcedenciaDireccion> procedencias) {
		this.procedencias = procedencias;
	}
}