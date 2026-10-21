import http.client, json, os, sys, time, unittest, uuid
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import hcs_server
from engines import StubEngine, Engine, EngineError

JPEG = b"\xff\xd8\xff\xe0" + b"0" * 100
TOKEN = "secret-token-123"


class Failing(Engine):
    def generate(self, i, o):
        raise EngineError("x")


def multipart(data, field="image"):
    b = uuid.uuid4().hex
    body = (("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"a.jpg\"\r\n"
             "Content-Type: image/jpeg\r\n\r\n" % (b, field)).encode() + data +
            ("\r\n--%s--\r\n" % b).encode())
    return body, "multipart/form-data; boundary=" + b


class Base(unittest.TestCase):
    engine = StubEngine
    kw = {}

    def setUp(self):
        self.cfg = hcs_server.Config(self.engine(), TOKEN, **self.kw)
        self.httpd, self.state = hcs_server.start(self.cfg, port=0)
        import threading
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()
        self.port = self.httpd.server_address[1]

    def tearDown(self):
        self.httpd.shutdown(); self.httpd.server_close(); self.state.stop.set()
        import shutil; shutil.rmtree(self.cfg.work_dir, ignore_errors=True)

    def req(self, method, path, body=None, headers=None, auth=True):
        h = dict(headers or {})
        if auth:
            h["Authorization"] = "Bearer " + TOKEN
        c = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        c.request(method, path, body, h)
        r = c.getresponse(); data = r.read(); c.close()
        return r.status, data

    def submit(self, data=JPEG):
        body, ct = multipart(data)
        return self.req("POST", "/v1/jobs", body, {"Content-Type": ct})

    def wait(self, job_id, want):
        for _ in range(100):
            s, d = self.req("GET", "/v1/jobs/" + job_id)
            if json.loads(d)["status"] == want:
                return
            time.sleep(0.05)
        self.fail("status never became " + want)

    def files_left(self):
        return [p for p in os.listdir(self.cfg.work_dir)]


class Contract(Base):
    def test_health_and_auth(self):
        self.assertEqual(self.req("GET", "/v1/health")[0], 200)
        self.assertEqual(self.req("GET", "/v1/health", auth=False)[0], 401)
        s, _ = self.req("GET", "/v1/health", headers={"Authorization": "Bearer wrong"}, auth=False)
        self.assertEqual(s, 403)

    def test_full_flow_and_cleanup(self):
        s, d = self.submit()
        self.assertEqual(s, 200)
        job_id = json.loads(d)["id"]
        self.assertRegex(job_id, r"^[A-Za-z0-9_-]{1,64}$")
        self.wait(job_id, "done")
        s, glb = self.req("GET", "/v1/jobs/%s/result" % job_id)
        self.assertEqual(s, 200)
        self.assertEqual(glb[:4], b"glTF")
        self.assertEqual(int.from_bytes(glb[8:12], "little"), len(glb))
        self.assertEqual(self.files_left(), [])           # deleted after download
        self.assertEqual(self.req("GET", "/v1/jobs/" + job_id)[0], 404)

    def test_delete_removes_files(self):
        job_id = json.loads(self.submit()[1])["id"]
        self.wait(job_id, "done")
        self.req("DELETE", "/v1/jobs/" + job_id)
        self.assertEqual(self.files_left(), [])

    def test_photo_removed_after_conversion(self):
        job_id = json.loads(self.submit()[1])["id"]
        self.wait(job_id, "done")
        time.sleep(0.1)
        d = os.path.join(self.cfg.work_dir, os.listdir(self.cfg.work_dir)[0])
        self.assertEqual(os.listdir(d), ["result.glb"])

    def test_rejects_bad_input(self):
        self.assertEqual(self.submit(b"not a jpeg")[0], 415)
        body, ct = multipart(JPEG, field="other")
        self.assertEqual(self.req("POST", "/v1/jobs", body, {"Content-Type": ct})[0], 400)
        self.assertEqual(self.req("POST", "/v1/jobs", b"x", {"Content-Type": "text/plain"})[0], 400)

    def test_unknown_and_malformed_ids(self):
        self.assertEqual(self.req("GET", "/v1/jobs/nope")[0], 404)
        self.assertEqual(self.req("GET", "/v1/jobs/..%2f..")[0], 404)

    def test_result_not_ready_is_409(self):
        class Slow(Engine):
            def generate(s, i, o): time.sleep(0.5)
        self.state.cfg.engine = Slow()
        job_id = json.loads(self.submit()[1])["id"]
        self.assertEqual(self.req("GET", "/v1/jobs/%s/result" % job_id)[0], 409)


class TooLarge(Base):
    kw = {"max_image_bytes": 1000}

    def test_413(self):
        # body within slack but image over limit -> 413; far over limit -> 413 too
        self.assertEqual(self.submit(JPEG + b"0" * 2000)[0], 413)
        self.assertEqual(self.submit(JPEG + b"0" * 200000)[0], 413)


class Quota(Base):
    kw = {"daily_limit": 1}

    def test_429(self):
        self.assertEqual(self.submit()[0], 200)
        self.assertEqual(self.submit()[0], 429)


class EngineFails(Base):
    engine = Failing

    def test_failed_status(self):
        job_id = json.loads(self.submit()[1])["id"]
        self.wait(job_id, "failed")


class Ttl(Base):
    kw = {"ttl_seconds": 1}

    def test_expiry(self):
        job_id = json.loads(self.submit()[1])["id"]
        self.wait(job_id, "done")
        for _ in range(60):
            if not self.files_left():
                break
            time.sleep(0.1)
        self.assertEqual(self.files_left(), [])


class Startup(unittest.TestCase):
    def test_refuses_without_token(self):
        os.environ.pop("HCS_TOKEN", None); os.environ.pop("HCS_ALLOW_NO_TOKEN", None)
        with self.assertRaises(SystemExit):
            hcs_server.main()

    def test_trellis_refuses_honestly(self):
        from engines import get_engine
        with self.assertRaises(EngineError):
            get_engine("trellis2").generate("a", "b")


if __name__ == "__main__":
    unittest.main()
