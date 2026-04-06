# coding=utf-8
"""
输入验证模块
提供各种输入参数的验证功能
"""

import pathlib
from typing import Union, List, Tuple, Any
from PIL import Image
import numpy as np

from .exceptions import DDDDOCRError


def validate_image_input(img_input: Any) -> bool:
    """
    验证图片输入是否为支持的类型

    Args:
        img_input: 图片输入

    Returns:
        bool: 是否为有效的图片输入

    Raises:
        DDDDOCRError: 当输入类型不支持时
    """
    valid_types = (bytes, str, pathlib.PurePath, Image.Image, np.ndarray)
    if not isinstance(img_input, valid_types):
        raise DDDDOCRError(f"不支持的图片输入类型: {type(img_input)}。支持的类型: {valid_types}")
    return True


def validate_model_config(ocr: bool = True, det: bool = False, old: bool = False, 
                         beta: bool = False, use_gpu: bool = False, device_id: int = 0) -> bool:
    """
    验证模型配置参数
    
    Args:
        ocr: 是否启用OCR功能
        det: 是否启用目标检测功能
        old: 是否使用旧版OCR模型
        beta: 是否使用beta版OCR模型
        use_gpu: 是否使用GPU
        device_id: GPU设备ID
        
    Returns:
        bool: 配置是否有效
        
    Raises:
        DDDDOCRError: 当配置无效时
    """
    # 检查基本参数类型
    if not isinstance(ocr, bool):
        raise DDDDOCRError("ocr参数必须为布尔值")
    if not isinstance(det, bool):
        raise DDDDOCRError("det参数必须为布尔值")
    if not isinstance(old, bool):
        raise DDDDOCRError("old参数必须为布尔值")
    if not isinstance(beta, bool):
        raise DDDDOCRError("beta参数必须为布尔值")
    if not isinstance(use_gpu, bool):
        raise DDDDOCRError("use_gpu参数必须为布尔值")
    if not isinstance(device_id, int) or device_id < 0:
        raise DDDDOCRError("device_id参数必须为非负整数")
    
    # 检查功能组合的有效性
    if not ocr and not det:
        # 允许两者都为False，这种情况下只能使用滑块功能
        pass
    
    # 检查模型版本冲突
    if old and beta:
        raise DDDDOCRError("old和beta参数不能同时为True")
    
    # 检查GPU配置
    if use_gpu and device_id < 0:
        raise DDDDOCRError("使用GPU时device_id必须为非负整数")
    
    return True


def validate_color_filter_params(colors: List[str] = None, 
                                custom_ranges: List[Tuple[Tuple[int, int, int], Tuple[int, int, int]]] = None) -> bool:
    """
    验证颜色过滤参数
    
    Args:
        colors: 预设颜色名称列表
        custom_ranges: 自定义HSV范围列表
        
    Returns:
        bool: 参数是否有效
        
    Raises:
        DDDDOCRError: 当参数无效时
    """
    if colors is not None:
        if not isinstance(colors, list):
            raise DDDDOCRError("colors参数必须为列表")
        for color in colors:
            if not isinstance(color, str):
                raise DDDDOCRError("colors列表中的元素必须为字符串")
    
    if custom_ranges is not None:
        if not isinstance(custom_ranges, list):
            raise DDDDOCRError("custom_ranges参数必须为列表")
        
        for range_item in custom_ranges:
            if not isinstance(range_item, (list, tuple)) or len(range_item) != 2:
                raise DDDDOCRError("custom_ranges中的每个元素必须为包含两个元素的列表或元组")
            
            lower, upper = range_item
            if not isinstance(lower, (list, tuple)) or len(lower) != 3:
                raise DDDDOCRError("HSV范围的下界必须为包含3个元素的列表或元组")
            if not isinstance(upper, (list, tuple)) or len(upper) != 3:
                raise DDDDOCRError("HSV范围的上界必须为包含3个元素的列表或元组")
            
            # 验证HSV值范围
            for i, (l, u) in enumerate(zip(lower, upper)):
                if not isinstance(l, int) or not isinstance(u, int):
                    raise DDDDOCRError("HSV值必须为整数")
                
                if i == 0:  # H通道
                    if not (0 <= l <= 180) or not (0 <= u <= 180):
                        raise DDDDOCRError("H通道值必须在0-180范围内")
                else:  # S和V通道
                    if not (0 <= l <= 255) or not (0 <= u <= 255):
                        raise DDDDOCRError("S和V通道值必须在0-255范围内")
                
                if l > u:
                    raise DDDDOCRError(f"HSV范围下界不能大于上界: {l} > {u}")
    
    if colors is None and custom_ranges is None:
        raise DDDDOCRError("必须指定colors或custom_ranges参数")
    
    return True


def validate_charset_range(charset_range: Union[int, str, List[str]]) -> bool:
    """
    验证字符集范围参数
    
    Args:
        charset_range: 字符集范围参数
        
    Returns:
        bool: 参数是否有效
        
    Raises:
        DDDDOCRError: 当参数无效时
    """
    if charset_range is None:
        return True
    
    if isinstance(charset_range, int):
        if charset_range < 0:
            raise DDDDOCRError("字符集范围索引必须为非负整数")
    elif isinstance(charset_range, str):
        if len(charset_range) == 0:
            raise DDDDOCRError("字符集范围字符串不能为空")
    elif isinstance(charset_range, list):
        if len(charset_range) == 0:
            raise DDDDOCRError("字符集范围列表不能为空")
        for char in charset_range:
            if not isinstance(char, str):
                raise DDDDOCRError("字符集范围列表中的元素必须为字符串")
    else:
        raise DDDDOCRError(f"不支持的字符集范围类型: {type(charset_range)}")
    
    return True
