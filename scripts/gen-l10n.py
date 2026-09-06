"""Turn the settings translation tables into Android string resources.

Reads every extensions/tiktok/src/main/l10n/<lang>.tsv (English TAB translation, one entry
per line, # comments) and writes:

  extensions/tiktok/src/main/res/values/strings.xml          the English text
  extensions/tiktok/src/main/res/values-<lang>/strings.xml   each translation
  extensions/tiktok/src/main/l10n/index.txt                  the resource folders, one per line

Resource names are mtp_ followed by the CRC32 of the English text as UTF-8, which is what
L10n.key() in the extension computes at runtime, so the Java side never needs a table.

Run from the repository root: python scripts/gen-l10n.py
"""
import io
import os
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
L10N = os.path.join(ROOT, "extensions", "tiktok", "src", "main", "l10n")
RES = os.path.join(ROOT, "extensions", "tiktok", "src", "main", "res")


def key(english):
    return "mtp_%08x" % (zlib.crc32(english.encode("utf-8")) & 0xFFFFFFFF)


def escape(text):
    text = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    return text


def read(path):
    entries = {}
    with io.open(path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            line = line.rstrip("\r\n")
            if not line or line.startswith("#"):
                continue
            if "\t" not in line:
                sys.exit("%s:%d: no tab" % (path, number))
            english, translated = line.split("\t", 1)
            english = english.replace("\\n", "\n")
            translated = translated.replace("\\n", "\n")
            if english in entries:
                sys.exit("%s:%d: duplicate entry: %s" % (path, number, english))
            entries[english] = translated
    return entries


def write(path, entries, comment_source):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for english in sorted(entries, key=key):
        translated = entries[english]
        name = key(english)
        if comment_source:
            lines.append("    <!-- %s -->" % english.replace("--", "- -").replace("\n", " "))
        formatted = ' formatted="false"' if "%" in translated and "%1$" not in translated and translated.count("%") > 1 else ""
        lines.append('    <string name="%s"%s>%s</string>' % (name, formatted, escape(translated)))
    lines.append("</resources>")
    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")


def main():
    tables = {}
    for name in sorted(os.listdir(L10N)):
        if name.endswith(".tsv"):
            tables[name[:-4]] = read(os.path.join(L10N, name))
    if not tables:
        sys.exit("no .tsv tables in " + L10N)

    english = {}
    for entries in tables.values():
        for source in entries:
            english[source] = source
    write(os.path.join(RES, "values", "strings.xml"), english, comment_source=False)
    folders = ["values"]
    for lang, entries in tables.items():
        folder = "values-" + lang
        folders.append(folder)
        write(os.path.join(RES, folder, "strings.xml"), entries, comment_source=True)
    with io.open(os.path.join(L10N, "index.txt"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(folders) + "\n")
    print("wrote %d strings, folders: %s" % (len(english), ", ".join(folders)))


if __name__ == "__main__":
    main()
