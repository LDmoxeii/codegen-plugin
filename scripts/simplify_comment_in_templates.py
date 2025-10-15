#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简化模板中的 Comment 处理
将 commentLines 循环替换为简单的 Comment 字符串
"""

import os
import re
import sys

def simplify_comment_lines(templates_dir):
    """
    替换模板中的 commentLines 循环为简单的 Comment 变量

    将以下模式：
    {%- for line in commentLines %}
     * {{ line }}
    {%- endfor %}

    替换为：
     * {{ Comment }}
    """

    # 定义替换模式
    patterns = [
        # Pattern 1: {%- for line in commentLines %} ... {%- endfor %}
        (
            r'\{\%-?\s*for\s+line\s+in\s+commentLines\s*%\}\s*\n\s*\*\s*\{\{\s*line\s*\}\}\s*\n\s*\{\%-?\s*endfor\s*%\}',
            ' * {{ Comment }}'
        ),
        # Pattern 2: {% for line in commentLines %} ... {% endfor %}
        (
            r'\{\%\s*for\s+line\s+in\s+commentLines\s*%\}\s*\n\s*\*\s*\{\{\s*line\s*\}\}\s*\n\s*\{\%\s*endfor\s*%\}',
            ' * {{ Comment }}'
        ),
    ]

    # 获取所有 .peb 文件
    files = [f for f in os.listdir(templates_dir) if f.endswith('.peb')]

    updated_count = 0
    unchanged_count = 0

    for filename in files:
        filepath = os.path.join(templates_dir, filename)

        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            original = content

            # 应用所有替换模式
            for pattern, replacement in patterns:
                content = re.sub(pattern, replacement, content)

            # 如果内容有变化，写回文件
            if content != original:
                with open(filepath, 'w', encoding='utf-8', newline='') as f:
                    f.write(content)
                print(f'✓ Updated: {filename}')
                updated_count += 1
            else:
                print(f'  No change: {filename}')
                unchanged_count += 1

        except Exception as e:
            print(f'✗ Error processing {filename}: {e}', file=sys.stderr)

    print(f'\n=== Summary ===')
    print(f'Updated: {updated_count} files')
    print(f'Unchanged: {unchanged_count} files')
    print(f'Total: {updated_count + unchanged_count} files')

if __name__ == '__main__':
    # 模板目录路径
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    templates_dir = os.path.join(project_root, 'plugin', 'src', 'main', 'resources', 'templates')

    if not os.path.exists(templates_dir):
        print(f'Error: Templates directory not found: {templates_dir}', file=sys.stderr)
        sys.exit(1)

    print(f'Processing templates in: {templates_dir}\n')
    simplify_comment_lines(templates_dir)
    print('\nDone!')
