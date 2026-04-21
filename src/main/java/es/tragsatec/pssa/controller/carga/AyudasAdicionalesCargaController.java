package es.tragsatec.pssa.controller.carga;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

import com.ibm.icu.text.MessageFormat;

import es.tragsatec.pssa.bo.carga.AyudasAdicionalesBO;
import es.tragsatec.pssa.bo.carga.CargaAyudasAdicionalesBO;
import es.tragsatec.pssa.bo.mantenimiento.PlanBO;
import es.tragsatec.pssa.bo.pago.DocumentoBO;
import es.tragsatec.pssa.common.PssaError;
import es.tragsatec.pssa.common.PssaException;
import es.tragsatec.pssa.constantes.Constantes;
import es.tragsatec.pssa.controller.consulta.AyudasAdicionalesController;
import es.tragsatec.pssa.events.CustomEventPublisher;
import es.tragsatec.pssa.events.EndSubirFicheroAyudasAdicionalesEvent;
import es.tragsatec.pssa.events.StartSubirFicheroAyudasAdicionalesEvent;
import es.tragsatec.pssa.model.AyudasAdicionalesAux;
import es.tragsatec.pssa.model.AyudasAdicionalesCargaAux;
import es.tragsatec.pssa.model.AyudasTiComAux;
import es.tragsatec.pssa.model.Documento;
import es.tragsatec.pssa.model.FicheroBf;
import es.tragsatec.pssa.model.Plan;
import es.tragsatec.pssa.model.TipoDocumento;
import es.tragsatec.pssa.model.TipoDocumentoCodigo;
import es.tragsatec.pssa.utilidades.AppContext;

@ManagedBean(name = "ayudasAdicionalesCargaController")
@ViewScoped
public class AyudasAdicionalesCargaController implements Serializable{
	
	private static final long serialVersionUID = -8297481856454619862L;

	
	private static final Logger LOGGER = LogManager.getLogger(AyudasAdicionalesController.class);
		
	private Map<String, Object> parameters = new HashMap<>();

	@ManagedProperty(value = "#{Msg}")
	private ResourceBundle bundle;
	
	@ManagedProperty(value = "#{customEventPublisher}")
	private CustomEventPublisher eventPublisher;
	
	@ManagedProperty(value = "#{appContext}")
	private AppContext appContext;
	
	@ManagedProperty(value = "#{documentoBO}")
	private DocumentoBO documentoBO;
	private List<AyudasAdicionalesCargaAux> listadoDocumentoCarga;
	private List<AyudasAdicionalesCargaAux> listadoDocumentoConsolidado;
	
	@ManagedProperty(value = "#{ayudasAdicionalesBO}")
	private AyudasAdicionalesBO ayudasAdicionalesBO;
	
	@ManagedProperty(value = "#{cargaAyudasAdicionalesBO}")
	private CargaAyudasAdicionalesBO cargaAyudasAdicionalesBO;
	
	@ManagedProperty(value = "#{planBO}")
	private PlanBO planBO;
	private Plan plan;
	private List<Plan> planes;
	
	//COMBO TIPO AYUDA
	private String tipoAyuda;
	/** private List<String> tipoAyudas; */
	private HashMap<String, String> tipoAyudas;
	
	//PARA CONSOLIDAR
	private String idCarga;
	private Integer carga;	
	private Date fecha;
	private Date fechaCarga;

	private LazyDataModel<AyudasAdicionalesAux> listadoAyudasAdicionalesCarga;
	private LazyDataModel<AyudasTiComAux> listadoAyudasTiComCarga;

	
	@PostConstruct
	public void init(){
		
		listadoDocumentoCarga = cargaAyudasAdicionalesBO.getListadoDocumento('N');
		listadoDocumentoConsolidado = cargaAyudasAdicionalesBO.getListadoDocumento('S');
		
		tipoAyudas = new HashMap<>();
		tipoAyudas.put("Ayudas adicionales", TipoDocumentoCodigo.AYUDA_ADIC.name());
		tipoAyudas.put("Titularidad compartida", TipoDocumentoCodigo.TIT_COMPAR.name());
		
		
		if(listadoDocumentoCarga.isEmpty()){
			planes = planBO.getPlanesMDD();
		} else {
			
			if(listadoDocumentoCarga.get(0).getDocumento().getTipoDoc().getCodTipoDocumento().equals(TipoDocumentoCodigo.AYUDA_ADIC.name())){
				if(tipoAyuda == null)
					tipoAyuda = TipoDocumentoCodigo.AYUDA_ADIC.name();
				updateListadoAyudasAdicionalesCarga();	 //CARGA
			}
			else {
				if(tipoAyuda == null)
					tipoAyuda = TipoDocumentoCodigo.TIT_COMPAR.name();
				updateListadoAyudasTiComCarga();
			}
			
		}
	}
	
	@SuppressWarnings("serial")
	private void updateListadoAyudasAdicionalesCarga(){

		listadoAyudasAdicionalesCarga = new LazyDataModel<AyudasAdicionalesAux>() {
			@Override
			public List<AyudasAdicionalesAux> load(int first, int pageSize, String sortField, SortOrder sortOrder,
					Map<String, Object> filters) {
				RowBounds rowBounds = new RowBounds(first, pageSize);
				
				int rowCount = ayudasAdicionalesBO.getRowCountListadoAyudasAdicionalesCarga(filters);
				listadoAyudasAdicionalesCarga.setRowCount(rowCount);
				return ayudasAdicionalesBO.getListadoAyudasAdicionalesCarga(filters, sortField, sortOrder.name(), rowBounds);

			}
		};
		
	}

	@SuppressWarnings("serial")
	private void updateListadoAyudasTiComCarga(){

		
		listadoAyudasTiComCarga = new LazyDataModel<AyudasTiComAux>() {
			
			//REVISAR ID DE DOCUMENTO, PARA QUE CARGUE BIEN
			@Override
			public List<AyudasTiComAux> load(int first, int pageSize, String sortField, SortOrder sortOrder,
			Map<String, Object> filters){
				
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = ayudasAdicionalesBO.getRowCountListadoAyudasTiComCarga(filters);
				
				listadoAyudasTiComCarga.setRowCount(rowCount);
				return ayudasAdicionalesBO.getListadoAyudasTiComCarga(filters, sortField, sortOrder.name(), rowBounds);
			}
		};
		
	}
	
	
	public void uploadFicheroAyudasAdicionales(FileUploadEvent event) {
		

		Documento documento = null;
		eventPublisher.publish(new StartSubirFicheroAyudasAdicionalesEvent(this));
		if(plan != null)
			parameters.put(Constantes.MDD_ID_PLAN, plan.getId());
		else
			parameters.put(Constantes.MDD_ID_PLAN, "");
		
		try {
			
			if (listadoDocumentoCarga.isEmpty()) {
				
				documento = cargaAyudasAdicionalesBO.subirFicheroAyudas(event.getFile(), tipoAyuda); // BBDD tipo de documento
				TipoDocumento tipoDoc = documento.getTipoDoc();

				parameters.put("idTipoDocumento", tipoDoc.getId());
				parameters.put("idDocumento", documento.getId());

				//AÑADE LA NUEVA CARGA Y RECIBE EL ID CORRESPONDIENTE
				int idNuevaCarga = ayudasAdicionalesBO.addIdCarga(documento.getId(), tipoDoc.getId());
				parameters.put("idCarga", idNuevaCarga);
				
				if(tipoAyuda.equals(TipoDocumentoCodigo.AYUDA_ADIC.name())){	//ayuda adicional
					cargaAyudasAdicionalesBO.cargarFicheroAyudasAdicionales(event.getFile(), parameters);
					updateListadoAyudasAdicionalesCarga();
				} else {	//tit compartida
					cargaAyudasAdicionalesBO.cargarFicheroAyudasTiCom(event.getFile(), parameters);
					updateListadoAyudasTiComCarga();
				}
				
				listadoDocumentoCarga = cargaAyudasAdicionalesBO.getListadoDocumento('N');
				
				FacesContext.getCurrentInstance().addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_INFO, bundle.getString("carga.ayudasAdicionales.carga.mensajes.success.upload.fichero.title"),
								MessageFormat.format(bundle.getString("carga.ayudasAdicionales.carga.mensajes.success.upload.fichero.msg"), event.getFile().getFileName())));
				
			} else {
				FacesContext.getCurrentInstance().addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_INFO, bundle.getString("carga.ayudasAdicionales.carga.mensajes.validate.upload.max.ficheros.title"),
								MessageFormat.format(bundle.getString("carga.ayudasAdicionales.carga.mensajes.validate.upload.max.ficheros.msg"), event.getFile().getFileName())));
			}
		} catch (PssaException e) {
			if (documento != null) {
				/** cargaAyudasAdicionalesBO.eliminarAyudaByDocumento(documento); */

				try {
					cargaAyudasAdicionalesBO.eliminarAyudaByDocumento(documento, false);
					documentoBO.eliminarDocumentoById(documento);
					
				} catch (PssaException e1) {
					LOGGER.error(e1.getError().getMessage(), e1);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
							Constantes.ERROR_MENSAJE_TITULO, e1.getError().getMessage()));
				}
			}
			LOGGER.error(e.getError().getMessage(), e);
			
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO,
							String.format(Constantes.STRING_FORMAT_S_S, 
									e.getError().getMessage(), 
									ofNullable(Arrays.toString((Object[]) e.getParams()))
										.orElse(""))));
		} catch (Exception e) {
			LOGGER.error(e, e);
			FacesContext.getCurrentInstance().addMessage("messageId", new FacesMessage(
					FacesMessage.SEVERITY_ERROR, "Error de sistema: " + e.getCause().getMessage(), ""));
		}
		eventPublisher.publish(new EndSubirFicheroAyudasAdicionalesEvent(this));
		/** tipoAyuda = ""; */
	}
	
	public void consolidarCarga(AyudasAdicionalesCargaAux carga){
		
		if(fechaCarga != null){
			
			int idDocumento = carga.getDocumento().getId();
			parameters.put("idDocumento", idDocumento);
			parameters.put("fechaCarga", fechaCarga);
			ayudasAdicionalesBO.updateCarga(fechaCarga, idDocumento);
			
			if(carga.getDocumento().getTipoDoc().getCodTipoDocumento().equals(TipoDocumentoCodigo.AYUDA_ADIC.name())){	//AYUDA ADICIONAL
				ayudasAdicionalesBO.consolidarAyuda();
				
			} else {  //TICOM
				ayudasAdicionalesBO.consolidarCargaTiCom();
			}
			
			eliminarDocumento(carga);
			
			listadoDocumentoCarga = cargaAyudasAdicionalesBO.getListadoDocumento('N');
			listadoDocumentoConsolidado = cargaAyudasAdicionalesBO.getListadoDocumento('S');
			
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_INFO, bundle.getString(
							"carga.ayudasAdicionales.mensajes.consolidar.documento.title"), bundle
									.getString(
											"carga.ayudasAdicionales.mensajes.consolidar.documento.msg")));
			tipoAyuda = "";
			
		} else {
			
			 FacesContext.getCurrentInstance().addMessage(null, 
			            new FacesMessage(FacesMessage.SEVERITY_ERROR, 
			            "ERROR", bundle.getString("carga.ayudasAdicionales.error.seleccionFecha")));
			
		}
	}
	
	public String redirigirConsulta(AyudasAdicionalesCargaAux documento){
		
		FacesContext.getCurrentInstance().getExternalContext()
        .getFlash().put("documentoSeleccionado", documento);
		
		return "/pages/secure/pagoAgroseguro/consultas/consultaAyudasAdicionales.xhtml?faces-redirect=true";
	}
	
	//AJAX
	public void onPlanChange(){
		//NO TIENE NADA, SOLO PARA QUE EL AJAX DE SELECT ONE MENU DE CONVOCATORIA FUNCIONE Y SE APLIQUE EN EL VALUE CONVOCATORIA
	}
	
	//AJAX
	public void onFechaCargaChange(){
		/** Ajax **/
	}
	
	//AJAX
	public void onFechaClean(){
		this.fechaCarga = null;
	}
	
	//AJAX
	public void getUltimaCarga(){
		carga = ayudasAdicionalesBO.getNumCargaByTipo(tipoAyuda);
		fecha = ayudasAdicionalesBO.getFechaUltimaCarga(tipoAyuda);
	}
	
	public void eliminarDocumento(AyudasAdicionalesCargaAux carga){
		
		try {
			
			if (!appContext.isRunningSubirFicheroAyudas()/* && !appContext
					.isRunningGuardarFicheroDireccion()*/) {
				
				if(carga.getConsolidado() == 'N' && fechaCarga == null){ //ELIMINADO DOCUMENTO CARGA

					cargaAyudasAdicionalesBO.eliminarAyudaByDocumento(carga.getDocumento(), false); 
					//necesito una que elimine carga_tmp
					documentoBO.eliminarDocumentoById(carga.getDocumento());
					
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
							FacesMessage.SEVERITY_INFO, 
							bundle.getString("carga.ayudasAdicionales.mensajes.eliminar.documento.title"),
							MessageFormat.format(bundle.getString(
								"carga.ayudasAdicionales.mensajes.eliminar.documento.msg"), carga.getDocumento()
										.getDesDocumento())));
					
					
				} else if(carga.getConsolidado() == 'N' && fechaCarga != null){ //CONSOLIDAR CARGA

					cargaAyudasAdicionalesBO.eliminarAyudaByDocumento(carga.getDocumento(), true);
					
				} 
//				else if(carga.getConsolidado() == 'S'){  //CARGA YA CONSOLIDADA, ELIMINANDO DEL REGISTRO DE CARGAS. DE MOMENTO, SOLO PARA ÁMBITO DE PRUEBAS.
//
//					ayudasAdicionalesBO.eliminarAyudaConsolidada(carga);  //ayuda y carga
//					documentoBO.eliminarDocumentoById(carga.getDocumento());  //doc
//					
//				}
				//REFRESCAR LISTA DE DOCUMENTOS
				listadoDocumentoCarga = cargaAyudasAdicionalesBO.getListadoDocumento('N');
				listadoDocumentoConsolidado = cargaAyudasAdicionalesBO.getListadoDocumento('S');
				tipoAyuda = "";
				planes = planBO.getPlanesMDD();
				
			}
			
		} catch (PssaException e) {
			LOGGER.error(e.getError().getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
					FacesMessage.SEVERITY_WARN, Constantes.ERROR_MENSAJE_TITULO, e.getError()
							.getMessage()));
		} catch (Exception e) {
			LOGGER.error(e, e);
			FacesContext.getCurrentInstance().addMessage("messageId", new FacesMessage(
					FacesMessage.SEVERITY_ERROR, "Error de sistema: " + e.getCause().getMessage(), ""));
		}
		
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
		} catch (Exception e) {
			LOGGER.error(e, e);
			FacesContext.getCurrentInstance().addMessage("messageId", new FacesMessage(
					FacesMessage.SEVERITY_ERROR, "Error de sistema: " + e.getCause().getMessage(), ""));
		}
	}
	

	//GETTERS SETTERS//
	
	public ResourceBundle getBundle() {
		return bundle;
	}

	public void setBundle(ResourceBundle bundle) {
		this.bundle = bundle;
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

	public DocumentoBO getDocumentoBO() {
		return documentoBO;
	}

	public void setDocumentoBO(DocumentoBO documentoBO) {
		this.documentoBO = documentoBO;
	}

	public AyudasAdicionalesBO getAyudasAdicionalesBO() {
		return ayudasAdicionalesBO;
	}

	public void setAyudasAdicionalesBO(AyudasAdicionalesBO ayudasAdicionalesBO) {
		this.ayudasAdicionalesBO = ayudasAdicionalesBO;
	}

	public CargaAyudasAdicionalesBO getCargaAyudasAdicionalesBO() {
		return cargaAyudasAdicionalesBO;
	}

	public void setCargaAyudasAdicionalesBO(CargaAyudasAdicionalesBO cargaAyudasAdicionalesBO) {
		this.cargaAyudasAdicionalesBO = cargaAyudasAdicionalesBO;
	}

	public LazyDataModel<AyudasAdicionalesAux> getListadoAyudasAdicionalesCarga() {
		return listadoAyudasAdicionalesCarga;
	}

	public void setListadoAyudasAdicionalesCarga(LazyDataModel<AyudasAdicionalesAux> listadoAyudasAdicionalesCarga) {
		this.listadoAyudasAdicionalesCarga = listadoAyudasAdicionalesCarga;
	}

	public static Logger getLogger() {
		return LOGGER;
	}

	public String getTipoAyuda() {
		return tipoAyuda;
	}


	public void setTipoAyuda(String tipoAyuda) {
		this.tipoAyuda = tipoAyuda;
	}


	public HashMap<String, String> getTipoAyudas() {
		return new HashMap<>(tipoAyudas);
	}


	public void setTipoAyudas(HashMap<String, String> tipoAyudas) {
		this.tipoAyudas = new HashMap<>(tipoAyudas);
	}


	public String getIdCarga() {
		return idCarga;
	}


	public void setIdCarga(String idCarga) {
		this.idCarga = idCarga;
	}


	public Integer getCarga() {
		return carga;
	}


	public void setCarga(Integer carga) {
		this.carga = carga;
	}


	public Date getFecha() {
		return fecha;
	}


	public void setFecha(Date fecha) {
		this.fecha = fecha;
	}


	public LazyDataModel<AyudasTiComAux> getListadoAyudasTiComCarga() {
		return listadoAyudasTiComCarga;
	}


	public void setListadoAyudasTiComCarga(LazyDataModel<AyudasTiComAux> listadoAyudasTiComCarga) {
		this.listadoAyudasTiComCarga = listadoAyudasTiComCarga;
	}

	public Map<String, Object> getParameters() {
		return parameters;
	}

	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}

	public Date getFechaCarga() {
		return fechaCarga;
	}

	public void setFechaCarga(Date fechaCarga) {
		this.fechaCarga = fechaCarga;
	}

	public List<AyudasAdicionalesCargaAux> getListadoDocumentoConsolidado() {
		return listadoDocumentoConsolidado;
	}

	public void setListadoDocumentoConsolidado(List<AyudasAdicionalesCargaAux> listadoDocumentoConsolidado) {
		this.listadoDocumentoConsolidado = listadoDocumentoConsolidado;
	}

	public List<AyudasAdicionalesCargaAux> getListadoDocumentoCarga() {
		return listadoDocumentoCarga;
	}

	public void setListadoDocumentoCarga(List<AyudasAdicionalesCargaAux> listadoDocumentoCarga) {
		this.listadoDocumentoCarga = listadoDocumentoCarga;
	}

	public PlanBO getPlanBO() {
		return planBO;
	}

	public void setPlanBO(PlanBO planBO) {
		this.planBO = planBO;
	}

	public Plan getPlan() {
		return plan;
	}

	public void setPlan(Plan plan) {
		this.plan = plan;
	}

	public List<Plan> getPlanes() {
		return planes;
	}

	public void setPlanes(List<Plan> planes) {
		this.planes = planes;
	}
	
	
	
	
}


