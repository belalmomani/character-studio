"""Reference server for docs/SERVER_CONTRACT.md. Standard library only.
Run behind a TLS reverse proxy; the app only accepts https://."""
import hmac
import json
import os
import queue
import re
import shutil
import tempfile
import threading
import time
import secrets
from datetime import date
from email.parser import BytesParser
from email.policy import HTTP
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from engines import Engine, EngineError, get_engine

ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


class Config:
    def __init__(self, engine: Engine, token: str = "", max_image_bytes=10 * 1024 * 1024,
                 daily_limit=50, ttl_seconds=900, work_dir=None):
        self.engine = engine
        self.token = token
        self.max_image_bytes = max_image_bytes
        self.daily_limit = daily_limit
        self.ttl = ttl_seconds
        self.work_dir = work_dir or tempfile.mkdtemp(prefix="hcs-")


class Job:
    def __init__(self, job_id, directory):
        self.id = job_id
        self.dir = directory
        self.status = "queued"
        self.error = None
        self.created = time.time()
        self.result = os.path.join(directory, "result.glb")
        self.image = os.path.join(directory, "image.jpg")


class State:
    def __init__(self, cfg: Config):
        self.cfg = cfg
        self.jobs = {}
        self.lock = threading.Lock()
        self.queue = queue.Queue()
        self.day = date.today()
        self.count = 0
        self.stop = threading.Event()

    def take_quota(self) -> bool:
        with self.lock:
            if date.today() != self.day:
                self.day, self.count = date.today(), 0
            if self.count >= self.cfg.daily_limit:
                return False
            self.count += 1
            return True

    def remove(self, job_id):
        with self.lock:
            job = self.jobs.pop(job_id, None)
        if job:
            shutil.rmtree(job.dir, ignore_errors=True)

    def worker(self):
        while not self.stop.is_set():
            try:
                job_id = self.queue.get(timeout=0.2)
            except queue.Empty:
                continue
            with self.lock:
                job = self.jobs.get(job_id)
                if job is None:
                    continue
                job.status = "running"
            try:
                self.cfg.engine.generate(job.image, job.result)
                ok = os.path.isfile(job.result)
                with self.lock:
                    job.status, job.error = ("done", None) if ok else ("failed", "no output")
            except EngineError:
                with self.lock:
                    job.status, job.error = "failed", "engine"
            except Exception:
                with self.lock:
                    job.status, job.error = "failed", "engine"
            finally:
                # source photo is not needed once conversion ended
                try:
                    os.remove(job.image)
                except OSError:
                    pass

    def janitor(self):
        while not self.stop.wait(1.0):
            now = time.time()
            with self.lock:
                old = [j.id for j in self.jobs.values()
                       if now - j.created > self.cfg.ttl and j.status != "running"]
            for job_id in old:
                self.remove(job_id)


def make_handler(state: State):
    cfg = state.cfg

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"
        server_version = "hcs"

        def log_message(self, *a):  # never log paths, headers or tokens
            pass

        def _json(self, code, obj):
            body = json.dumps(obj).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _authed(self):
            if not cfg.token:
                return True
            given = self.headers.get("Authorization", "")
            if not given.startswith("Bearer "):
                self._json(401, {"error": "auth"})
                return False
            if not hmac.compare_digest(given[7:].encode(), cfg.token.encode()):
                self._json(403, {"error": "auth"})
                return False
            return True

        def _job(self, job_id):
            if not ID_RE.match(job_id):
                self._json(404, {"error": "not_found"})
                return None
            with state.lock:
                job = state.jobs.get(job_id)
            if job is None:
                self._json(404, {"error": "not_found"})
            return job

        def do_GET(self):
            if not self._authed():
                return
            parts = self.path.split("?")[0].strip("/").split("/")
            if parts == ["v1", "health"]:
                return self._json(200, {"ok": True})
            if len(parts) == 3 and parts[:2] == ["v1", "jobs"]:
                job = self._job(parts[2])
                if job:
                    with state.lock:
                        out = {"status": job.status}
                        if job.error == "quota":
                            out["error"] = "quota"
                    self._json(200, out)
                return
            if len(parts) == 4 and parts[:2] == ["v1", "jobs"] and parts[3] == "result":
                job = self._job(parts[2])
                if not job:
                    return
                with state.lock:
                    done = job.status == "done"
                if not done:
                    return self._json(409, {"error": "not_ready"})
                try:
                    with open(job.result, "rb") as f:
                        data = f.read()
                except OSError:
                    return self._json(404, {"error": "not_found"})
                state.remove(job.id)  # delete before sending: data is already in memory
                self.send_response(200)
                self.send_header("Content-Type", "model/gltf-binary")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                return
            self._json(404, {"error": "not_found"})

        def do_DELETE(self):
            if not self._authed():
                return
            parts = self.path.split("?")[0].strip("/").split("/")
            if len(parts) == 3 and parts[:2] == ["v1", "jobs"] and ID_RE.match(parts[2]):
                state.remove(parts[2])
                return self._json(200, {"ok": True})
            self._json(404, {"error": "not_found"})

        def do_POST(self):
            if self.path.split("?")[0].rstrip("/") != "/v1/jobs":
                return self._json(404, {"error": "not_found"})
            if not self._authed():
                return
            try:
                length = int(self.headers.get("Content-Length", ""))
            except ValueError:
                return self._json(411, {"error": "length_required"})
            if length > cfg.max_image_bytes + 64 * 1024:
                self.close_connection = True
                return self._json(413, {"error": "too_large"})
            raw = self.rfile.read(length)
            ctype = self.headers.get("Content-Type", "")
            if not ctype.startswith("multipart/form-data"):
                return self._json(400, {"error": "multipart_required"})
            msg = BytesParser(policy=HTTP).parsebytes(
                b"Content-Type: " + ctype.encode() + b"\r\n\r\n" + raw)
            image = None
            for part in msg.iter_parts() if msg.is_multipart() else []:
                if part.get_param("name", header="content-disposition") == "image":
                    image = part.get_payload(decode=True)
            if not image:
                return self._json(400, {"error": "image_missing"})
            if len(image) > cfg.max_image_bytes:
                return self._json(413, {"error": "too_large"})
            if not image.startswith(b"\xff\xd8\xff"):
                return self._json(415, {"error": "jpeg_required"})
            if not state.take_quota():
                return self._json(429, {"error": "quota"})
            job_id = secrets.token_urlsafe(16)
            directory = tempfile.mkdtemp(prefix="job-", dir=cfg.work_dir)
            job = Job(job_id, directory)
            with open(job.image, "wb") as f:
                f.write(image)
            with state.lock:
                state.jobs[job_id] = job
            state.queue.put(job_id)  # single worker = one concurrent job
            self._json(200, {"id": job_id})

    return Handler


def start(cfg: Config, host="127.0.0.1", port=8080):
    state = State(cfg)
    httpd = ThreadingHTTPServer((host, port), make_handler(state))
    threading.Thread(target=state.worker, daemon=True).start()
    threading.Thread(target=state.janitor, daemon=True).start()
    return httpd, state


def main():
    token = os.environ.get("HCS_TOKEN", "")
    if not token and os.environ.get("HCS_ALLOW_NO_TOKEN") != "1":
        raise SystemExit("Refusing to start without HCS_TOKEN (set HCS_ALLOW_NO_TOKEN=1 to override, not for public hosts)")
    cfg = Config(
        get_engine(os.environ.get("HCS_ENGINE", "trellis2")), token,
        int(os.environ.get("HCS_MAX_IMAGE_MB", "10")) * 1024 * 1024,
        int(os.environ.get("HCS_DAILY_LIMIT", "50")),
        int(os.environ.get("HCS_TTL_SECONDS", "900")),
    )
    httpd, state = start(cfg, os.environ.get("HCS_HOST", "127.0.0.1"),
                         int(os.environ.get("HCS_PORT", "8080")))
    try:
        httpd.serve_forever()
    finally:
        state.stop.set()
        shutil.rmtree(cfg.work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
