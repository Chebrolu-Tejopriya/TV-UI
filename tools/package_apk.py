"""Normalize APK ZIP paths and add DEX files using only Python's standard library."""

import argparse
import copy
from pathlib import Path
import zipfile


def package(base: Path, dex_dir: Path, output: Path) -> None:
    dex_files = sorted(dex_dir.glob("classes*.dex"))
    if not dex_files or not any(item.name == "classes.dex" for item in dex_files):
        raise ValueError("D8 did not produce classes.dex")
    if output.exists():
        raise ValueError("Refusing to overwrite a packaging output")
    renamed = 0
    names = set()
    with zipfile.ZipFile(base) as source, zipfile.ZipFile(output, "w") as target:
        target.comment = source.comment
        for entry in source.infolist():
            name = entry.filename.replace("\\", "/")
            if name in names:
                raise ValueError(f"Duplicate normalized APK entry: {name}")
            if name in {item.name for item in dex_files}:
                raise ValueError(f"Unexpected pre-existing DEX entry: {name}")
            names.add(name)
            renamed += name != entry.filename
            info = copy.copy(entry)
            info.filename = info.orig_filename = name
            target.writestr(info, source.read(entry))
        for dex in dex_files:
            target.write(dex, dex.name, compress_type=zipfile.ZIP_DEFLATED)
    print(f"Packaged {len(dex_files)} DEX file(s); normalized {renamed} ZIP paths")


def verify(apk: Path, assets: Path) -> None:
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate APK entries")
        if any("\\" in name for name in names):
            raise ValueError("APK still contains Windows path separators")
        for required in ("AndroidManifest.xml", "resources.arsc", "classes.dex"):
            archive.getinfo(required)
        asset_files = sorted(item for item in assets.rglob("*") if item.is_file())
        for asset in asset_files:
            name = "assets/" + asset.relative_to(assets).as_posix()
            info = archive.getinfo(name)
            if archive.read(name) != asset.read_bytes():
                raise ValueError(f"Asset differs from source: {name}")
            if asset.suffix.lower() in {".ttf", ".mp4"} and info.compress_type != zipfile.ZIP_STORED:
                raise ValueError(f"Streaming asset is compressed: {name}")
        if any(name.startswith("lib/") and name.endswith(".so") for name in names):
            raise ValueError("Unexpected native library in APK")
        bad_entry = archive.testzip()
        if bad_entry:
            raise ValueError(f"Failed ZIP CRC check: {bad_entry}")
    print(f"Verified {len(asset_files)} assets, uncompressed fonts/video, DEX, ZIP integrity, and no native libraries")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pack = commands.add_parser("package")
    pack.add_argument("base", type=Path)
    pack.add_argument("dex_dir", type=Path)
    pack.add_argument("output", type=Path)
    check = commands.add_parser("verify")
    check.add_argument("apk", type=Path)
    check.add_argument("assets", type=Path)
    args = parser.parse_args()
    if args.command == "package":
        package(args.base, args.dex_dir, args.output)
    else:
        verify(args.apk, args.assets)


if __name__ == "__main__":
    main()
