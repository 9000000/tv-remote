#!/usr/bin/env python3
"""The paths whose change means a new number for one app, read from its own build.gradle.kts: the release must not miss code an
app compiles (a fix in app/shared/src/tvui reached TV Home but release.sh watched only app/shared/src/home, issue #1), nor bump an
app for code it never compiles (the same fix would have made a second phone app version on one day).

  dev/release/app_paths.py <android|tv|home|channels|games|desktop>   -> git pathspecs, one per line

android / tv build on :shared, so they take app/shared whole, less each app/shared/src/<dir> that neither they nor :shared name.
desktop (the computer app, JVM, no :shared) takes every app/shared/... it names, the web page and the words too.
The TV apps take only the app/shared/src/... they name; TV Home's packed copy of the web page (packHomeWeb) is not counted, as before.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../..')


def named(app, only_src=True):
    s = open(os.path.join(ROOT, 'app', app, 'build.gradle.kts')).read()
    return sorted(set(re.findall(r'"\.\./shared/(%s[^"]+)"' % ('src/' if only_src else ''), s)))


def paths(app):
    out = ['app/' + app, ':(exclude)app/%s/*.apk' % app, ':(exclude)app/%s/tests' % app]
    mine = named(app)
    if app in ('android', 'tv'):
        shared = open(os.path.join(ROOT, 'app/shared/build.gradle.kts')).read()
        used = {p.split('/')[1] for p in mine} | set(re.findall(r'"src/(\w+)/', shared)) | {'main'}
        out += ['app/shared', ':(exclude)app/shared/tests', ':(exclude)app/shared/src/test']
        out += [':(exclude)app/shared/src/' + d for d in sorted(os.listdir(os.path.join(ROOT, 'app/shared/src'))) if d not in used and d != 'test']
    elif app == 'desktop':
        out += ['app/shared/' + p for p in named(app, only_src=False)]
    else:
        out += ['app/shared/' + p for p in mine]
    return out


if __name__ == '__main__':
    print('\n'.join(paths(sys.argv[1])))
