# NEURAL_NETWORK

Custom, fully trainable neural stack — no ML libraries, no pretrained
weights, ~330k parameters at default settings (maxVocab 2048, dim 64,
2 transformer blocks, 4 heads).

## Layout

```
token ids (+BOS/EOS, number buckets, <num:*>, identifiers)
  -> token Embedding [maxVocab x dim] + learned positional Embedding [maxSeq x dim]
  -> N x TransformerBlock (pre-norm MHA + residual + pre-norm FFN-GELU + residual)
  -> LayerNorm -> mean pool -> sentence vector
  -> Dense(dim -> maxVocab) next-token logits
```

- `DenseLayer` — exact forward/backprop, gradient accumulation, Adam/SGD.
- `EmbeddingLayer` — sparse row updates; rows past the active vocabulary stay
  allocated so **vocabulary growth never changes the layout** (checkpoints stay valid).
- `MultiHeadAttention` — scaled dot-product, per-head softmax, full backprop
  through scores/values/projections (projection caches replayed per row for exactness).
- `TransformerBlock` — pre-norm attention + FFN with residuals; per-row norm
  replay keeps shared-layer gradients exact.
- `LayerNorm` — gain/bias with exact backward.
- `Activation` — ReLU, tanh, sigmoid, GELU (tanh approx), linear.
- `LossFunctions` — softmax cross-entropy, MSE, cosine-alignment.
- `Optimizer` — Adam/AdamW with bias correction, global-norm clipping,
  decoupled weight decay; `LearningRateSchedule` (constant, warmup+cosine).

## Training signals

1. **Next-token prediction** over player exchanges + registry-derived semantic
   sentences (`TrainingPipeline.learnExchange/learnFact`), trained in small
   off-thread batches on the training pool.
2. **Sentence alignment** (`trainAlign`): pulls encodings toward confirmed
   intent prototypes — this is how paraphrase understanding improves.

## Inference

`InferenceEngine` runs recognition under a compute budget (default 1500 ms);
on timeout the deterministic response fallback answers instead of hanging.

## Persistence

`ModelCheckpoint` writes `jarvis/models/<uuid>/model-latest.bin`
(versioned magic + layout check + optimizer step + loss + vocab hash),
keeping `model-previous.bin` for one-step rollback
(`/jarvis test` can't roll back; rollback is via `PersistenceManager.rollbackModel`,
exposed to ops tooling). Layout mismatches never corrupt data: the old
checkpoint is kept and training continues from fresh weights.

## Honest note

Randomly initialized embeddings do not "understand" language on day one.
Semantic competence comes from the hybrid scorer (neural cosine blended with
a learned synonym/keyword graph) plus per-player training. See LIMITATIONS.md.
