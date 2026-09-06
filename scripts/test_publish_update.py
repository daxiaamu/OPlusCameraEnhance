import importlib.util, json, os, pathlib, subprocess, tempfile, unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location("publish",pathlib.Path(__file__).with_name("publish_update.py"))
publish=importlib.util.module_from_spec(spec)
spec.loader.exec_module(publish)

class PublishChecks(unittest.TestCase):
 def execute(self, good_count=10, package="com.daxiaamu.opluscameraenhance", assets=1):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory); previous=os.getcwd()
   try:
    os.chdir(root)
    (root/"sdk/build-tools/36").mkdir(parents=True)
    (root/"sdk/build-tools/36/aapt2").touch()
    (root/"update").mkdir()
    (root/"update/policy-stable.json").write_text(json.dumps(dict(policyRevision=1,maxForcedVersionCode=0,reason="test")))
    release=dict(tag_name="v0.3.1",prerelease=False,published_at="2026-09-06T00:00:00Z",body="Test",assets=[
     dict(name=f"app{i}.apk",id=i,browser_download_url=f"https://github.com/owner/repo/releases/download/v0.3.1/app{i}.apk")
     for i in range(assets)])
    (root/"event.json").write_text(json.dumps(dict(release=release)))
    def run(args,**kw):
     if args[0]=="gh": kw["stdout"].write(b"test-apk")
     return subprocess.CompletedProcess(args,0)
    def output(args,**kw):
     if args[0]=="git": return "f"*40
     return f"package: name='{package}' versionCode='9' versionName='0.3.1'\n"
    def download(url,destination):
     index=int(destination.stem.split("-")[-1])
     if index>=good_count: raise OSError("CDN unavailable")
     destination.write_bytes(b"test-apk")
    with patch.dict(os.environ,dict(GITHUB_EVENT_PATH=str(root/"event.json"),GITHUB_REPOSITORY="owner/repo",ANDROID_HOME=str(root/"sdk"))):
     with patch.object(publish.subprocess,"run",side_effect=run),patch.object(publish.subprocess,"check_output",side_effect=output),patch.object(publish,"checked_download",side_effect=download):
      publish.main()
    pointer=json.loads((root/"update/update.json").read_text())
    manifest=next((root/"update/manifests").glob("*.json"))
    self.assertEqual(pointer["manifestSha256"],publish.digest(manifest.read_bytes()))
    self.assertEqual(json.loads(manifest.read_text())["maxForcedVersionCode"],0)
   finally: os.chdir(previous)
 def test_valid_pointer_and_policy(self): self.execute()
 def test_four_cdns_rejected(self):
  with self.assertRaises(AssertionError): self.execute(good_count=4)
 def test_wrong_package_rejected(self):
  with self.assertRaises(AssertionError): self.execute(package="wrong.package")
 def test_multiple_apks_rejected(self):
  with self.assertRaises(AssertionError): self.execute(assets=2)
if __name__=="__main__": unittest.main()