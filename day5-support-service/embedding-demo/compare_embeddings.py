"""
Day 5: fixed (static) vs contextual embeddings, with one Hugging Face model.

sentence-transformers/all-MiniLM-L6-v2 is a small BERT-style model (90 MB, 384 dimensions):
  - FIXED: its input embedding table. One vector per token, looked up the same way in every sentence,
    like word2vec or GloVe. A sentence vector is just the average of its token vectors.
  - CONTEXTUAL: the output of its transformer layers. Every token's vector depends on the words around
    it, and the sentence vector (mean pooling) is what Day 5's /similarity and Day 6's RAG rely on.
Same model, same tokens: the only difference is context.

Run:
  python3 -m venv .venv && source .venv/bin/activate
  pip install torch --index-url https://download.pytorch.org/whl/cpu
  pip install -r requirements.txt
  python compare_embeddings.py
"""

import torch
from transformers import AutoModel, AutoTokenizer

MODEL = "sentence-transformers/all-MiniLM-L6-v2"

tokenizer = AutoTokenizer.from_pretrained(MODEL)
model = AutoModel.from_pretrained(MODEL).eval()
table = model.get_input_embeddings().weight          # [vocabulary, 384]: the fixed embeddings


def cosine(a, b):
    return torch.nn.functional.cosine_similarity(a, b, dim=0).item()

'''The encode() function takes a sentence as input 
and converts it into contextual token embeddings using a Transformer model. 
First, the tokenizer converts the sentence into tokens and their corresponding
numerical token IDs, with return_tensors="pt" specifying that the output should 
be in PyTorch tensor format. The torch.no_grad() block disables gradient calculation
because the model is being used only for generating embeddings and not for training. 
The tokenized input is then passed through the Transformer model, and last_hidden_state 
is extracted to obtain a contextual vector for each token. 
The [0] removes the batch dimension since only one sentence is processed. 
Finally, the function returns both the token IDs and their corresponding 
contextual embedding vectors, where each token's vector represents its meaning based 
on the context in which it appears.'''
def encode(sentence):
    batch = tokenizer(sentence, return_tensors="pt")
    with torch.no_grad():
        hidden = model(**batch).last_hidden_state[0]   # one contextual vector per token
    return batch["input_ids"][0], hidden

'''The word_vectors() function retrieves two types of vector 
representations for a given word in a sentence: a fixed (static) 
vector and a contextual vector. First, the function calls 
encode(sentence) to obtain the token IDs and contextual embeddings 
for all tokens in the sentence. It then converts the given word 
into its corresponding token ID using tokenizer.convert_tokens_to_ids(word). 
The function searches for the position of this word in the token ID sequence 
and identifies its location in the sentence. Finally, it returns table[word_id], 
which is the fixed/static embedding of the word that remains the same regardless 
of the sentence, and hidden[position], which is the contextual embedding generated 
by the Transformer model and therefore depends on the surrounding words and context.'''
def word_vectors(sentence, word):
    """The fixed and the contextual vector of `word` inside `sentence`."""
    ids, hidden = encode(sentence)
    word_id = tokenizer.convert_tokens_to_ids(word)
    position = (ids == word_id).nonzero()[0].item()
    return table[word_id].detach(), hidden[position]

'''The sentence_vectors() function generates two vector 
representations for the entire sentence: a fixed (static) 
sentence vector and a contextual sentence vector. 
First, it calls encode(sentence) to obtain the 
token IDs and their contextual embeddings. 
The inner = slice(1, len(ids) - 1) statement 
excludes the special [CLS] and [SEP] tokens, 
so only the actual sentence tokens are considered. 
For the fixed representation, table[ids[inner]] 
retrieves the pre-existing static embedding of 
each token, and mean(dim=0) averages 
these token vectors to produce a single 
fixed vector for the sentence. 
For the contextual representation, hidden[inner] 
contains the Transformer-generated contextual vector 
for each token, and these vectors are also averaged 
to obtain one contextual vector representing the 
entire sentence. Thus, the function compares 
a sentence representation obtained by averaging 
fixed word embeddings with one obtained by averaging 
context-dependent Transformer embeddings.'''
def sentence_vectors(sentence):
    """Fixed: average of the looked-up token vectors. Contextual: average of the transformer outputs."""
    ids, hidden = encode(sentence)
    inner = slice(1, len(ids) - 1)                     # leave out [CLS] and [SEP]
    return table[ids[inner]].detach().mean(dim=0), hidden[inner].mean(dim=0)
'''The part1_same_word_different_meaning() function demonstrates the difference 
between fixed word embeddings and contextual embeddings by examining the word “charge” 
in four different sentences. It first defines four sentences in which “charge” 
has different meanings: a financial amount, an additional fee, the action of 
supplying power to a battery, and responsibility or authority. 
For each sentence, the word_vectors() function obtains 
both the fixed and contextual vector of “charge”. 
The function then calculates the cosine similarity between the 
vectors of “charge” across every pair of sentences. For the 
fixed representation, the vector of “charge” is always the 
same regardless of context, so its cosine similarity 
with itself across all sentences is 1.000. 
In contrast, the contextual representation changes according 
to the surrounding words, so the two financial meanings in sentences 
1 and 2 are expected to have higher similarity, while the battery-related 
meaning and the responsibility-related meaning are more different. 
Thus, the experiment demonstrates that static embeddings represent 
a word with one fixed vector, whereas contextual embeddings can represent 
different meanings of the same word based on its context.
'''

def part1_same_word_different_meaning():
    print("=" * 96)
    print("PART 1: the same word in different contexts")
    print("=" * 96)
    word = "charge"
    sentences = [
        "The card charge appeared twice on my statement.",
        "There was an extra charge on my invoice.",
        "My phone will not charge since the update.",
        "Who is in charge of my refund?",
    ]
    vectors = [word_vectors(s, word) for s in sentences]
    for i, s in enumerate(sentences, 1):
        print(f"  {i}. {s}")
    print(f"\n  cosine similarity of '{word}' between sentences     fixed   contextual")
    for i in range(len(sentences)):
        for j in range(i + 1, len(sentences)):
            fixed = cosine(vectors[i][0], vectors[j][0])
            contextual = cosine(vectors[i][1], vectors[j][1])
            print(f"    {i + 1} vs {j + 1}{'':42}{fixed:6.3f}   {contextual:6.3f}")
    print("\n  Fixed: always 1.000. One vector for 'charge', whether it is money, a battery or responsibility.")
    print("  Contextual: the two money senses (1, 2) stay closest; battery and 'in charge' drift away.\n")

'''
The part2_sentences() function demonstrates how fixed and contextual embeddings 
represent the similarity between complete sentences. It defines four sentence pairs 
covering different cases: paraphrases with different words, sentences containing the 
same words in different orders, sentences that differ through negation, and 
completely unrelated sentences. For each pair, the sentence_vectors() 
function generates both a fixed sentence vector and a contextual sentence vector 
by averaging the corresponding token vectors. The cosine() function is then 
used to measure the similarity between the two sentence vectors. 
The results demonstrate that fixed embeddings may fail to capture 
the actual meaning of a sentence because they mainly depend on the 
individual words and ignore word order and context. For example, 
the fixed representation may incorrectly consider unrelated sentences 
more similar than paraphrases if they contain overlapping or similar words. 
Contextual embeddings generally provide a better distinction between paraphrases 
and unrelated sentences because they consider the context of each word. 
However, the example also shows that embeddings are not perfect at understanding 
negation or logical meaning: sentences such as “The refund was approved” 
and “The refund was not approved” may still receive a high similarity score 
because they discuss the same topic. Therefore, embeddings are useful for semantic retrieval 
and similarity, while an LLM is still needed to carefully interpret the retrieved text and 
determine the exact meaning.
'''
def part2_sentences():
    print("=" * 96)
    print("PART 2: sentence similarity")
    print("=" * 96)
    pairs = [
        ("paraphrase, different words", "I want my money back.", "Please refund my order."),
        ("same words, different order", "The customer charged the card.", "The card charged the customer."),
        ("negation", "The refund was approved.", "The refund was not approved."),
        ("unrelated", "Where is my parcel?", "How do I reset my password?"),
    ]
    print(f"  {'':30}{'fixed':>8} {'contextual':>11}")
    for label, a, b in pairs:
        fa, ca = sentence_vectors(a)
        fb, cb = sentence_vectors(b)
        print(f"  {label:30}{cosine(fa, fb):8.3f} {cosine(ca, cb):11.3f}    '{a}' / '{b}'")
    print("""
  What to notice:
  - Compare the paraphrase with the unrelated pair. Fixed vectors rate the UNRELATED pair as more similar
    than the paraphrase: they only see shared and look-alike words. Contextual vectors put the paraphrase
    far above the unrelated pair. That gap is what semantic search needs, and why Day 6 embeds with a
    contextual model (and why sentence-embedding models are trained for exactly this).
  - Word order: averaging fixed vectors ignores order completely (1.000); context notices, but only a little.
  - Negation: both still score very high. Embeddings capture topic far better than logic, which is why RAG
    retrieves with embeddings but lets the LLM read the passage before answering.""")


if __name__ == "__main__":
    part1_same_word_different_meaning()
    part2_sentences()
