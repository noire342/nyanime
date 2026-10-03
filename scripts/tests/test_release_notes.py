import unittest
from unittest.mock import patch

from scripts.release_notes import previous_tag, release_notes


class ReleaseNotesTest(unittest.TestCase):
    def test_numeric_notes_never_expose_a_revision_prefix(self):
        notes = release_notes("## Current\n\n- New.\n", None, "v0.19.0.0")
        self.assertIn("Nyanime 0.19.0.0", notes)
        self.assertNotIn("Nyanime v0", notes)

    def test_previous_release_ignores_current_aliases_and_unrelated_platforms(self):
        def fake_git(*args):
            if args[0] == "tag":
                return "r42\nv0.19.0.0\nr99\nv0.19.0.1\ntv-r100"
            return {"r42..HEAD": "3", "v0.19.0.0..HEAD": "3", "r99..HEAD": "0"}[args[-1]]
        with patch("scripts.release_notes.git", side_effect=fake_git):
            self.assertEqual(previous_tag("v0.19.0.1"), "v0.19.0.0")

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


if __name__ == "__main__":
    unittest.main()
