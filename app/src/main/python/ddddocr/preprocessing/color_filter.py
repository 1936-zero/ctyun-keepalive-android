# coding=utf-8
"""
颜色过滤模块
提供基于HSV颜色空间的图像颜色过滤功能
"""

from typing import List, Tuple, Optional, Union
import numpy as np
from PIL import Image

from ..utils.exceptions import safe_import_opencv, ImageProcessError
from ..utils.validators import validate_color_filter_params
from ..utils.image_io import image_to_numpy, numpy_to_image

# 安全导入OpenCV
cv2 = safe_import_opencv()


class ColorFilter:
    """图片颜色过滤器类，支持HSV颜色空间的颜色范围过滤"""
    
    # 内置常见颜色预设的HSV范围值
    COLOR_PRESETS = {
        'red': [((0, 50, 50), (10, 255, 255)), ((170, 50, 50), (180, 255, 255))],  # 红色需要两个范围
        'blue': [((100, 50, 50), (130, 255, 255))],
        'green': [((40, 50, 50), (80, 255, 255))],
        'yellow': [((20, 50, 50), (40, 255, 255))],
        'orange': [((10, 50, 50), (20, 255, 255))],
        'purple': [((130, 50, 50), (170, 255, 255))],
        'cyan': [((80, 50, 50), (100, 255, 255))],
        'black': [((0, 0, 0), (180, 255, 50))],
        'white': [((0, 0, 200), (180, 30, 255))],
        'gray': [((0, 0, 50), (180, 30, 200))]
    }
    
    def __init__(self, colors: Optional[List[str]] = None, 
                 custom_ranges: Optional[List[Tuple[Tuple[int, int, int], Tuple[int, int, int]]]] = None):
        """
        初始化颜色过滤器
        
        Args:
            colors: 预设颜色名称列表，如 ['red', 'blue']
            custom_ranges: 自定义HSV范围列表，如 [((0,50,50), (10,255,255))]
            
        Raises:
            ValueError: 当参数无效时
        """
        # 验证输入参数
        validate_color_filter_params(colors, custom_ranges)
        
        self.hsv_ranges = []
        
        if colors:
            for color in colors:
                color_lower = color.lower()
                if color_lower in self.COLOR_PRESETS:
                    self.hsv_ranges.extend(self.COLOR_PRESETS[color_lower])
                else:
                    available_colors = ', '.join(self.COLOR_PRESETS.keys())
                    raise ValueError(f"不支持的颜色预设: {color}。可用颜色: {available_colors}")
        
        if custom_ranges:
            self.hsv_ranges.extend(custom_ranges)
        
        if not self.hsv_ranges:
            raise ValueError("必须指定colors或custom_ranges参数")
    
    def filter_image(self, image: Union[Image.Image, np.ndarray]) -> Image.Image:
        """
        对图片进行颜色过滤
        
        Args:
            image: 输入图片（PIL.Image或numpy.ndarray）
            
        Returns:
            过滤后的PIL Image对象
            
        Raises:
            ImageProcessError: 当图片处理失败时
        """
        try:
            # 转换为numpy数组
            if isinstance(image, Image.Image):
                img_array = cv2.cvtColor(image_to_numpy(image, 'RGB'), cv2.COLOR_RGB2BGR)
            else:
                img_array = image.copy()
            
            # 转换到HSV颜色空间
            hsv = cv2.cvtColor(img_array, cv2.COLOR_BGR2HSV)
            
            # 创建掩码
            mask = np.zeros(hsv.shape[:2], dtype=np.uint8)
            
            for lower, upper in self.hsv_ranges:
                # 创建当前颜色范围的掩码
                range_mask = cv2.inRange(hsv, np.array(lower), np.array(upper))
                # 合并到总掩码
                mask = cv2.bitwise_or(mask, range_mask)
            
            # 应用掩码
            result = cv2.bitwise_and(img_array, img_array, mask=mask)
            
            # 将背景设为白色
            result[mask == 0] = [255, 255, 255]
            
            # 转换回PIL Image
            result_rgb = cv2.cvtColor(result, cv2.COLOR_BGR2RGB)
            return numpy_to_image(result_rgb, 'RGB')
            
        except Exception as e:
            raise ImageProcessError(f"颜色过滤处理失败: {str(e)}") from e
    
    def get_mask(self, image: Union[Image.Image, np.ndarray]) -> np.ndarray:
        """
        获取颜色过滤的掩码
        
        Args:
            image: 输入图片
            
        Returns:
            二值掩码数组
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            # 转换为numpy数组
            if isinstance(image, Image.Image):
                img_array = cv2.cvtColor(image_to_numpy(image, 'RGB'), cv2.COLOR_RGB2BGR)
            else:
                img_array = image.copy()
            
            # 转换到HSV颜色空间
            hsv = cv2.cvtColor(img_array, cv2.COLOR_BGR2HSV)
            
            # 创建掩码
            mask = np.zeros(hsv.shape[:2], dtype=np.uint8)
            
            for lower, upper in self.hsv_ranges:
                # 创建当前颜色范围的掩码
                range_mask = cv2.inRange(hsv, np.array(lower), np.array(upper))
                # 合并到总掩码
                mask = cv2.bitwise_or(mask, range_mask)
            
            return mask
            
        except Exception as e:
            raise ImageProcessError(f"掩码生成失败: {str(e)}") from e
    
    def add_color_range(self, lower: Tuple[int, int, int], upper: Tuple[int, int, int]) -> None:
        """
        添加自定义颜色范围
        
        Args:
            lower: HSV下界
            upper: HSV上界
        """
        validate_color_filter_params(None, [(lower, upper)])
        self.hsv_ranges.append((lower, upper))
    
    def add_preset_color(self, color: str) -> None:
        """
        添加预设颜色
        
        Args:
            color: 预设颜色名称
            
        Raises:
            ValueError: 当颜色名称不存在时
        """
        color_lower = color.lower()
        if color_lower in self.COLOR_PRESETS:
            self.hsv_ranges.extend(self.COLOR_PRESETS[color_lower])
        else:
            available_colors = ', '.join(self.COLOR_PRESETS.keys())
            raise ValueError(f"不支持的颜色预设: {color}。可用颜色: {available_colors}")
    
    def clear_ranges(self) -> None:
        """清空所有颜色范围"""
        self.hsv_ranges.clear()
    
    def get_ranges(self) -> List[Tuple[Tuple[int, int, int], Tuple[int, int, int]]]:
        """
        获取当前所有颜色范围
        
        Returns:
            颜色范围列表
        """
        return self.hsv_ranges.copy()
    
    @classmethod
    def get_available_colors(cls) -> List[str]:
        """
        获取所有可用的预设颜色名称
        
        Returns:
            可用颜色名称列表
        """
        return list(cls.COLOR_PRESETS.keys())
    
    @classmethod
    def get_color_range(cls, color: str) -> List[Tuple[Tuple[int, int, int], Tuple[int, int, int]]]:
        """
        获取指定颜色的HSV范围
        
        Args:
            color: 颜色名称
            
        Returns:
            颜色的HSV范围列表
            
        Raises:
            ValueError: 当颜色名称不存在时
        """
        color_lower = color.lower()
        if color_lower in cls.COLOR_PRESETS:
            return cls.COLOR_PRESETS[color_lower].copy()
        else:
            available_colors = ', '.join(cls.COLOR_PRESETS.keys())
            raise ValueError(f"不支持的颜色预设: {color}。可用颜色: {available_colors}")
    
    def __repr__(self) -> str:
        return f"ColorFilter(ranges={len(self.hsv_ranges)})"
    
    def __str__(self) -> str:
        return f"ColorFilter with {len(self.hsv_ranges)} color ranges"
