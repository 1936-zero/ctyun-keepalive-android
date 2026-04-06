# coding=utf-8
"""
图像处理模块
提供图像预处理、增强、变换等功能
"""

from typing import Tuple, Union, Optional
import numpy as np
from PIL import Image

from ..utils.exceptions import safe_import_opencv, ImageProcessError
from ..utils.image_io import image_to_numpy, numpy_to_image, png_rgba_black_preprocess

# 安全导入OpenCV
cv2 = safe_import_opencv()


class ImageProcessor:
    """图像处理器类，提供各种图像预处理功能"""
    
    @staticmethod
    def resize_image(image: Image.Image, target_size: Tuple[int, int], 
                    keep_aspect_ratio: bool = False, 
                    resample: int = Image.LANCZOS) -> Image.Image:
        """
        调整图像尺寸
        
        Args:
            image: 输入图像
            target_size: 目标尺寸 (width, height)
            keep_aspect_ratio: 是否保持宽高比
            resample: 重采样方法
            
        Returns:
            调整尺寸后的图像
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            if keep_aspect_ratio:
                # 计算保持宽高比的尺寸
                original_width, original_height = image.size
                target_width, target_height = target_size
                
                # 计算缩放比例
                width_ratio = target_width / original_width
                height_ratio = target_height / original_height
                scale_ratio = min(width_ratio, height_ratio)
                
                # 计算新尺寸
                new_width = int(original_width * scale_ratio)
                new_height = int(original_height * scale_ratio)
                
                return image.resize((new_width, new_height), resample)
            else:
                return image.resize(target_size, resample)
                
        except Exception as e:
            raise ImageProcessError(f"图像尺寸调整失败: {str(e)}") from e
    
    @staticmethod
    def convert_to_grayscale(image: Image.Image) -> Image.Image:
        """
        将图像转换为灰度图
        
        Args:
            image: 输入图像
            
        Returns:
            灰度图像
            
        Raises:
            ImageProcessError: 当转换失败时
        """
        try:
            return image.convert('L')
        except Exception as e:
            raise ImageProcessError(f"灰度转换失败: {str(e)}") from e
    
    @staticmethod
    def normalize_image(image: Union[Image.Image, np.ndarray], 
                       target_mean: float = 0.0, target_std: float = 1.0) -> np.ndarray:
        """
        标准化图像像素值
        
        Args:
            image: 输入图像
            target_mean: 目标均值
            target_std: 目标标准差
            
        Returns:
            标准化后的numpy数组
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            if isinstance(image, Image.Image):
                img_array = image_to_numpy(image)
            else:
                img_array = image.copy()
            
            # 转换为float32并归一化到[0,1]
            img_array = img_array.astype(np.float32) / 255.0
            
            # 计算当前均值和标准差
            current_mean = np.mean(img_array)
            current_std = np.std(img_array)
            
            # 避免除零
            if current_std == 0:
                current_std = 1.0
            
            # 标准化
            normalized = (img_array - current_mean) / current_std
            normalized = normalized * target_std + target_mean
            
            return normalized
            
        except Exception as e:
            raise ImageProcessError(f"图像标准化失败: {str(e)}") from e
    
    @staticmethod
    def enhance_contrast(image: Image.Image, factor: float = 1.5) -> Image.Image:
        """
        增强图像对比度
        
        Args:
            image: 输入图像
            factor: 对比度增强因子
            
        Returns:
            对比度增强后的图像
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            from PIL import ImageEnhance
            enhancer = ImageEnhance.Contrast(image)
            return enhancer.enhance(factor)
        except Exception as e:
            raise ImageProcessError(f"对比度增强失败: {str(e)}") from e
    
    @staticmethod
    def enhance_sharpness(image: Image.Image, factor: float = 1.5) -> Image.Image:
        """
        增强图像锐度
        
        Args:
            image: 输入图像
            factor: 锐度增强因子
            
        Returns:
            锐度增强后的图像
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            from PIL import ImageEnhance
            enhancer = ImageEnhance.Sharpness(image)
            return enhancer.enhance(factor)
        except Exception as e:
            raise ImageProcessError(f"锐度增强失败: {str(e)}") from e
    
    @staticmethod
    def remove_noise(image: Image.Image, kernel_size: int = 3) -> Image.Image:
        """
        去除图像噪声
        
        Args:
            image: 输入图像
            kernel_size: 滤波核大小
            
        Returns:
            去噪后的图像
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            img_array = image_to_numpy(image)
            
            # 使用中值滤波去噪
            if len(img_array.shape) == 3:
                # 彩色图像
                denoised = cv2.medianBlur(img_array, kernel_size)
            else:
                # 灰度图像
                denoised = cv2.medianBlur(img_array, kernel_size)
            
            return numpy_to_image(denoised, image.mode)
            
        except Exception as e:
            raise ImageProcessError(f"图像去噪失败: {str(e)}") from e
    
    @staticmethod
    def binarize_image(image: Image.Image, threshold: int = 128, 
                      method: str = 'simple') -> Image.Image:
        """
        图像二值化
        
        Args:
            image: 输入图像
            threshold: 二值化阈值
            method: 二值化方法 ('simple', 'otsu', 'adaptive')
            
        Returns:
            二值化后的图像
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            # 转换为灰度图
            if image.mode != 'L':
                gray_image = image.convert('L')
            else:
                gray_image = image
            
            img_array = image_to_numpy(gray_image)
            
            if method == 'simple':
                _, binary = cv2.threshold(img_array, threshold, 255, cv2.THRESH_BINARY)
            elif method == 'otsu':
                _, binary = cv2.threshold(img_array, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            elif method == 'adaptive':
                binary = cv2.adaptiveThreshold(img_array, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
                                             cv2.THRESH_BINARY, 11, 2)
            else:
                raise ValueError(f"不支持的二值化方法: {method}")
            
            return numpy_to_image(binary, 'L')
            
        except Exception as e:
            raise ImageProcessError(f"图像二值化失败: {str(e)}") from e
    
    @staticmethod
    def preprocess_for_ocr(image: Image.Image, target_height: int = 64, 
                          enhance_contrast: bool = True, 
                          remove_noise: bool = True) -> Image.Image:
        """
        OCR预处理流水线
        
        Args:
            image: 输入图像
            target_height: 目标高度
            enhance_contrast: 是否增强对比度
            remove_noise: 是否去噪
            
        Returns:
            预处理后的图像
            
        Raises:
            ImageProcessError: 当处理失败时
        """
        try:
            processed_image = image.copy()
            
            # 处理PNG透明背景
            if processed_image.mode == 'RGBA':
                processed_image = png_rgba_black_preprocess(processed_image)
            
            # 调整尺寸（保持宽高比）
            original_width, original_height = processed_image.size
            target_width = int(original_width * (target_height / original_height))
            processed_image = ImageProcessor.resize_image(
                processed_image, (target_width, target_height), keep_aspect_ratio=False
            )
            
            # 增强对比度
            if enhance_contrast:
                processed_image = ImageProcessor.enhance_contrast(processed_image, factor=1.2)
            
            # 去噪
            if remove_noise:
                processed_image = ImageProcessor.remove_noise(processed_image, kernel_size=3)
            
            # 转换为灰度图
            processed_image = ImageProcessor.convert_to_grayscale(processed_image)
            
            return processed_image
            
        except Exception as e:
            raise ImageProcessError(f"OCR预处理失败: {str(e)}") from e
