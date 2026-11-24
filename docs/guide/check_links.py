"""Check local href and src targets in the offline HTML guide."""

import sys
from functools import cache
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


class Page(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.anchors = set()
        self.references = []
        self.feed(path.read_text(encoding="utf-8"))
        self.close()

    def handle_starttag(self, tag, attrs):
        line, _ = self.getpos()
        for name, value in attrs:
            if value is None:
                continue
            if name == "id" or (tag == "a" and name == "name"):
                self.anchors.add(value)
            if name in {"href", "src"}:
                self.references.append((line, name, value))


@cache
def read_page(path):
    return Page(path)


def check_reference(source, value):
    url = urlsplit(value.strip())
    # The guide uses relative paths so the same links work over file and HTTP.
    if url.scheme or url.netloc or url.path.startswith("/"):
        return None

    target = (source.parent / unquote(url.path)).resolve() if url.path else source
    if target.is_dir():
        target /= "index.html"
    if not target.is_file():
        return "target does not exist"

    fragment = unquote(url.fragment)
    if fragment and target.suffix.lower() in {".html", ".htm"}:
        if fragment not in read_page(target).anchors:
            return f"missing HTML fragment {fragment!r}"
    return None


def main():
    guide = Path(__file__).resolve().parent
    pages = sorted(guide.rglob("*.html"))
    errors = 0
    checked = 0
    for source in pages:
        for line, attribute, value in read_page(source).references:
            checked += 1
            try:
                problem = check_reference(source, value)
            except (OSError, ValueError) as error:
                problem = str(error)
            if problem:
                print(f"{source.relative_to(guide)}:{line}: {attribute}={value!r}: {problem}",
                      file=sys.stderr)
                errors += 1

    print(f"Scanned {checked} references across {len(pages)} guide pages. "
          f"Found {errors} broken references.")
    return int(errors != 0)


if __name__ == "__main__":
    sys.exit(main())
