"""Stage the offline guide for GitHub Pages with links to the deployed source."""

import argparse
from pathlib import Path
import re
import shutil
from urllib.parse import quote, unquote, urlsplit

from check_links import main as check_guide


GUIDE = Path(__file__).resolve().parent
REPO = GUIDE.parents[1]
REFERENCE = re.compile(r'\b(href|src)="([^"]*)"')


def hosted_reference(match, page, source_url):
    attribute, value = match.groups()
    url = urlsplit(value)
    if url.scheme or url.netloc or not url.path or url.path.startswith("/"):
        return match[0]

    target = (page.parent / unquote(url.path)).resolve()
    if target.is_relative_to(GUIDE):
        return match[0]

    path = quote(target.relative_to(REPO).as_posix())
    destination = f"{source_url}/{path}"
    if url.fragment:
        destination += f"#{url.fragment}"
    return f'{attribute}="{destination}"'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("source_url", help="GitHub blob URL through the commit SHA")
    args = parser.parse_args()

    if check_guide():
        return 1

    for source in sorted(GUIDE.rglob("*")):
        if not source.is_file() or source.suffix not in {".html", ".css", ".js", ".png"}:
            continue

        output = args.output / source.relative_to(GUIDE)
        output.parent.mkdir(parents=True, exist_ok=True)
        if source.suffix == ".html":
            html = source.read_text(encoding="utf-8")
            html = REFERENCE.sub(lambda match: hosted_reference(match, source, args.source_url), html)
            output.write_text(html, encoding="utf-8")
        else:
            shutil.copyfile(source, output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
