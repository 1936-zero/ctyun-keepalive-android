# coding=utf-8
"""
ddddocr HTTP API服务模块
提供RESTful API接口和MCP协议支持
"""

__version__ = "1.6.1"
__author__ = "sml2h3"

from .server import create_app, run_server
from .models import *
from .app import app, main

__all__ = ['create_app', 'run_server', 'app', 'main']
