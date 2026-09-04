#!/usr/bin/env python3
"""Evaluate the checked-in semantic index against reviewed Indonesian themes."""

from __future__ import annotations

import argparse
import json
import re
import struct
import unicodedata
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

from build_web_index import DIMENSIONS, read_web_verses


def read_index(path: Path) -> tuple[np.ndarray, np.ndarray]:
    with path.open("rb") as stream:
        magic, version, dimensions, count = struct.unpack("<8sIII", stream.read(20))
        if magic != b"YUKUSEM1" or version != 1 or dimensions != DIMENSIONS:
            raise ValueError("Incompatible semantic index")
        aris = np.empty(count, dtype=np.int32)
        vectors = np.empty((count, DIMENSIONS), dtype=np.float32)
        for row in range(count):
            aris[row], scale = struct.unpack("<if", stream.read(8))
            vectors[row] = np.frombuffer(stream.read(DIMENSIONS), dtype=np.int8) * scale
    return aris, vectors


def embed(session: ort.InferenceSession, tokenizer: Tokenizer, query: str) -> np.ndarray:
    encoded = tokenizer.encode(query)
    ids = np.asarray([encoded.ids], dtype=np.int64)
    mask = np.asarray([encoded.attention_mask], dtype=np.int64)
    vector = session.run(None, {"input_ids": ids, "attention_mask": mask})[0][0, 0].astype(np.float32)
    return vector / np.linalg.norm(vector)


def semantic_query(query: str, synonyms: dict[str, list[str]]) -> str:
    groups: dict[str, list[str]] = {}
    for root, words in synonyms.items():
        group = sorted(set([root, *words]))
        groups.update({word: group for word in group})
    tokens = re.findall(r"[^\W_]+", unicodedata.normalize("NFKC", query).lower())
    expanded = dict.fromkeys(word for token in tokens for word in groups.get(token, [token]))
    stop_words = {"dan", "yang", "saat", "untuk", "dalam", "and", "the", "when", "for", "in"}
    return " ".join(word for word in expanded if word not in stop_words)


def main(args: argparse.Namespace) -> None:
    benchmark = json.loads(args.benchmark.read_text(encoding="utf-8"))
    synonyms = json.loads(args.synonyms.read_text(encoding="utf-8"))
    aris, vectors = read_index(args.index)
    verse_text = dict(read_web_verses(args.usfm))
    tokenizer = Tokenizer.from_file(str(args.tokenizer))
    tokenizer.enable_truncation(max_length=128)
    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
    misses = []
    for case in benchmark:
        retrieval_query = semantic_query(case["query"], synonyms)
        scores = vectors @ embed(session, tokenizer, retrieval_query)
        order = np.argsort(scores)[::-1][: args.top_k]
        ranked = [int(aris[index]) for index in order]
        hit_rank = next((rank + 1 for rank, value in enumerate(ranked) if value in case["relevantAris"]), None)
        print(f"{case['query']}: reviewed-hit-rank={hit_rank}")
        for index in order[:5]:
            value = int(aris[index])
            print(f"  {value} {float(scores[index]):.4f} {verse_text[value]}")
        if hit_rank is None:
            misses.append(case["query"])
    if misses:
        raise SystemExit(f"No reviewed hit in top {args.top_k}: {', '.join(misses)}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--index", type=Path, required=True)
    parser.add_argument("--usfm", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--synonyms", type=Path, required=True)
    parser.add_argument("--top-k", type=int, default=50)
    return parser.parse_args()


if __name__ == "__main__":
    main(parse_args())
