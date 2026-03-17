# Javelit Sample Project - CLAUDE Instructions

## Progetto: LlamaBench Visualizer

Applicazione Java basata su **Javelit** (framework per data apps ispirato a Streamlit) che visualizza in modo interattivo i dati di output di `llama-bench` da file JSON.

### Stack Tecnologico
- **Java JDK**: >= 21
- **Framework**: Javelit 0.86.0
- **Build Tool**: jbang (gestione automatica classpath)
- **JSON Parsing**: Jackson Databind
- **Grafici**: ECharts (via org.icepear.echarts)

### Esecuzione
```bash
# Installazione jbang javelit CLI (se non installato)
jbang app install --force javelit@javelit

# Esecuzione applicazione
cd /home/alessio/workspaces/javelit-sample-from-scratch
javelit run LlamaBenchViewer.java

# Il server web parte su http://localhost:8080
```

### Struttura File JSON llama-bench
File di esempio: `lama-bench-rocm72-step35-flash-r128Q4KM.json`

**Campi principali:**
- **Metadati hardware**: `cpu_info`, `gpu_info`, `backends`
- **Modello**: `model_type`, `model_size`, `model_n_params`
- **Configurazione**: `n_batch`, `n_threads`, `n_gpu_layers`, `flash_attn`
- **Parametri test**: `n_prompt`, `n_gen`, `n_depth` (context size)
- **Metriche performance**: `avg_ts` (tokens/sec), `stddev_ts`, `samples_ts`

### Javelit - API Rilevanti Verificate

#### File Uploader (Multi-file supportato)
```java
import io.javelit.components.media.FileUploaderComponent;

List<JtUploadedFile> files = Jt.fileUploader("Carica file JSON")
    .acceptMultipleFiles(FileUploaderComponent.MultipleFiles.TRUE)
    .type(Arrays.asList(".json", "application/json"))
    .use();
```

**JtUploadedFile record:**
- `filename()` - nome del file
- `contentType()` - MIME type
- `content()` - byte[] del contenuto

#### SelectBox
```java
String selected = Jt.selectbox("Label", listOfOptions)
    .index(0)  // valore di default (indice)
    .use();
```

#### Checkbox
```java
boolean checked = Jt.checkbox("Label")
    .value(true)  // valore di default
    .use();
```

#### ECharts Line Chart
```java
import org.icepear.echarts.Line;
import org.icepear.echarts.components.coord.cartesian.CategoryAxis;

Line chart = new Line()
    .setLegend()
    .setTooltip("item")
    .addXAxis(new CategoryAxis().setData(stringArray))  // asse X richiede String[]
    .addYAxis()                                          // NECESSARIO per renderizzazione
    .addSeries("Serie Name", numberArray);              // Number[]

Jt.echarts(chart).height(500).use();
```

**Nota importante:** `addYAxis()` è obbligatorio - senza di esso il grafico non si renderizza (errore JS: "Cannot read properties of undefined").

### Implementazione Attuale: LlamaBenchViewer.java

**File unico** con:
1. **Record BenchmarkEntry**: Mappa tutti i campi JSON rilevanti
2. **Metodo parseJsonFile()**: Parsing Jackson + trasformazione dati
3. **Flusso main**:
   - File uploader multi-file (con fallback su selectbox per file esistenti)
   - Summary tabella con hardware/modello
   - Checkbox per toggle serie grafico
   - Grafico ECharts: tokens/sec vs context depth
   - Tabella completa dati benchmark

### TODO Futuri
- [ ] Eventuale miglioramento UI/UX
- [ ] Supporto per confronto multi-file più avanzato
