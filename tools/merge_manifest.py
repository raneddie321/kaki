#!/usr/bin/env python3
"""Merges library manifests (from AARs) into the app manifest, like Gradle's manifest merger but
only for what our libraries need: permissions, <queries> entries and <application> components.

Usage: merge_manifest.py app/AndroidManifest.xml out/AndroidManifest.xml app.id lib1/AndroidManifest.xml ...
"""
import sys
import xml.etree.ElementTree as ET

ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)
NAME = '{%s}name' % ANDROID


def load(path, app_id):
    with open(path, encoding='utf-8') as f:
        text = f.read().replace('${applicationId}', app_id)
    return ET.fromstring(text)


def main():
    app_path, out_path, app_id, libs = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
    app = load(app_path, app_id)
    application = app.find('application')
    have = {(e.tag, e.get(NAME)) for e in app.iter()}
    queries = app.find('queries')
    for lib in libs:
        root = load(lib, app_id)
        for perm in root.findall('uses-permission'):
            if ('uses-permission', perm.get(NAME)) not in have:
                app.insert(0, perm)
                have.add(('uses-permission', perm.get(NAME)))
        for q in root.findall('queries'):
            if queries is None:
                queries = ET.Element('queries')
                app.insert(0, queries)
            for child in list(q):
                queries.append(child)
        lib_app = root.find('application')
        if lib_app is not None:
            for comp in list(lib_app):
                key = (comp.tag, comp.get(NAME))
                if key not in have:
                    application.append(comp)
                    have.add(key)
    ET.ElementTree(app).write(out_path, encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    main()
