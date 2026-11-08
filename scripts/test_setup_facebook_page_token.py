import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("facebook_setup", Path(__file__).with_name("setup-facebook-page-token.py"))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class FacebookSetupTest(unittest.TestCase):
    def test_exchange_gets_requested_page_and_does_not_store_user_token(self):
        with patch.object(setup, "request", side_effect=[{"access_token": "long-user"},
                {"id": "123", "access_token": "page-secret"}]) as request:
            self.assertEqual("page-secret", setup.provision("v26.0", "456", "secret", "user", "123"))
            self.assertEqual("fb_exchange_token", request.call_args_list[0].args[2]["grant_type"])
            self.assertEqual(("v26.0", "123", {"fields": "id,access_token"}, "long-user"), request.call_args_list[1].args)

    def test_wrong_page_does_not_get_saved(self):
        with patch.object(setup, "request", side_effect=[{"access_token": "long-user"},
                {"id": "999", "access_token": "page-secret"}]):
            with self.assertRaises(setup.SetupError):
                setup.provision("v26.0", "456", "secret", "user", "123")

    def test_environment_update_preserves_other_values_and_restricts_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / ".env"
            target.write_text("OTHER=value\nFACEBOOK_PAGE_TOKEN=old\n# comment\nFACEBOOK_PAGE_TOKEN=duplicate\n")
            setup.save_token(target, "new-secret")
            self.assertEqual("OTHER=value\nFACEBOOK_PAGE_TOKEN='new-secret'\n# comment\n", target.read_text())
            self.assertEqual(0o600, os.stat(target).st_mode & 0o777)
            with self.assertRaises(setup.SetupError):
                setup.save_token(target, "bad\nTOKEN=injected")
            self.assertIn("new-secret", target.read_text())

    def test_remote_errors_do_not_expose_tokens(self):
        with patch.object(setup.urllib.request, "urlopen", side_effect=setup.urllib.error.URLError("secret-token")):
            with self.assertRaises(setup.SetupError) as error:
                setup.request("v26.0", "123", {}, "secret-token")
            self.assertNotIn("secret-token", str(error.exception))


if __name__ == "__main__":
    unittest.main()
