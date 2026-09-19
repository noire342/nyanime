"""Run the production outbox migration and delivery query against SQLite, without an emulator."""
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / 'app/src/main/java/eu/kanade/tachiyomi/data/community'
STORE = (PACKAGE / 'CommunityStore.kt').read_text(encoding='utf-8')
POLICY = (PACKAGE / 'RelayDeliveryPolicy.kt').read_text(encoding='utf-8')
UPGRADE = re.findall(r'"([^"\n]+)"', POLICY.split('val upgrade = listOf(', 1)[1].split('\n    )', 1)[0])
DUE = re.search(r'const val DUE = """(.*?)"""', POLICY, re.S)[1]


class OutboxTest(unittest.TestCase):
    def connect(self, path=':memory:'):
        db = sqlite3.connect(path)
        db.execute('PRAGMA foreign_keys=ON')
        return db

    def seed(self, db):
        for sql in re.findall(r'"(CREATE TABLE [^"\n]+)"', STORE):
            db.execute(sql)
        db.execute("INSERT INTO outbox VALUES('old',X'0011FF','nyanime.sync.v1:private',1,0,22)")
        db.execute("INSERT INTO receipts VALUES('old','relay-a')")
        for sql in UPGRADE:
            db.execute(sql)

    def due(self, db, relay='relay-a', now=100, sync=True):
        return [row[0] for row in db.execute(DUE, (relay, now, int(sync), now, relay))]

    def test_upgrade_preserves_ciphertext_and_existing_receipts(self):
        with self.connect() as db:
            self.seed(db)
            self.assertEqual((b'\x00\x11\xff', 0), db.execute('SELECT event,priority FROM outbox').fetchone())
            self.assertEqual([], self.due(db))
            self.assertEqual([b'\x00\x11\xff'], self.due(db, 'relay-b'))

    def test_live_progress_precedes_large_initial_library(self):
        with self.connect() as db:
            self.seed(db)
            db.executemany('INSERT INTO outbox(id,event,address,created,priority) VALUES(?,?,?,?,0)',
                           [(str(i), bytes(str(i), 'ascii'), 'nyanime.sync.v1:' + str(i), i) for i in range(1441)])
            db.execute("INSERT INTO outbox(id,event,address,created,priority) VALUES('live',X'99','nyanime.sync.live',9999,2)")
            self.assertEqual(b'\x99', self.due(db)[0])
            self.assertEqual([], self.due(db, sync=False))

    def test_backoff_and_acknowledgements_survive_reopen(self):
        with tempfile.TemporaryDirectory() as folder:
            path = str(Path(folder) / 'queue.db')
            with self.connect(path) as db:
                self.seed(db)
                db.execute("INSERT INTO delivery_attempts(event,relay,retry_at,attempts,reason) VALUES('old','relay-b',30000,1,'rate-limited')")
            db.close()
            with self.connect(path) as db:
                self.assertEqual([], self.due(db, 'relay-b'))
                self.assertEqual([], self.due(db, 'relay-a', now=30001))
                self.assertEqual([b'\x00\x11\xff'], self.due(db, 'relay-b', now=30001))
                db.execute("DELETE FROM outbox WHERE id='old'")
                self.assertEqual(0, db.execute('SELECT count(*) FROM delivery_attempts').fetchone()[0])
            db.close()

    def test_expired_events_are_not_replayed(self):
        with self.connect() as db:
            self.seed(db)
            db.execute("UPDATE outbox SET expires=99 WHERE id='old'")
            self.assertEqual([], self.due(db, 'relay-b'))


if __name__ == '__main__':
    unittest.main()
