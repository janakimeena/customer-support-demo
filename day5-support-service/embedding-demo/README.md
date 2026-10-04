# Fixed vs contextual embeddings (Day 5)

A small Python script showing the difference between **fixed (static)** embeddings, one vector per word as in
word2vec or GloVe, and **contextual** embeddings, where a word's vector depends on its sentence. It uses a
single Hugging Face model, `sentence-transformers/all-MiniLM-L6-v2` (90 MB): its input embedding table gives
the fixed vectors, its transformer output gives the contextual ones, so the only difference is context.

```sh
cd day5-support-service/embedding-demo
python3 -m venv .venv && source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install torch --index-url https://download.pytorch.org/whl/cpu
pip install -r requirements.txt
python compare_embeddings.py
```

| Part | What it shows |
| --- | --- |
| 1. Same word, different contexts | "charge" as a card charge, an invoice charge, a phone battery and "in charge": fixed vectors are identical (1.000), contextual ones separate the senses |
| 2. Sentence similarity | Paraphrase with different words, same words in a different order, negation, unrelated: where averaging fixed vectors fails and context helps, and where both still fail (negation) |

Output on CPU with `all-MiniLM-L6-v2` (cosine similarity):

| | Fixed | Contextual |
| --- | --- | --- |
| "charge" (card) vs "charge" (invoice) | 1.000 | 0.889 |
| "charge" (card) vs "charge" (phone battery) | 1.000 | 0.769 |
| "charge" (card) vs "in charge" | 1.000 | 0.557 |
| Paraphrase: "I want my money back." / "Please refund my order." | 0.473 | 0.547 |
| Unrelated: "Where is my parcel?" / "How do I reset my password?" | **0.543** | **0.153** |
| Word order swapped | 1.000 | 0.984 |
| Negation: "approved" / "not approved" | 0.965 | 0.914 |

Fixed vectors rate the unrelated pair above the paraphrase; contextual vectors separate them clearly.

Discussion questions:
1. Why is the fixed similarity of "charge" exactly 1.000 in every pair?
2. "I want my money back" and "Please refund my order" share almost no words. Which embedding finds them similar, and what does that mean for Day 6's search?
3. Both kinds score "approved" vs "not approved" as similar. Why is it still safe to use embeddings for retrieval in RAG?
4. Day 5's `/similarity` endpoint uses `nomic-embed-text` through Ollama. Is that fixed or contextual?
