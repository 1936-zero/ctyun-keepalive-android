# coding=utf-8
"""
工具函数模块
提供图像处理、异常处理、输入验证等工具函数
"""

# 从新的模块化结构导入
from .image_io import png_rgba_black_preprocess
from .exceptions import DDDDOCRError, ModelLoadError, ImageProcessError
from .validators import validate_image_input, validate_model_config

# 从兼容模块导入（保持向后兼容）
from .compat import (
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
    # 新模块化结构的导出
    'png_rgba_black_preprocess',
    'DDDDOCRError',
    'ModelLoadError',
    'ImageProcessError',
    'validate_image_input',
    'validate_model_config',
    # 从 compat.py 导入的兼容性导出
    'ALLOWED_IMAGE_FORMATS',
    'MAX_IMAGE_BYTES',
    'MAX_IMAGE_SIDE',
    'DdddOcrInputError',
    'InvalidImageError',
    'TypeError',
    'base64_to_image',
    'get_img_base64',
]
