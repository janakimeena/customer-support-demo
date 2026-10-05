# Unit 3: Generative AI for Java developers

## Large language models

A large language model (LLM) predicts the next token from the tokens before it. It knows only what was in its training data, which has a cutoff date, and it can produce fluent but false statements, called hallucinations.

## Tokens and context window

Models read and write tokens, not characters; one token is roughly three to four characters of English text. The context window is the maximum number of tokens the model can consider at once, covering the system prompt, the conversation, any retrieved documents and the answer.

## Temperature

Temperature controls randomness in sampling. A temperature near 0 makes output nearly deterministic, which suits extraction, classification and query rewriting. Higher temperatures, such as 0.7 to 1.0, give more varied output for creative writing.

---

## Embeddings

An embedding is a vector of numbers that represents the meaning of a text. Texts with similar meaning have vectors that point in similar directions, measured with cosine similarity. The nomic-embed-text model produces 768-dimensional vectors; OpenAI's text-embedding-3-small produces 1536 dimensions. Embeddings from different models cannot be compared, so the vector store must be rebuilt when the embedding model changes.

## Retrieval-Augmented Generation (RAG)

RAG retrieves relevant chunks of your own documents at question time and places them in the prompt, so the model answers from that context instead of from memory alone. RAG has two pipelines: ingestion (read, chunk, embed, store) runs once, and the query pipeline (embed the question, search, augment the prompt, generate) runs for every question. RAG reduces hallucination and keeps answers current without retraining the model.

## Chunking

Documents are split into chunks before embedding. Chunks that are too large dilute the match with unrelated text; chunks that are too small lose context. A common starting point is around 512 tokens, then tune between 256 and 768 tokens using retrieval metrics.

---

## Evaluating RAG

Evaluate retrieval and generation separately. Hit@k measures whether any expected source appears in the top k results. Mean Reciprocal Rank (MRR) averages 1 divided by the rank of the first correct result, so it rewards ranking the right chunk first. Faithfulness checks that every claim in the answer is supported by the retrieved context. An LLM used as a judge should be different from, or stronger than, the model that generated the answer.

## Spring AI

Spring AI gives Java developers portable abstractions: `ChatClient` for prompts, `EmbeddingModel` for vectors, `VectorStore` for similarity search and `DocumentReader` for ingestion. RAG is implemented as an advisor in the ChatClient chain: `QuestionAnswerAdvisor` for simple similarity search, or `RetrievalAugmentationAdvisor` for a modular pipeline with query transformers, expanders, retrievers and post-processors.
