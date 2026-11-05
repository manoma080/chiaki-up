#!/usr/bin/env python3
"""Copy the bundled nanopb generator and adapt its protobuf API in the build tree."""
import os
import shutil
import sys


def prepare(source, destination):
    generator = os.path.join(source, 'nanopb_generator.py')
    with open(generator, encoding='utf-8') as f:
        text = f.read()
    old_import = 'import google.protobuf.reflection as reflection'
    new_import = 'import google.protobuf.message_factory as reflection'
    old_call = 'reflection.MakeClass(desc)'
    new_call = 'reflection.GetMessageClass(desc)'
    if (text.count(old_import), text.count(old_call), text.count(new_import), text.count(new_call)) == (1, 1, 0, 0):
        text = text.replace(old_import, 'import google.protobuf.message_factory as message_factory\n    import google.protobuf.reflection as reflection')
        text = text.replace(old_call, '(message_factory.GetMessageClass(desc) if hasattr(message_factory, "GetMessageClass") else reflection.MakeClass(desc))')
    elif (text.count(old_import), text.count(old_call), text.count(new_import), text.count(new_call)) == (0, 0, 1, 1):
        text = text.replace(new_import, 'import google.protobuf.message_factory as message_factory\n    import google.protobuf.reflection as reflection')
        text = text.replace(new_call, '(message_factory.GetMessageClass(desc) if hasattr(message_factory, "GetMessageClass") else reflection.MakeClass(desc))')
    else:
        raise ValueError('Unsupported nanopb generator: expected known protobuf API calls')

    for root, dirs, files in os.walk(source):
        dirs[:] = [d for d in dirs if d != '__pycache__']
        target = os.path.join(destination, os.path.relpath(root, source))
        os.makedirs(target, exist_ok=True)
        for name in files:
            if name != 'nanopb_generator.py' and not name.endswith(('.pyc', '.pyo')):
                shutil.copy2(os.path.join(root, name), os.path.join(target, name))
    with open(os.path.join(destination, 'nanopb_generator.py'), 'w', encoding='utf-8') as f:
        f.write(text)


if __name__ == '__main__':
    prepare(sys.argv[1], sys.argv[2])
