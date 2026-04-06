# coding=utf-8
"""
核心功能模块
提供OCR识别、目标检测、滑块匹配等核心功能
"""

from .base import BaseEngine
from .ocr_engine import OCREngine
from .detection_engine import DetectionEngine
from .slide_engine import SlideEngine

__all__ = [
    'BaseEngine',
    'OCREngine',
    'DetectionEngine',
    'SlideEngine',
    'DdddOcr'
]


def __getattr__(name: str):
    # 延迟导入，避免 compat.v1 -> core -> compat.v1 的循环依赖
    if name == 'DdddOcr':
        from ..compat.v1 import DdddOcr as _DdddOcr
        return _DdddOcr
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
