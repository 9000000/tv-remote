#!/usr/bin/env python3
"""Checks the TV Games quiz bank, docs/games/web/quiz/bank.json (the quiz is a web game of the site: no app release to change it)."""
import json, sys, collections, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
A = R / 'docs/games/web/quiz/bank.json'
bad = []
d = json.loads(A.read_text(encoding='utf8'))
cats = {c['id'] for c in d['categories']}
ids, seen, per, cs = set(), set(), collections.Counter(), collections.Counter()
for q in d['questions']:
    i = q.get('id')
    if i in ids: bad.append(f'dup id {i}')
    ids.add(i)
    if q['cat'] not in cats: bad.append(f'{i}: unknown cat')
    if q['d'] not in (1, 2, 3): bad.append(f'{i}: d')
    if len(q['q']) != 2 or not all(q['q']): bad.append(f'{i}: q')
    if len(q['o']) != 2 or any(len(o) != 4 or not all(o) or len(set(o)) != 4 for o in q['o']): bad.append(f'{i}: options')
    if q['c'] not in range(4): bad.append(f'{i}: c')
    for k in (0, 1):
        if q['q'][k] in seen: bad.append(f'{i}: dup question')
        seen.add(q['q'][k])
    per[q['cat']] += 1; cs[q['c']] += 1
print(len(d['questions']), dict(per), 'c:', dict(sorted(cs.items())))
print('\n'.join(bad) or 'OK')
sys.exit(1 if bad else 0)
