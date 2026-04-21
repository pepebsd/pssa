package es.tragsatec.pssa.controller.consulta;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;

import org.apache.ibatis.session.RowBounds;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

import es.tragsatec.pssa.bo.carga.AyudasAdicionalesBO;
import es.tragsatec.pssa.bo.mantenimiento.ConvocatoriaBO;
import es.tragsatec.pssa.bo.mantenimiento.PlanBO;
import es.tragsatec.pssa.model.AyudasAdicionalesAux;
import es.tragsatec.pssa.model.AyudasAdicionalesCargaAux;
import es.tragsatec.pssa.model.AyudasTiComAux;
import es.tragsatec.pssa.model.Convocatoria;
import es.tragsatec.pssa.model.Plan;
import es.tragsatec.pssa.model.TipoDocumento;
import es.tragsatec.pssa.model.TipoDocumentoCodigo;

//LOG DE ERRORES
//C:\sw\wls\logs\pssa

//LOS MANAGED PROPERTY TIENEN QUE TENER GET PARA QUE NO HAYA ERRORES

//SE USARÁ EN EL XHTML EN LA PARTE DE DATATABLE VALUE

//EL IMPORT CORRECTO PARA QUE EL EXPORT FUNCIONE
@ManagedBean(name = "ayudasAdicionalesController")
@ViewScoped // IMPORT FACES.BEAN.VIEWSCOPED PARA QUE EL EXPORT A EXCEL FUNCIONES
public class AyudasAdicionalesController implements Serializable {
	// C:\Users\rciscar.ext\Documents\PSSA_Workspace\.metadata\.plugins\org.eclipse.wst.server.core
	// BORRAR TODAS LAS CARPETAS QUE SE LLAMEN TEMP

	private static final long serialVersionUID = 2761199626226300026L;

	@ManagedProperty(value = "#{ayudasAdicionalesBO}")
	private AyudasAdicionalesBO ayudasAdicionalesBO;

//	@ManagedProperty(value = "#{convocatoriaBO}")
//	private ConvocatoriaBO convocatoriaBO;
//	private Integer convocatoria;
//	private List<Convocatoria> convocatorias;
	
	@ManagedProperty(value = "#{planBO}")
	private PlanBO planBO;
	private Integer idPlan;
	private List<Plan> planes;

	private TipoDocumento tipoDocumento;

	private List<AyudasAdicionalesCargaAux> ayudaCarga;
	private Integer carga;
	private List<Integer> cargas; // comprobar
	private String fechaCarga;
	private List<String> fechasCarga;

	private String soloAyuda;
	private List<String> soloAyudas;

	private String infoTiCom;
	private List<String> _infoTiCom;

	private String tipoAyuda;
	private HashMap<String, String> tipoAyudas;

	private LazyDataModel<AyudasAdicionalesAux> listadoAyudasAdicionales;
	private LazyDataModel<AyudasTiComAux> listadoAyudasTiComCarga;
	
	private AyudasAdicionalesCargaAux documentoConsolidado;
	
	
	private int puntoLinea;
	

	@PostConstruct
	public void init() {
		
		planes = planBO.getPlanesMDD();

		soloAyudas = new ArrayList<>();
		soloAyudas.add("Solo subvencionables");
		soloAyudas.add("Solo no subvencionables");
		soloAyudas.add("Todo");
		soloAyuda = soloAyudas.get(2);

		tipoAyudas = new HashMap<>();
		tipoAyudas.put("Ayudas adicionales", TipoDocumentoCodigo.AYUDA_ADIC.name());
		tipoAyudas.put("Titularidad compartida", TipoDocumentoCodigo.TIT_COMPAR.name());

		cargas = new ArrayList<>();
		fechasCarga = new ArrayList<>();
		ayudaCarga = new ArrayList<>();

		_infoTiCom = new ArrayList<>();
		_infoTiCom.add("Con titularidad compartida detallada");
		_infoTiCom.add("Con titularidad compartida");
		_infoTiCom.add("Sin titularidad compartida");
		infoTiCom = _infoTiCom.get(2);
		
		//SI SE REDIRIGE A ESTA PAGINA DESDE LA DE CARGAS->DOCUMENTOS CONSOLIDADOS, VA A SETEAR LOS PARAMETROS SEGUN EL DOCUMENTO QUE SE HAYA ELEGIDO
		if(FacesContext.getCurrentInstance()
				.getExternalContext().getFlash().get("documentoSeleccionado") != null){
			
			
			documentoConsolidado = (AyudasAdicionalesCargaAux) FacesContext.getCurrentInstance()
					.getExternalContext().getFlash().get("documentoSeleccionado");

			tipoAyuda = documentoConsolidado.getTipoDoc().getAbreviatura();
			onTipoAyudaChange();
			
			fechaCarga = documentoConsolidado.getFechaCarga();
			onFechaCargaChange();
			
			carga = documentoConsolidado.getNumCarga();
			
		}

	}

	public void buscar() {
		
		if (tipoAyuda != null) {

			if (tipoAyuda.equals(TipoDocumentoCodigo.AYUDA_ADIC.name())) {
				listadoAyudasTiComCarga = null;
				updateListadoAyudas();
			} else {
				listadoAyudasAdicionales = null;
				updateListadoAyudasTiCom();
			}
		}
	}


	@SuppressWarnings("serial")
	private void updateListadoAyudas() {
		listadoAyudasAdicionales = new LazyDataModel<AyudasAdicionalesAux>() {

			@Override
			public List<AyudasAdicionalesAux> load(int first, int pageSize, String sortField, SortOrder sortOrder,
					Map<String, Object> filters) {
				// DE QUE LINEA A QUE LINEA, FIRST SIENDO DONDE EMPIEZA Y
				// PAGESIZE LAS LINEAS QUE OCUPAN LA TABLA
				// EN ESA PAGINA
				RowBounds rowBounds = new RowBounds(first, pageSize);
				int rowCount = ayudasAdicionalesBO.getRowCountListadoAyudasAdicionales(idPlan, soloAyuda,
						tipoAyuda, 
						carga, 
						infoTiCom, 
						filters);
				listadoAyudasAdicionales.setRowCount(rowCount);
				
				List<AyudasAdicionalesAux> lista = ayudasAdicionalesBO.getListadoAyudasAdicionales(idPlan,
						soloAyuda, tipoAyuda, carga, infoTiCom, filters, sortField, sortOrder.name(), rowBounds);
				//Collections.sort(lista, Comparator.comparing(AyudasAdicionalesAux::getCIFDNI).reversed());
				return lista;

			}
		};

	}

	@SuppressWarnings("serial")
	private void updateListadoAyudasTiCom() {
		listadoAyudasTiComCarga = new LazyDataModel<AyudasTiComAux>() {

			@Override
			public List<AyudasTiComAux> load(int first, int pageSize, String sortField, SortOrder sortOrder,
					Map<String, Object> filters) {

				RowBounds rowBounds = new RowBounds(first, pageSize);

				int rowCount = ayudasAdicionalesBO.getRowCountListadoTiCom(idPlan, tipoAyuda, carga, filters);
				listadoAyudasTiComCarga.setRowCount(rowCount);
				return ayudasAdicionalesBO.getListadoTiCom(idPlan, tipoAyuda, carga, filters, sortField,
						sortOrder.name(), rowBounds);

			}
		};
	}

	/**
	 * AJAX, CUANDO SE CAMBIA EL SELECT ONE MENU DE TIPO DE AYUDA, SE TENDRÁ QUE HACER LA LISTA
	 * CORRESPONDIENTE DE SU NUMERO DE CARGAS
	 */
	public void onTipoAyudaChange() {
		
		//RESETEA LA CARGA.
		carga=null;
		if (cargas != null)
		    cargas.clear();
		fechaCarga="";
		if (fechasCarga != null) 
		    fechasCarga.clear();
		if(!ayudaCarga.isEmpty())
			ayudaCarga.clear();
		ayudaCarga = ayudasAdicionalesBO.getNumCargas(tipoAyuda, idPlan);
		
		
		List<String> fechasCargaDupl = new ArrayList<>();
		
		ayudaCarga.forEach(aux ->
			fechasCargaDupl.add(aux.getFechaCarga())
		);
		
		DateTimeFormatter formateador = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		
		//ELIMINA LAS FECHAS DUPLICADAS, SI LAS HAY.
		fechasCarga = fechasCargaDupl.stream().
				distinct().		//BORRA LOS DUPLICADOS
				map(fecha -> LocalDate.parse(fecha, formateador)).		//CAMBIA DE STRING A DATE
				sorted().		//ORDENA LAS FECHAS DE MÁS ANTIGUA A MAS CERCANA
				map(fecha -> fecha.format(formateador)).  //CAMBIA DE DATE A STRING
				collect(Collectors.toList());
	}
	
	public void onFechaCargaChange() {
		if(!cargas.isEmpty())
			cargas.clear();
		
		ayudaCarga.forEach(aux -> {
			
			if(aux.getFechaCarga().equals(fechaCarga))
				cargas.add(aux.getNumCarga());
			
		});
	}
	
	
	public void onPlanChange() {
		
		/** RESETEA TODOs PARA QUE NO HAYA CONFLICTOS Y ERRORES */
		
		if(!ayudaCarga.isEmpty())
			ayudaCarga.clear();
		if (cargas != null) 
		    cargas.clear();
		if (fechasCarga != null) 
		    fechasCarga.clear();
		this.tipoAyuda = "";
	    this.fechaCarga = "";
	    this.carga = null;
		soloAyuda = soloAyudas.get(2);
		infoTiCom = _infoTiCom.get(2);
	}
	
	public void obtenerPuntosLinea(ActionEvent event){
		puntoLinea = ayudasAdicionalesBO.getPuntoLineaByTipo(event.getComponent().getId());
	}

	/* GET SET */

	public LazyDataModel<AyudasAdicionalesAux> getListadoAyudasAdicionales() {
		return listadoAyudasAdicionales;
	}

	public void setListadoAyudasAdicionales(LazyDataModel<AyudasAdicionalesAux> listadoAyudasAdicionales) {
		this.listadoAyudasAdicionales = listadoAyudasAdicionales;
	}

	public AyudasAdicionalesBO getAyudasAdicionalesBO() {
		return ayudasAdicionalesBO;
	}

	public void setAyudasAdicionalesBO(AyudasAdicionalesBO ayudasAdicionalesBO) {
		this.ayudasAdicionalesBO = ayudasAdicionalesBO;
	}

	

	public PlanBO getPlanBO() {
		return planBO;
	}

	public void setPlanBO(PlanBO planBO) {
		this.planBO = planBO;
	}

	

	public Integer getIdPlan() {
		return idPlan;
	}

	public void setIdPlan(Integer idPlan) {
		this.idPlan = idPlan;
	}

	public List<Plan> getPlanes() {
		return planes;
	}

	public void setPlanes(List<Plan> planes) {
		this.planes = planes;
	}

	public AyudasAdicionalesCargaAux getDocumentoConsolidado() {
		return documentoConsolidado;
	}

	public void setDocumentoConsolidado(AyudasAdicionalesCargaAux documentoConsolidado) {
		this.documentoConsolidado = documentoConsolidado;
	}

	public Integer getCarga() {
		return carga;
	}

	public void setCarga(Integer carga) {
		this.carga = carga;
	}

	public String getSoloAyuda() {
		return soloAyuda;
	}

	public void setSoloAyuda(String soloAyuda) {
		this.soloAyuda = soloAyuda;
	}

	public List<String> getSoloAyudas() {
		return soloAyudas;
	}

	public void setSoloAyudas(List<String> soloAyudas) {
		this.soloAyudas = soloAyudas;
	}

	public String getTipoAyuda() {
		return tipoAyuda;
	}

	public void setTipoAyuda(String tipoAyuda) {
		this.tipoAyuda = tipoAyuda;
	}

	public HashMap<String, String> getTipoAyudas() {
		return tipoAyudas;
	}

	public void setTipoAyudas(HashMap<String, String> tipoAyudas) {
		this.tipoAyudas = tipoAyudas;
	}

	public LazyDataModel<AyudasTiComAux> getListadoAyudasTiComCarga() {
		return listadoAyudasTiComCarga;
	}

	public void setListadoAyudasTiComCarga(LazyDataModel<AyudasTiComAux> listadoAyudasTiComCarga) {
		this.listadoAyudasTiComCarga = listadoAyudasTiComCarga;
	}

	public String getInfoTiCom() {
		return infoTiCom;
	}

	public void setInfoTiCom(String infoTiCom) {
		this.infoTiCom = infoTiCom;
	}

	public List<String> get_infoTiCom() {
		return _infoTiCom;
	}

	public void set_infoTiCom(List<String> _infoTiCom) {
		this._infoTiCom = _infoTiCom;
	}

	public TipoDocumento getTipoDocumento() {
		return tipoDocumento;
	}

	public void setTipoDocumento(TipoDocumento tipoDocumento) {
		this.tipoDocumento = tipoDocumento;
	}

	public String getFechaCarga() {
		return fechaCarga;
	}

	public void setFechaCarga(String fechaCarga) {
		this.fechaCarga = fechaCarga;
	}

	public List<AyudasAdicionalesCargaAux> getAyudaCarga() {
		return ayudaCarga;
	}

	public void setAyudaCarga(List<AyudasAdicionalesCargaAux> ayudaCarga) {
		this.ayudaCarga = ayudaCarga;
	}

	public List<Integer> getCargas() {
		return cargas;
	}

	public void setCargas(List<Integer> cargas) {
		this.cargas = cargas;
	}

	public List<String> getFechasCarga() {
		return fechasCarga;
	}

	public void setFechasCarga(List<String> fechasCarga) {
		this.fechasCarga = fechasCarga;
	}

	public int getPuntoLinea() {
		return puntoLinea;
	}

	public void setPuntoLinea(int puntoLinea) {
		this.puntoLinea = puntoLinea;
	}
	
}

