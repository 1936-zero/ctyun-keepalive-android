# coding=utf-8
# 从兼容层导入主类（新的模块化结构）
from .compat.v1 import DdddOcr

from .utils import (
    ALLOWED_IMAGE_FORMATS,
    MAX_IMAGE_BYTES,
    MAX_IMAGE_SIDE,
    DdddOcrInputError,
    InvalidImageError,
    TypeError,
    base64_to_image,
    get_img_base64,
)

__all__ = [
    "ALLOWED_IMAGE_FORMATS",
    "MAX_IMAGE_BYTES",
    "MAX_IMAGE_SIDE",
    "DdddOcr",
    "DdddOcrInputError",
    "InvalidImageError",
    "TypeError",
    "base64_to_image",
    "get_img_base64",
]
