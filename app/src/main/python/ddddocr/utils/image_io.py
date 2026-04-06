# coding=utf-8
"""
图像输入输出工具模块
提供图像格式转换、读取、保存等功能
"""

import io
import os
import base64
import pathlib
from typing import Union, BinaryIO
from PIL import Image
import numpy as np

from .exceptions import ImageProcessError


def base64_to_image(img_base64: str) -> Image.Image:
    """
    将base64编码的图片转换为PIL Image对象
    
    Args:
        img_base64: base64编码的图片字符串
        
    Returns:
        PIL Image对象
        
    Raises:
        ImageProcessError: 当base64解码或图片加载失败时
    """
    try:
        img_data = base64.b64decode(img_base64)
        return Image.open(io.BytesIO(img_data))
    except Exception as e:
        raise ImageProcessError(f"base64图片解码失败: {str(e)}") from e


def get_img_base64(image_path: Union[str, pathlib.Path]) -> str:
    """
    读取图片文件并转换为base64编码字符串
    
    Args:
        image_path: 图片文件路径
        
    Returns:
        base64编码的图片字符串
        
    Raises:
        ImageProcessError: 当文件读取失败时
    """
    try:
        with open(image_path, 'rb') as fp:
            img_base64 = base64.b64encode(fp.read())
            return img_base64.decode()
    except Exception as e:
        raise ImageProcessError(f"图片文件读取失败: {str(e)}") from e


def png_rgba_black_preprocess(img: Image.Image) -> Image.Image:
    """
    处理PNG图片的RGBA透明背景，将透明部分设置为白色背景
    
    Args:
        img: PIL Image对象
        
    Returns:
        处理后的PIL Image对象
        
    Raises:
        ImageProcessError: 当图片处理失败时
    """
    try:
        width = img.width
        height = img.height
        image = Image.new('RGB', size=(width, height), color=(255, 255, 255))
        image.paste(img, (0, 0), mask=img)
        return image
    except Exception as e:
        raise ImageProcessError(f"PNG透明背景处理失败: {str(e)}") from e


def load_image_from_input(img_input: Union[bytes, str, pathlib.PurePath, Image.Image, np.ndarray]) -> Image.Image:
    """
    从多种输入格式加载图片

    Args:
        img_input: 图片输入，支持bytes、base64字符串、文件路径、PIL Image对象或numpy数组

    Returns:
        PIL Image对象

    Raises:
        ImageProcessError: 当图片加载失败时
    """
    try:
        if isinstance(img_input, bytes):
            return Image.open(io.BytesIO(img_input))
        elif isinstance(img_input, Image.Image):
            return img_input.copy()
        elif isinstance(img_input, np.ndarray):
            return _numpy_to_pil_image(img_input)
        elif isinstance(img_input, str):
            # 先尝试作为文件路径，如果失败则作为base64
            if os.path.exists(img_input):
                return Image.open(img_input)
            else:
                return base64_to_image(img_input)
        elif isinstance(img_input, pathlib.PurePath):
            return Image.open(img_input)
        else:
            supported_types = (bytes, str, pathlib.PurePath, Image.Image, np.ndarray)
            raise ImageProcessError(
                f"不支持的图片输入类型: {type(img_input)}。"
                f"支持的类型: {supported_types}"
            )
    except ImageProcessError:
        raise
    except Exception as e:
        raise ImageProcessError(f"图片加载失败: {str(e)}") from e


def _numpy_to_pil_image(array: np.ndarray) -> Image.Image:
    """
    将numpy数组转换为PIL Image对象

    Args:
        array: numpy数组

    Returns:
        PIL Image对象

    Raises:
        ImageProcessError: 当转换失败时
    """
    try:
        # 确保数组是正确的数据类型
        if array.dtype != np.uint8:
            # 如果是浮点数，假设范围是0-1，转换为0-255
            if array.dtype in [np.float32, np.float64]:
                if array.max() <= 1.0:
                    array = (array * 255).astype(np.uint8)
                else:
                    array = array.astype(np.uint8)
            else:
                array = array.astype(np.uint8)

        # 处理不同的数组形状
        if len(array.shape) == 2:
            # 灰度图像 (H, W)
            return Image.fromarray(array, mode='L')
        elif len(array.shape) == 3:
            if array.shape[2] == 1:
                # 单通道图像 (H, W, 1) -> (H, W)
                return Image.fromarray(array.squeeze(axis=2), mode='L')
            elif array.shape[2] == 3:
                # RGB图像 (H, W, 3)
                return Image.fromarray(array, mode='RGB')
            elif array.shape[2] == 4:
                # RGBA图像 (H, W, 4)
                return Image.fromarray(array, mode='RGBA')
            else:
                raise ImageProcessError(f"不支持的通道数: {array.shape[2]}，支持1、3、4通道")
        else:
            raise ImageProcessError(f"不支持的数组维度: {len(array.shape)}，支持2D或3D数组")

    except Exception as e:
        raise ImageProcessError(f"numpy数组转PIL图像失败: {str(e)}") from e


def image_to_numpy(image: Image.Image, target_mode: str = 'RGB') -> np.ndarray:
    """
    将PIL Image转换为numpy数组
    
    Args:
        image: PIL Image对象
        target_mode: 目标颜色模式，默认为'RGB'
        
    Returns:
        numpy数组
        
    Raises:
        ImageProcessError: 当转换失败时
    """
    try:
        if image.mode != target_mode:
            image = image.convert(target_mode)
        return np.array(image)
    except Exception as e:
        raise ImageProcessError(f"图片转numpy数组失败: {str(e)}") from e


def numpy_to_image(array: np.ndarray, mode: str = 'RGB') -> Image.Image:
    """
    将numpy数组转换为PIL Image
    
    Args:
        array: numpy数组
        mode: 图片模式，默认为'RGB'
        
    Returns:
        PIL Image对象
        
    Raises:
        ImageProcessError: 当转换失败时
    """
    try:
        return Image.fromarray(array, mode=mode)
    except Exception as e:
        raise ImageProcessError(f"numpy数组转图片失败: {str(e)}") from e
