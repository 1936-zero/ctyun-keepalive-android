#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
允许通过 `python -m ddddocr.api` 启动 API 服务（向后兼容）。
"""
from .app import main


if __name__ == "__main__":
    main()
