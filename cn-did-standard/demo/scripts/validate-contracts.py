#!/usr/bin/env python3
"""Static DID contract checks, independent of Docker (requires PyYAML)."""
from pathlib import Path
import re
import subprocess
import yaml

root = Path(__file__).resolve().parents[1]
for relative in ("interface/openapi/did-v1.yaml", "bft-reader/openapi.yaml"):
    path = root / relative
    document = yaml.safe_load(path.read_text())

    def check(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == "$ref":
                    assert child.startswith("#/"), (path, child)
                    target = document
                    for part in child[2:].split("/"):
                        target = target[part.replace("~1", "/").replace("~0", "~")]
                else:
                    check(child)
        elif isinstance(value, list):
            for child in value:
                check(child)

    check(document)
    print(f"OpenAPI local references: {relative}: PASS")

for script in sorted((root / "scripts").glob("*.sh")):
    subprocess.run(["sh", "-n", str(script)], check=True)
print("Shell syntax: PASS")

for base in (root / "java-api/src/main", root / "scripts", root / "sql", root / "interface", root / "bft-reader/java-api/src/main"):
    for path in base.rglob("*"):
        if not path.is_file() or path.suffix not in {".java", ".sh", ".sql", ".md", ".yaml"}:
            continue
        content = path.read_text()
        assert not re.search(r"\b(?:from|join)\s+[\"']?__\w+", content, re.I), path
        assert "snapshotToken" not in content, path
        assert "active-only demo" not in content, path
for path in root.parent.rglob("*"):
    if any(part in {"target", ".daml", ".deps", ".git"} for part in path.parts):
        continue
    if not path.is_file() or path.suffix not in {".java", ".sh", ".sql", ".md", ".yaml", ".py"}:
        continue
    content = path.read_text()
    assert not re.search(r"\b(?:created|archived|Created|Archived)To\b", content), path
    assert not re.search(r"(?:created|archived)_effective_at\s*<=", content), path
    if path != Path(__file__).resolve():
        assert not re.search(r"inclusive (?:effective-time|ISO-8601)|effective-time filters use inclusive >= and <=", content, re.I), path
print("Stale/physical-table and half-open time-filter audit: PASS")

for module in ("java-api", "bft-reader/java-api"):
    import xml.etree.ElementTree as ET
    reports = list((root / module / "target/surefire-reports").glob("TEST-*.xml"))
    counts = {name: 0 for name in ("tests", "failures", "errors", "skipped")}
    for report in reports:
        suite = ET.parse(report).getroot()
        for name in counts:
            counts[name] += int(suite.get(name, 0))
    print(f"{module} existing test reports: {counts}")
