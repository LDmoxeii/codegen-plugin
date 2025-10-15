#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析所有模板文件的 imports 并生成 ImportManager 类的参考数据
"""

import os
import re
from collections import defaultdict

def extract_imports_from_template(filepath):
    """从模板文件中提取所有硬编码的 imports"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    imports = []
    for line in content.split('\n'):
        # 匹配 import 语句（不包含模板变量的）
        if line.strip().startswith('import ') and '{{' not in line:
            import_str = line.strip().replace('import ', '')
            imports.append(import_str)

    return imports

def main():
    templates_dir = 'plugin/src/main/resources/templates'

    # 模板文件到生成器的映射
    template_to_generator = {
        'command.kt.peb': 'Command',
        'query.kt.peb': 'Query',
        'domain_event.kt.peb': 'DomainEvent',
        'domain_event_handler.kt.peb': 'DomainEventHandler',
        'factory.kt.peb': 'Factory',
        'aggregate.kt.peb': 'Aggregate',
        'enum.kt.peb': 'Enum',
        'client.kt.peb': 'Client',
        'client_handler.kt.peb': 'ClientHandler',
        'domain_service.kt.peb': 'DomainService',
        'integration_event.kt.peb': 'IntegrationEvent',
        'integration_event_handler.kt.peb': 'IntegrationEventHandler',
    }

    print("=== Template Imports Analysis ===\n")

    for template_file, generator_name in template_to_generator.items():
        filepath = os.path.join(templates_dir, template_file)

        if not os.path.exists(filepath):
            print(f"⚠ {template_file} - NOT FOUND")
            continue

        imports = extract_imports_from_template(filepath)

        if not imports:
            print(f"✓ {generator_name}ImportManager - No base imports (dynamic only)")
            print()
            continue

        print(f"✓ {generator_name}ImportManager:")
        print(f"  File: {template_file}")
        print(f"  Base imports:")
        for imp in imports:
            print(f"    - {imp}")
        print()

if __name__ == '__main__':
    main()
