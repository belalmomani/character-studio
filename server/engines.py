"""Conversion engines. An engine turns one JPEG file into one GLB file."""
import json
import struct


class EngineError(Exception):
    pass


class Engine:
    name = "base"

    def generate(self, image_path: str, out_path: str) -> None:
        raise NotImplementedError


class StubEngine(Engine):
    """TEST ONLY. Writes a tiny valid GLB (empty scene). It does NOT convert the photo.
    Enabled only with HCS_ENGINE=stub so the contract can be tested without a GPU."""
    name = "stub"

    def generate(self, image_path, out_path):
        doc = json.dumps({"asset": {"version": "2.0"}, "scenes": [{}], "scene": 0}).encode()
        doc += b" " * (-len(doc) % 4)
        total = 12 + 8 + len(doc)
        with open(out_path, "wb") as f:
            f.write(struct.pack("<III", 0x46546C67, 2, total))
            f.write(struct.pack("<II", len(doc), 0x4E4F534A))
            f.write(doc)


class Trellis2Engine(Engine):
    """NOT IMPLEMENTED END TO END. Verified from the official source (2026-10-21):
      pipeline = Trellis2ImageTo3DPipeline.from_pretrained(<model>)   # loads rembg + DINOv3 eagerly
      pipeline.run(image, seed=42, preprocess_image=True, pipeline_type=None) -> List[MeshWithVoxel]
    NOT verified: how a MeshWithVoxel is exported to GLB. Until that is read from the
    official README/examples and run on a 24GB+ NVIDIA GPU, this engine refuses to run
    rather than pretend."""
    name = "trellis2"

    def generate(self, image_path, out_path):
        raise EngineError("trellis2 engine is not implemented: GLB export step unverified")


def get_engine(name: str) -> Engine:
    engines = {"stub": StubEngine, "trellis2": Trellis2Engine}
    if name not in engines:
        raise ValueError("unknown engine: %s (choose %s)" % (name, ", ".join(engines)))
    return engines[name]()
