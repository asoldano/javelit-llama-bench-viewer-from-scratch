//JAVELIT
import io.javelit.core.Jt;
import io.javelit.core.JtUploadedFile;
import io.javelit.components.media.FileUploaderComponent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.icepear.echarts.Line;
import org.icepear.echarts.components.coord.cartesian.CategoryAxis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class LlamaBenchViewer {

    record BenchmarkEntry(
        String sourceFile,
        String cpuInfo,
        String gpuInfo,
        String backends,
        String modelType,
        int nPrompt,
        int nGen,
        int nDepth,
        double avgTs,
        double stddevTs,
        String buildCommit,
        Integer buildNumber,
        Instant testTime
    ) {}

    public static void main(String[] args) {
        Jt.title("llama-bench Visualizer").use();
        Jt.text("Visualizza le performance di llama-bench da file JSON.").use();

        // 1. File uploader multi-file con supporto per multiple uploads
        Jt.info("### Carica i file JSON").use();

        List<JtUploadedFile> uploadedFiles = Jt.fileUploader("Carica file JSON")
            .acceptMultipleFiles(FileUploaderComponent.MultipleFiles.TRUE)
            .type(Arrays.asList(".json", "application/json"))
            .use();

        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            // Fallback: mostra i file JSON disponibili nella directory corrente
            Jt.text("").use();  // spacer
            List<String> jsonFiles = findJsonFiles();

            if (!jsonFiles.isEmpty()) {
                Jt.info("### Oppure seleziona un file esistente").use();
                String selectedFile = Jt.selectbox("File JSON disponibili", jsonFiles).index(0).use();

                if (selectedFile != null) {
                    try {
                        byte[] content = Files.readString(Path.of(selectedFile)).getBytes();
                        uploadedFiles = Collections.singletonList(
                            new JtUploadedFile(selectedFile, "application/json", content)
                        );
                    } catch (IOException e) {
                        Jt.error("Errore nella lettura del file: " + e.getMessage()).use();
                        return;
                    }
                } else {
                    Jt.warning("Nessun file selezionato.").use();
                    return;
                }
            } else {
                Jt.warning("Nessun file JSON trovato. Carica un file o assicurati che ci siano file .json nella directory.").use();
                return;
            }
        }

        // 2. Parse dei file e estrazione dati
        Map<String, List<BenchmarkEntry>> allData = new LinkedHashMap<>();

        for (JtUploadedFile file : uploadedFiles) {
            try {
                String content = new String(file.content());
                List<BenchmarkEntry> entries = parseJsonFile(content, file.filename());
                allData.put(file.filename(), entries);
            } catch (Exception e) {
                Jt.error("Errore nella lettura del file " + file.filename() + ": " + e.getMessage()).use();
            }
        }

        if (allData.isEmpty()) {
            Jt.warning("Nessun dato valido estratto dai file.").use();
            return;
        }

        // 3. Summary cards con metadati
        Jt.divider().use();
        Jt.info("### Summary Hardware & Modello").use();

        Map<String, Object> summaryData = new LinkedHashMap<>();
        var firstFileEntry = allData.values().iterator().next().get(0);
        summaryData.put("CPU", firstFileEntry.cpuInfo());
        summaryData.put("GPU", firstFileEntry.gpuInfo());
        summaryData.put("Backend", firstFileEntry.backends());
        summaryData.put("Modello", firstFileEntry.modelType());

        Set<Integer> allDepths = new TreeSet<>();
        for (List<BenchmarkEntry> entries : allData.values()) {
            for (BenchmarkEntry entry : entries) {
                allDepths.add(entry.nDepth());
            }
        }
        summaryData.put("Context Sizes", allDepths.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(", ")));

        Jt.table(List.of(summaryData)).use();

        // 4. Checkbox group per toggle delle serie nel grafico
        Jt.divider().use();
        Jt.info("### Controlli Grafico").use();

        List<String> fileNames = new ArrayList<>(allData.keySet());
        Set<String> selectedFiles = new HashSet<>();

        for (String fileName : fileNames) {
            boolean checked = Jt.checkbox(fileName).value(true).use();
            if (checked) {
                selectedFiles.add(fileName);
            }
        }

        if (selectedFiles.isEmpty()) {
            Jt.warning("Seleziona almeno un file per visualizzare il grafico.").use();
        } else {
            // 5. Grafico ECharts multilinea: tokens/sec vs context depth
            Jt.divider().use();
            Jt.info("### Performance: Tokens/sec vs Context Depth").use();

            // Raggruppa dati per n_depth e calcola avg_ts per prefill (nGen=0) e decode (nPrompt=0)
            Map<Integer, Map<String, Double>> depthData = new TreeMap<>();

            for (String fileName : selectedFiles) {
                List<BenchmarkEntry> entries = allData.get(fileName);
                if (entries != null) {
                    for (BenchmarkEntry entry : entries) {
                        depthData.computeIfAbsent(entry.nDepth(), k -> new LinkedHashMap<>());
                        String seriesName = entry.nGen() > 0 ? "Decode" : "Prefill";
                        String key = fileName + " - " + seriesName;
                        depthData.get(entry.nDepth()).put(key, entry.avgTs());
                    }
                }
            }

            // Crea serie per il grafico
            Line lineChart = new Line()
                .setLegend()
                .setTooltip("item");

            // Aggiungi asse X con i valori di n_depth come stringhe (CategoryAxis)
            Integer[] depthValues = depthData.keySet().toArray(new Integer[0]);
            String[] depthLabels = Arrays.stream(depthValues).map(String::valueOf).toArray(String[]::new);

            lineChart.addXAxis(new CategoryAxis().setData(depthLabels));
            lineChart.addYAxis();  // Asse Y necessario per la renderizzazione

            // Aggiungi serie per ogni file+type combinato
            for (String fileName : selectedFiles) {
                List<BenchmarkEntry> entries = allData.get(fileName);
                if (entries == null) continue;

                // Serie Prefill (nGen=0, quindi nPrompt > 0)
                Map<Integer, Double> prefillData = new TreeMap<>();
                for (BenchmarkEntry entry : entries) {
                    if (entry.nGen() == 0 && entry.nPrompt() > 0) {
                        prefillData.put(entry.nDepth(), entry.avgTs());
                    }
                }

                if (!prefillData.isEmpty()) {
                    Number[] prefillValues = Arrays.stream(depthValues)
                        .map(d -> prefillData.getOrDefault(d, null))
                        .toArray(Number[]::new);
                    lineChart.addSeries(fileName + " - Prefill", prefillValues);
                }

                // Serie Decode (nPrompt=0, quindi nGen > 0)
                Map<Integer, Double> decodeData = new TreeMap<>();
                for (BenchmarkEntry entry : entries) {
                    if (entry.nPrompt() == 0 && entry.nGen() > 0) {
                        decodeData.put(entry.nDepth(), entry.avgTs());
                    }
                }

                if (!decodeData.isEmpty()) {
                    Number[] decodeValues = Arrays.stream(depthValues)
                        .map(d -> decodeData.getOrDefault(d, null))
                        .toArray(Number[]::new);
                    lineChart.addSeries(fileName + " - Decode", decodeValues);
                }
            }

            Jt.echarts(lineChart)
                .height(500)
                .use();

            Jt.text("Clicca sulla legenda per mostrare/nascondere le serie. X = Context Depth (tokens), Y = Tokens/sec").use();
        }

        // 6. Tabella completa con tutti i dati
        Jt.divider().use();
        Jt.info("### Dati Completi dei Benchmark").use();

        List<Map<String, Object>> tableRows = new ArrayList<>();

        for (Map.Entry<String, List<BenchmarkEntry>> entry : allData.entrySet()) {
            String fileName = entry.getKey();
            for (BenchmarkEntry bench : entry.getValue()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("File", fileName);
                row.put("Type", bench.nGen() > 0 ? "Decode" : "Prefill");
                row.put("Context Depth", bench.nDepth());
                row.put("n_prompt", bench.nPrompt());
                row.put("n_gen", bench.nGen());
                row.put("Tokens/sec (avg)", String.format("%.2f", bench.avgTs()));
                row.put("Stddev", String.format("%.4f", bench.stddevTs()));
                row.put("Build", bench.buildCommit() != null ? bench.buildCommit() : "N/A");
                tableRows.add(row);
            }
        }

        Jt.table(tableRows).use();
    }

    private static List<String> findJsonFiles() {
        List<String> jsonFiles = new ArrayList<>();
        try {
            Files.list(Path.of("."))
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString())
                .sorted()
                .forEach(jsonFiles::add);
        } catch (IOException e) {
            // Ignore
        }
        return jsonFiles;
    }

    private static List<BenchmarkEntry> parseJsonFile(String content, String sourceFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(content);

        if (!rootNode.isArray()) {
            throw new IllegalArgumentException("Il file JSON deve contenere un array di benchmark entries");
        }

        List<BenchmarkEntry> entries = new ArrayList<>();

        for (JsonNode node : rootNode) {
            String cpuInfo = node.has("cpu_info") ? node.get("cpu_info").asText() : "N/A";
            String gpuInfo = node.has("gpu_info") ? node.get("gpu_info").asText() : "N/A";
            String backends = node.has("backends") ? node.get("backends").asText() : "N/A";
            String modelType = node.has("model_type") ? node.get("model_type").asText() : "N/A";

            int nPrompt = node.has("n_prompt") ? node.get("n_prompt").asInt(0) : 0;
            int nGen = node.has("n_gen") ? node.get("n_gen").asInt(0) : 0;
            int nDepth = node.has("n_depth") ? node.get("n_depth").asInt(0) : 0;

            double avgTs = node.has("avg_ts") ? node.get("avg_ts").asDouble(0.0) : 0.0;
            double stddevTs = node.has("stddev_ts") ? node.get("stddev_ts").asDouble(0.0) : 0.0;

            String buildCommit = node.has("build_commit") && !node.get("build_commit").isNull()
                ? node.get("build_commit").asText() : null;
            Integer buildNumber = node.has("build_number") && !node.get("build_number").isNull()
                ? node.get("build_number").asInt() : null;

            Instant testTime = null;
            if (node.has("test_time")) {
                try {
                    testTime = Instant.parse(node.get("test_time").asText());
                } catch (Exception e) {
                    // Ignore parsing error
                }
            }

            entries.add(new BenchmarkEntry(
                sourceFile,
                cpuInfo,
                gpuInfo,
                backends,
                modelType,
                nPrompt,
                nGen,
                nDepth,
                avgTs,
                stddevTs,
                buildCommit,
                buildNumber,
                testTime
            ));
        }

        return entries;
    }
}
