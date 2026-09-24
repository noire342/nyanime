import unittest

from scripts.release_notes import release_notes


class ReleaseNotesTest(unittest.TestCase):
    def test_only_new_entries_are_published(self):
        old = "## Older section\n\n- Previously released change.\n"
        current = """## Current section

- New change spanning
  two lines.

## Older section

- Previously released change.
"""
        notes = release_notes(current, old, "r42")

        self.assertIn("New change spanning two lines.", notes)
        self.assertNotIn("Previously released change.", notes)
        self.assertNotIn("Older section", notes)

    def test_tv_release_uses_its_own_name(self):
        notes = release_notes("## TV\n\n- Remote UI.\n", None, "tv-r42", "Nyanime TV")
        self.assertIn("Novità di Nyanime TV tv-r42", notes)
        self.assertIn("Remote UI.", notes)


if __name__ == "__main__":
    unittest.main()
