#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简化 aggregate 生成器中的 Comment 处理
将复杂的 commentLines 处理逻辑替换为直接使用 SqlSchemaUtils.getComment(table)
"""

import re
import os

def simplify_comment_in_generator(filepath):
    """
    替换生成器中的 commentLines 处理逻辑

    将以下代码：
        // 准备注释行
        val commentLines = SqlSchemaUtils.getComment(table)
            .split(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                if (line.endsWith(";")) line.dropLast(1).trim() else line
            }
            .filter { it.isNotEmpty() }

        with(context) {
            resultContext.putContext(tag, "commentLines", commentLines)
        }

    替换为：
        resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
    """

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content

    # Pattern: 匹配从 "// 准备注释行" 开始到 putContext(tag, "commentLines", commentLines) 结束的整个块
    pattern = r'// 准备注释行\s*\n\s*val commentLines = SqlSchemaUtils\.getComment\(table\).*?with\(context\) \{\s*\n\s*resultContext\.putContext\(tag, "commentLines", commentLines\)\s*\n\s*\}'

    replacement = 'resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))'

    content = re.sub(pattern, replacement, content, flags=re.DOTALL)

    return content, content != original

def main():
    # 需要处理的文件列表
    files = [
        'plugin/src/main/kotlin/com/only/codegen/generators/aggregate/EntityGenerator.kt',
        'plugin/src/main/kotlin/com/only/codegen/generators/aggregate/DomainEventGenerator.kt',
        'plugin/src/main/kotlin/com/only/codegen/generators/aggregate/DomainEventHandlerGenerator.kt',
        'plugin/src/main/kotlin/com/only/codegen/generators/aggregate/FactoryGenerator.kt',
        'plugin/src/main/kotlin/com/only/codegen/generators/aggregate/SpecificationGenerator.kt',
        'plugin/src/main/kotlin/com/only/codegen/generators/aggregate/SchemaGenerator.kt'
    ]

    updated_count = 0
    unchanged_count = 0
    error_count = 0

    print("Processing aggregate generators...\n")

    for filepath in files:
        filename = os.path.basename(filepath)

        if not os.path.exists(filepath):
            print(f'  Skip: {filename} (not found)')
            continue

        try:
            new_content, changed = simplify_comment_in_generator(filepath)

            if changed:
                with open(filepath, 'w', encoding='utf-8', newline='') as f:
                    f.write(new_content)
                print(f'✓ Updated: {filename}')
                updated_count += 1
            else:
                print(f'  No change: {filename}')
                unchanged_count += 1

        except Exception as e:
            print(f'✗ Error: {filename} - {e}')
            error_count += 1

    print(f'\n=== Summary ===')
    print(f'Updated: {updated_count} files')
    print(f'Unchanged: {unchanged_count} files')
    print(f'Errors: {error_count} files')
    print(f'Total: {len(files)} files')
    print('\nDone!')

if __name__ == '__main__':
    main()
