import tempfile
import unittest
from pathlib import Path

import numpy as np

from tools.offline_search.build_web_index import DIMENSIONS, quantize, write_index


class BuildWebIndexTest(unittest.TestCase):
    def test_quantization_preserves_fixture_nearest_neighbors(self):
        vectors = np.zeros((4, DIMENSIONS), dtype=np.float32)
        vectors[0, :2] = [1.0, 0.0]
        vectors[1, :2] = [0.98, 0.02]
        vectors[2, :2] = [0.5, 0.5]
        vectors[3, :2] = [-1.0, 0.0]
        vectors /= np.linalg.norm(vectors, axis=1, keepdims=True)
        exact = np.argsort(-(vectors @ vectors[0]))[:3].tolist()
        decoded = np.stack([quantize(row)[1].astype(np.float32) * quantize(row)[0] for row in vectors])
        approximate = np.argsort(-(decoded @ vectors[0]))[:3].tolist()
        self.assertEqual(exact, approximate)

    def test_writer_is_byte_deterministic(self):
        rows = [(0x000101, np.arange(DIMENSIONS, dtype=np.float32) / DIMENSIONS)]
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "a.int8"
            second = Path(directory) / "b.int8"
            write_index(first, rows)
            write_index(second, rows)
            self.assertEqual(first.read_bytes(), second.read_bytes())


if __name__ == "__main__":
    unittest.main()
