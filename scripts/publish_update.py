"""Run only after an authorized GitHub Release exists. Never creates a release."""
import concurrent.futures, datetime, hashlib, json, os, pathlib, re, subprocess, tempfile, urllib.parse

def digest(data):
    return hashlib.sha256(data).hexdigest()

def checked_download(url, destination):
    subprocess.run(["curl", "--fail", "--location", "--proto", "=https", "--proto-redir", "=https",
                    "--connect-timeout", "10", "--max-time", "180", "--output", str(destination), url], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def main():
    event = json.loads(pathlib.Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    release = event["release"]
    repo = os.environ["GITHUB_REPOSITORY"]
    assets = [a for a in release["assets"] if a["name"].endswith(".apk")]
    assert len(assets) == 1, "Release must contain exactly one APK"
    asset = assets[0]
    with tempfile.TemporaryDirectory() as work:
        work = pathlib.Path(work)
        apk = work / "release.apk"
        with apk.open("wb") as out:
            subprocess.run(["gh", "api", "-H", "Accept: application/octet-stream",
                            f"/repos/{repo}/releases/assets/{asset['id']}"], stdout=out, check=True)
        aapts = list(pathlib.Path(os.environ["ANDROID_HOME"], "build-tools").glob("*/aapt2"))
        assert aapts
        badging = subprocess.check_output([str(sorted(aapts)[-1]), "dump", "badging", str(apk)], text=True)
        fields = dict(re.findall(r"(\w+)='([^']*)'", badging.splitlines()[0]))
        assert fields["name"] == "com.daxiaamu.opluscameraenhance"
        code, name = int(fields["versionCode"]), fields["versionName"]
        assert (release["tag_name"][1:] if release["tag_name"].startswith("v") else release["tag_name"]) == name
        channel = "beta" if release["prerelease"] else "stable"
        assert ("-" in name) == release["prerelease"], "Release channel must match APK version"
        policy = json.loads(pathlib.Path(f"update/policy-{channel}.json").read_text())
        assert isinstance(policy["policyRevision"], int) and policy["policyRevision"] > 0
        assert isinstance(policy["maxForcedVersionCode"], int) and 0 <= policy["maxForcedVersionCode"] < code
        assert policy["reason"].strip()
        target = pathlib.Path("update/update-beta.json" if channel == "beta" else "update/update.json")
        if target.exists():
            assert policy["policyRevision"] > json.loads(target.read_text())["policyRevision"]
        sha, size = digest(apk.read_bytes()), apk.stat().st_size
        official = asset["browser_download_url"]
        hosts = ["ghfast.top", "gh-proxy.com", "ghproxy.net", "gh.llkk.cc", "ghp.keleyaa.com",
                 "gh.monlor.com", "ghproxy.vip", "gh.jasonzeng.dev", "gh.3w.pm", "gh-proxy.org"]
        urls = [f"https://{h}/{official}" for h in hosts]
        def verify(item):
            index, url = item
            try:
                candidate = work / f"cdn-{index}.apk"
                checked_download(url, candidate)
                assert candidate.stat().st_size == size and digest(candidate.read_bytes()) == sha
                return url
            except Exception:
                return None
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
            good = [url for url in pool.map(verify, enumerate(urls)) if url]
        assert len({urllib.parse.urlparse(url).hostname for url in good}) >= 5, "Five verified CDN hosts required"
        manifest = dict(schemaVersion=1, channel=channel, versionCode=code, versionName=name,
                        publishedAt=release["published_at"], changelog=release.get("body") or "",
                        maxForcedVersionCode=policy["maxForcedVersionCode"], policyRevision=policy["policyRevision"],
                        urls=good + [official], sha256=sha, size=size)
        content = json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
        manifest_sha = digest(content)
        relative = f"update/manifests/{channel}-{policy['policyRevision']}-{manifest_sha}.json"
        destination = pathlib.Path(relative)
        assert not destination.exists(), "Immutable manifest cannot be overwritten"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(content)
        subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
        subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
        subprocess.run(["git", "add", relative], check=True)
        subprocess.run(["git", "commit", "-m", "chore: add immutable update manifest"], check=True)
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        expiry = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=365)
        pointer = dict(policyRevision=policy["policyRevision"], manifestSha256=manifest_sha,
                       manifestUrl=f"https://raw.githubusercontent.com/{repo}/{commit}/{relative}",
                       expiresAt=expiry.isoformat())
        temporary = target.with_suffix(".tmp")
        temporary.write_text(json.dumps(pointer, indent=2) + "\n")
        temporary.replace(target)
if __name__ == "__main__":
    main()