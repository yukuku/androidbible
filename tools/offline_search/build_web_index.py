#!/usr/bin/env python3
"""Build the deterministic int8 WEB verse index consumed by Android."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import zipfile
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

MAGIC = b"YUKUSEM1"
FORMAT_VERSION = 1
DIMENSIONS = 384
BOOK_CODES = (
    "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB "
    "PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG "
    "ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT "
    "PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV"
).split()
BOOK_ID = {code: index for index, code in enumerate(BOOK_CODES)}


def ari(book_id: int, chapter: int, verse: int) -> int:
    return ((book_id & 0xFF) << 16) | ((chapter & 0xFF) << 8) | (verse & 0xFF)


def clean_usfm(text: str) -> str:
    text = re.sub(r"\\(?:f|x)\s.*?\\(?:f|x)\*", " ", text, flags=re.DOTALL)
    text = re.sub(r"\\(?:fig|cat)\s.*?\\(?:fig|cat)\*", " ", text, flags=re.DOTALL)
    text = re.sub(r"\|[^\\\n]*", " ", text)
    text = re.sub(r"\\[A-Za-z0-9+_-]+\*?\s*", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def read_web_verses(usfm_zip: Path) -> list[tuple[int, str]]:
    rows: list[tuple[int, str]] = []
    with zipfile.ZipFile(usfm_zip) as archive:
        for name in archive.namelist():
            raw = archive.read(name).decode("utf-8-sig", errors="strict")
            id_match = re.search(r"(?m)^\\id\s+([A-Z0-9]{3})", raw)
            if not id_match or id_match.group(1) not in BOOK_ID:
                continue
            book_id = BOOK_ID[id_match.group(1)]
            chapter = 0
            marker = re.compile(r"(?m)^\\(c|v)\s+([^\s]+)(?:\s+)?")
            matches = list(marker.finditer(raw))
            for index, match in enumerate(matches):
                if match.group(1) == "c":
                    chapter = int(match.group(2))
                    continue
                verse = int(match.group(2))
                end = matches[index + 1].start() if index + 1 < len(matches) else len(raw)
                text = clean_usfm(raw[match.end():end])
                if text:
                    rows.append((ari(book_id, chapter, verse), text))
    rows.sort(key=lambda row: row[0])
    if len({value for value, _ in rows}) != len(rows):
        raise ValueError("Duplicate ARI in WEB source")
    if len({value >> 16 for value, _ in rows}) != 66:
        raise ValueError("WEB source must contain 66 canonical books")
    if len(rows) < 31_000:
        raise ValueError(f"WEB source is incomplete: {len(rows)} verses")
    return rows


def quantize(vector: np.ndarray) -> tuple[float, np.ndarray]:
    vector = np.asarray(vector, dtype=np.float32)
    maximum = float(np.max(np.abs(vector)))
    scale = maximum / 127.0 if maximum else 1.0
    encoded = np.clip(np.rint(vector / scale), -127, 127).astype(np.int8)
    return scale, encoded


def write_index(path: Path, rows: list[tuple[int, np.ndarray]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".part")
    with temporary.open("wb") as output:
        output.write(struct.pack("<8sIII", MAGIC, FORMAT_VERSION, DIMENSIONS, len(rows)))
        for verse_ari, vector in rows:
            if vector.shape != (DIMENSIONS,):
                raise ValueError(f"Unexpected vector shape {vector.shape}")
            scale, encoded = quantize(vector)
            output.write(struct.pack("<if", verse_ari, scale))
            output.write(encoded.tobytes(order="C"))
    temporary.replace(path)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build(args: argparse.Namespace) -> None:
    verses = read_web_verses(args.usfm)
    tokenizer = Tokenizer.from_file(str(args.tokenizer))
    tokenizer.enable_truncation(max_length=args.max_length)
    tokenizer.enable_padding(pad_id=179935, pad_token="<|endoftext|>")
    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])
    indexed: list[tuple[int, np.ndarray]] = []
    for start in range(0, len(verses), args.batch_size):
        batch = verses[start:start + args.batch_size]
        encoded = tokenizer.encode_batch([text for _, text in batch])
        ids = np.asarray([item.ids for item in encoded], dtype=np.int64)
        mask = np.asarray([item.attention_mask for item in encoded], dtype=np.int64)
        hidden = session.run(None, {"input_ids": ids, "attention_mask": mask})[0]
        vectors = hidden[:, 0, :].astype(np.float32)
        norms = np.linalg.norm(vectors, axis=1, keepdims=True)
        vectors = vectors / np.maximum(norms, np.finfo(np.float32).eps)
        if len(batch) != len(vectors):
            raise ValueError("Inference output batch size mismatch")
        indexed.extend((row[0], vector) for row, vector in zip(batch, vectors))
        print(f"Indexed {min(start + len(batch), len(verses))}/{len(verses)}", flush=True)
    write_index(args.output, indexed)
    metadata = {
        "schema": 1,
        "format": "YUKUSEM1",
        "dimensions": DIMENSIONS,
        "count": len(indexed),
        "source": "World English Bible (public domain)",
        "sourceSha256": sha256(args.usfm),
        "modelRevision": "835ad14087e140460703cf0fae09f97d469d65c2",
        "modelSha256": sha256(args.model),
        "tokenizerSha256": sha256(args.tokenizer),
        "indexSha256": sha256(args.output),
    }
    args.output.with_suffix(".metadata.json").write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--usfm", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--batch-size", type=int, default=96)
    parser.add_argument("--max-length", type=int, default=128)
    return parser.parse_args()


if __name__ == "__main__":
    build(parse_args())
