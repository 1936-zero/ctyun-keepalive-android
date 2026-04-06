# coding=utf-8
import base64
import binascii
import io
import os
from typing import Union

from PIL import Image

MAX_IMAGE_BYTES = 8 * 1024 * 1024
MAX_IMAGE_SIDE = 4096
ALLOWED_IMAGE_FORMATS = {
    'PNG', 'JPEG', 'JPG', 'WEBP', 'BMP', 'GIF', 'TIFF'
}


class TypeError(Exception):
    pass


class DdddOcrInputError(TypeError):
    """向下兼容的输入异常基类"""


class InvalidImageError(DdddOcrInputError):
    """图片格式或大小非法时抛出"""


def base64_to_image(img_base64: str) -> Image.Image:
    if not isinstance(img_base64, str):
        raise DdddOcrInputError("base64 输入必须是字符串")
    try:
        img_data = base64.b64decode(img_base64, validate=True)
    except binascii.Error as exc:
        raise DdddOcrInputError("base64 内容非法") from exc
    if len(img_data) == 0:
        raise InvalidImageError("base64 内容为空")
    if len(img_data) > MAX_IMAGE_BYTES:
        raise InvalidImageError("图片容量超过允许上限")
    try:
        image = Image.open(io.BytesIO(img_data))
    except Exception as exc:
        raise InvalidImageError("无法从 base64 中解析图片") from exc
    return image


def get_img_base64(single_image_path):
    with open(single_image_path, 'rb') as fp:
        img_base64 = base64.b64encode(fp.read())
        return img_base64.decode()


def _coerce_bool(value: Union[bool, str], field_name: str) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        lowered = value.strip().lower()
        if lowered in {'true', '1', 'yes', 'y'}:
            return True
        if lowered in {'false', '0', 'no', 'n'}:
            return False
    raise DdddOcrInputError(f"字段 {field_name} 只能是 bool 或 true/false 字符串，当前值: {value!r}")


def _coerce_int(value: Union[int, str], field_name: str) -> int:
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        try:
            return int(value.strip())
        except ValueError:
            pass
    raise DdddOcrInputError(f"字段 {field_name} 只能是整数，当前值: {value!r}")


def _coerce_positive_int(value: Union[int, str], field_name: str) -> int:
    result = _coerce_int(value, field_name)
    if result <= 0:
        raise DdddOcrInputError(f"字段 {field_name} 必须是正整数")
    return result


def _ensure_file_exists(path: str, description: str) -> None:
    if path and not os.path.exists(path):
        raise DdddOcrInputError(f"{description} {path} 不存在")


def png_rgba_black_preprocess(img: Image):
    width = img.width
    height = img.height
    image = Image.new('RGB', size=(width, height), color=(255, 255, 255))
    image.paste(img, (0, 0), mask=img)
    return image
