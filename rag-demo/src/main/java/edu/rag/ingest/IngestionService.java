package edu.rag.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Section 4: Extract (DocumentReader) -> Transform (TokenTextSplitter) -> Load (VectorStore.add). */
@Service
public class IngestionService {

    // "unit3-genai-notes.md" -> unit 3, so retrieval can be filtered per unit (section 6.3)
    private static final Pattern UNIT = Pattern.compile("^unit(\\d+)-");

    private final VectorStore vectorStore;
    private final Resource[] docs;

    public IngestionService(VectorStore vectorStore,
                            @Value("${rag.docs-path}") Resource[] docs) {
        this.vectorStore = vectorStore;
        this.docs = docs;
    }

    public int ingest() {
        // 1. EXTRACT
        List<Document> raw = new ArrayList<>();
        for (Resource r : docs) {
            String name = r.getFilename();
            if (name == null) continue;
            if (name.endsWith(".pdf")) {
                raw.addAll(readPdf(r));
            } else if (name.endsWith(".md")) {
                raw.addAll(readMarkdown(r));
            }
        }

        // Tag every document so we can filter and cite later
        raw.forEach(d -> {
            String source = String.valueOf(d.getMetadata().getOrDefault("file_name", "unknown"));
            d.getMetadata().putIfAbsent("source", source);
            d.getMetadata().put("course", "java-genai");
            Matcher m = UNIT.matcher(source);
            if (m.find()) {
                d.getMetadata().put("unit", Integer.parseInt(m.group(1)));
            }
        });

        // 2. TRANSFORM: split into ~512-token chunks
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
        List<Document> chunks = splitter.apply(raw);

        // 3. LOAD: embeds and stores (PGvector batches internally)
        vectorStore.add(chunks);
        return chunks.size();
    }

    private List<Document> readPdf(Resource r) {
        var config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfTopTextLinesToDelete(0)   // raise to strip running headers
                        .build())
                .withPagesPerDocument(1)                       // one Document per page -> page metadata
                .build();
        return new PagePdfDocumentReader(r, config).get();
    }

    private List<Document> readMarkdown(Resource r) {
        var config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(true)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata("file_name", r.getFilename())
                .build();
        return new MarkdownDocumentReader(r, config).get();
    }

    public boolean isEmpty() {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query("probe").topK(1).build()).isEmpty();
    }
}
