# WordPiece vs BPE (Day 5)

A small Python script that shows how two subword tokenizers split the same support-ticket text, and why it
matters for token counts (cost and context window). It is separate from the Java service; it only needs
Python 3.10+ and Hugging Face's `tokenizers` library.

```sh
cd day5-support-service/tokenizer-demo
python3 -m venv .venv && source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python compare_tokenizers.py
```

The first run downloads two small tokenizer files (BERT and GPT-2) from the Hugging Face Hub; no model weights.

| Part | What it shows |
| --- | --- |
| 1. Pretrained | `bert-base-uncased` (WordPiece) vs `gpt2` (byte-level BPE) on six support sentences: `##` continuations vs `Ġ` spaces, lower-casing, `[CLS]`/`[SEP]`, `[UNK]` for an emoji vs byte fallback, foreign words costing more tokens |
| 2. Trained | Both algorithms trained on the same text with the same vocabulary size (120), so only the merge rule differs: frequent words become one token, rare ones are built from shared pieces |
| 3. Cost | Words vs tokens for one agent question, and why each provider counts differently |

Discussion questions:
1. Why does BERT produce `[UNK]` for the emoji while GPT-2 never does?
2. The German sentence needs many more tokens than an English one of the same length. What does that mean for cost and context window?
3. BPE merges the most frequent pair; WordPiece merges the pair that most improves likelihood. In Part 2, where do their vocabularies differ?
4. The Day 5 service estimates tokens with JTokkit but reports the provider's exact count. Why can the two differ?
