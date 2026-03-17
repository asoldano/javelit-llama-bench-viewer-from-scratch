# LlamaBench Visualizer

Visualizzatore interattivo per i risultati di benchmark di `llama-bench`, costruito con [Javelit](https://github.com/javelit/javelit).

![Demo](narrow.gif)

## Cosa fa

Carica file JSON generati da `llama-bench` e visualizza:
- Summary hardware e configurazione del modello
- Grafico interattivo tokens/sec vs context depth (con ECharts)
- Tabella completa di tutti i test di benchmark

## Come eseguirlo

```bash
# Assicurati di avere Java 21+ e jbang installato

cd ./javelit-sample-from-scratch

javelit run LlamaBenchViewer.java
```

L'applicazione apre automaticamente il browser su `http://localhost:8080`.

## Requisiti

- Java JDK >= 21
- [jbang](https://www.jbang.dev/) installato
