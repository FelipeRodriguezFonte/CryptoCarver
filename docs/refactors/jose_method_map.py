#!/usr/bin/env python3
"""Brace-balanced JOSE inventory; strings/comments cannot affect method boundaries."""
import re
from pathlib import Path

SOURCE = Path('src/main/java/com/cryptocarver/ui/JOSEController.java')

def mask(source):
    pattern = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(pattern, lambda m: ''.join('\n' if c == '\n' else ' ' for c in m[0]), source)

def methods(source):
    code = mask(source)
    depth, levels = 0, []
    for c in code:
        levels.append(depth)
        depth += (c == '{') - (c == '}')
    result = []
    pattern = r'(?m)^    (?:(?:public|private|protected|static|final|synchronized)\s+)*(?:[\w<>?,.\[\]]+\s+)?(\w+)\s*\(([^;{}]*?)\)\s*(?:throws [\w., ]+)?\s*\{'
    for match in re.finditer(pattern, code):
        if levels[match.start()] != 1 or 'record ' in source[match.start():match.end()]: continue
        opening = match.end() - 1
        end = opening + 1
        while levels[end] != 2 or code[end] != '}': end += 1
        result.append(dict(name=match[1], start=match.start(), body=opening+1, end=end+1,
                           line=source.count('\n',0,match.start())+1,
                           lines=source.count('\n',match.start(),end+1)+1,
                           signature=source[match.start():opening].strip(),
                           params=source[match.start(2):match.end(2)]))
    return result

def fields(source):
    code=mask(source)
    result={}
    for m in re.finditer(r'@FXML\s+private\s+([\w<>.]+)\s+([\w,\s]+);',code):
        for field in m[2].split(','): result[field.strip()]=m[1]
    return result

if __name__ == '__main__':
    source=SOURCE.read_text()
    print(f'{SOURCE}: {len(source.splitlines())} lines, {len(methods(source))} methods')
    for m in methods(source): print(f"{m['line']:4} {m['lines']:3} {m['signature']}")
