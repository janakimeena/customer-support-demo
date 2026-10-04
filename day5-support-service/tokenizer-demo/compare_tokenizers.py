"""
Day 5: WordPiece vs Byte Pair Encoding (BPE), side by side.

Part 1 uses two real pretrained tokenizers from the Hugging Face Hub:
  - bert-base-uncased : WordPiece  (continuation pieces start with "##")
  - gpt2              : byte-level BPE (a leading space is part of the token, shown as "Ġ")

Part 2 trains both algorithms on the same small text with the same vocabulary size, so the only
difference is the algorithm, not the training data.

Run:
  python3 -m venv .venv && source .venv/bin/activate
  pip install -r requirements.txt
  python compare_tokenizers.py
"""

from tokenizers import Tokenizer, models, normalizers, pre_tokenizers, trainers

SENTENCES = [
    "My refund hasn't arrived yet.",
    "I was charged twice for order #5521!",
    "Two-step verification locked me out of my account.",
    "Unbelievably, the parcel arrived smashed.",
    "Kundenservice antwortet nicht.",          # German: words the English vocabularies rarely saw
    "Refund 😡 please",                         # an emoji
]


def show(name, tokenizer, text):
    encoding = tokenizer.encode(text)
    print(f"  {name:<10} {len(encoding.tokens):>3} tokens  {encoding.tokens}")


def part1_pretrained():
    print("=" * 100)
    print("PART 1: pretrained tokenizers (downloaded once from the Hugging Face Hub)")
    print("=" * 100)
    wordpiece = Tokenizer.from_pretrained("bert-base-uncased")
    bpe = Tokenizer.from_pretrained("gpt2")
    print(f"vocabulary size: WordPiece (BERT) = {wordpiece.get_vocab_size()}, BPE (GPT-2) = {bpe.get_vocab_size()}\n")

    for text in SENTENCES:
        print(text)
        show("WordPiece", wordpiece, text)
        show("BPE", bpe, text)
        print()

    print("What to notice:")
    print("  - WordPiece lower-cases (this BERT model is 'uncased') and marks word continuations with '##'.")
    print("  - BERT adds [CLS] and [SEP]: special tokens the model expects around every input.")
    print("  - GPT-2 BPE keeps case and spaces: 'Ġrefund' (with a space) and 'refund' are different tokens.")
    print("  - The emoji: WordPiece has no piece for it and gives [UNK] (the meaning is lost);")
    print("    byte-level BPE never needs [UNK], it falls back to raw bytes (several odd-looking tokens).")
    print("  - Rare or foreign words split into more pieces: more tokens = more cost and less context room.\n")


def part2_trained():
    print("=" * 100)
    print("PART 2: train both algorithms on the same text, same vocabulary size")
    print("=" * 100)
    corpus = [
        "refund refunds refunded refunding non-refundable",
        "charge charges charged charging overcharged",
        "deliver delivery delivered undelivered redelivery",
        "verify verification verified unverified",
        "account accounts sign in signed sign-in password reset",
        "the parcel arrived damaged and the customer wants a refund",
        "the customer was charged twice and wants the second charge refunded",
        "two-step verification locked the customer out of the account",
    ] * 20
    vocab_size = 120

    # WordPiece: merges the pair that most increases the likelihood of the training text
    # (frequency of the pair relative to the frequency of its parts).
    wp = Tokenizer(models.WordPiece(unk_token="[UNK]"))
    wp.normalizer = normalizers.Lowercase()
    wp.pre_tokenizer = pre_tokenizers.Whitespace()
    wp.train_from_iterator(corpus, trainers.WordPieceTrainer(vocab_size=vocab_size, special_tokens=["[UNK]"]))

    # BPE: merges the most frequent pair of symbols, again and again, until the vocabulary is full.
    bpe = Tokenizer(models.BPE(unk_token="[UNK]"))
    bpe.normalizer = normalizers.Lowercase()
    bpe.pre_tokenizer = pre_tokenizers.Whitespace()
    bpe.train_from_iterator(corpus, trainers.BpeTrainer(vocab_size=vocab_size, special_tokens=["[UNK]"]))

    print(f"both trained to {vocab_size} tokens on the same {len(corpus)} lines\n")
    for text in ["refunded", "overcharged", "redelivery", "unverified", "refundable charges",
                 "zebra"]:   # 'zebra' never appeared in training
        print(text)
        show("WordPiece", wp, text)
        show("BPE", bpe, text)
        print()

    print("What to notice:")
    print("  - Same idea (start from characters, merge into subwords), different merge rule.")
    print("  - WordPiece marks continuations with '##'; BPE pieces carry no marker here.")
    print("  - Frequent words become one token ('refunded'); rarer ones are built from pieces they share with")
    print("    others ('over' + 'charged'): that is how a fixed vocabulary covers words it never stored whole.")
    print("  - With this tiny vocabulary, BPE kept longer pieces and needs fewer tokens than WordPiece.")
    print("  - 'zebra' contains a letter ('z') never seen in training. WordPiece replaces the WHOLE word with")
    print("    [UNK]; BPE marks only the unknown letter and keeps 'e b r a'. (Byte-level BPE, like GPT-2 in")
    print("    Part 1, starts from all 256 bytes, so it never needs [UNK] at all.)")


def tokens_and_cost():
    print("=" * 100)
    print("WHY IT MATTERS FOR DAY 5: tokens are what you pay for and what fills the context window")
    print("=" * 100)
    bpe = Tokenizer.from_pretrained("gpt2")
    text = "Hi, a customer says we charged them twice for order 5521. What should I do?"
    print(f"'{text}'")
    print(f"  {len(text.split())} words -> {len(bpe.encode(text).tokens)} GPT-2 tokens")
    print("  Each model family has its own tokenizer, so the same text costs a different number of tokens")
    print("  on Claude, GPT or Llama. The Day 5 service estimates locally with JTokkit (an OpenAI tokenizer)")
    print("  and reports the exact count the provider bills (usage.inputTokens) next to it.")


if __name__ == "__main__":
    part1_pretrained()
    part2_trained()
    tokens_and_cost()
