# coding=utf-8
"""
OCR识别引擎
提供文字识别功能
"""

from typing import Union, List, Optional, Dict, Any, Tuple
import numpy as np
from PIL import Image

from .base import BaseEngine
from ..models.charset_manager import CharsetManager
from ..preprocessing.color_filter import ColorFilter
from ..preprocessing.image_processor import ImageProcessor
from ..utils.image_io import load_image_from_input, png_rgba_black_preprocess
from ..utils.exceptions import ModelLoadError, ImageProcessError
from ..utils.validators import validate_image_input


class OCREngine(BaseEngine):
    """OCR识别引擎"""
    
    def __init__(self, use_gpu: bool = False, device_id: int = 0, 
                 old: bool = False, beta: bool = False,
                 import_onnx_path: str = "", charsets_path: str = ""):
        """
        初始化OCR引擎
        
        Args:
            use_gpu: 是否使用GPU
            device_id: GPU设备ID
            old: 是否使用旧版模型
            beta: 是否使用beta版模型
            import_onnx_path: 自定义模型路径
            charsets_path: 自定义字符集路径
        """
        super().__init__(use_gpu, device_id)
        
        self.old = old
        self.beta = beta
        self.import_onnx_path = import_onnx_path
        self.charsets_path = charsets_path
        self.use_import_onnx = bool(import_onnx_path)
        
        # 字符集管理器
        self.charset_manager = CharsetManager()
        
        # 模型配置
        self.word = False
        self.resize = []
        self.channel = 1
        
        # 初始化引擎
        self.initialize()
    
    def initialize(self, **kwargs) -> None:
        """
        初始化OCR引擎
        
        Raises:
            ModelLoadError: 当初始化失败时
        """
        try:
            if self.use_import_onnx:
                # 加载自定义模型
                self.session, charset_info = self.model_loader.load_custom_model(
                    self.import_onnx_path, self.charsets_path
                )
                
                # 设置模型配置
                self.charset_manager.charset = charset_info['charset']

                # 初始化有效字符索引（使用完整字符集）
                self.charset_manager._update_valid_indices()

                self.word = charset_info['word']
                self.resize = charset_info['image']
                self.channel = charset_info['channel']
            else:
                # 加载默认模型
                self.session = self.model_loader.load_ocr_model(self.old, self.beta)
                
                # 加载默认字符集
                self.charset_manager.load_default_charset(self.old, self.beta)

                # 初始化有效字符索引（使用完整字符集）
                self.charset_manager._update_valid_indices()

                # 设置默认配置
                self.word = False
                self.resize = [64, 64]  # 默认尺寸
                self.channel = 1
            
            self.is_initialized = True
            
        except Exception as e:
            raise ModelLoadError(f"OCR引擎初始化失败: {str(e)}") from e
    
    def predict(self, image: Union[bytes, str, Image.Image], 
                png_fix: bool = False, probability: bool = False,
                color_filter_colors: Optional[List[str]] = None,
                color_filter_custom_ranges: Optional[List[Tuple[Tuple[int, int, int], Tuple[int, int, int]]]] = None,
                charset_range: Optional[Union[int, str, List[str]]] = None) -> Union[str, Dict[str, Any]]:
        """
        执行OCR识别
        
        Args:
            image: 输入图像
            png_fix: 是否修复PNG透明背景
            probability: 是否返回概率信息
            color_filter_colors: 颜色过滤预设颜色列表
            color_filter_custom_ranges: 自定义HSV颜色范围列表
            charset_range: 字符集范围限制
            
        Returns:
            识别结果文本或包含概率信息的字典
            
        Raises:
            ImageProcessError: 当图像处理失败时
            ModelLoadError: 当模型未初始化时
        """
        if not self.is_ready():
            raise ModelLoadError("OCR引擎未初始化")
        
        # 验证输入
        validate_image_input(image)
        
        try:
            # 加载图像
            pil_image = load_image_from_input(image)
            
            # 应用颜色过滤
            if color_filter_colors or color_filter_custom_ranges:
                try:
                    color_filter = ColorFilter(colors=color_filter_colors, 
                                             custom_ranges=color_filter_custom_ranges)
                    pil_image = color_filter.filter_image(pil_image)
                except Exception as e:
                    print(f"颜色过滤警告: {str(e)}，将跳过颜色过滤步骤")
            
            # 设置字符集范围
            if charset_range is not None:
                self.charset_manager.set_ranges(charset_range)
            else:
                # 确保在没有设置字符集范围时，有效索引被正确初始化
                self.charset_manager._update_valid_indices()
            
            # 预处理图像
            processed_image = self._preprocess_image(pil_image, png_fix)
            
            # 执行推理
            result = self._inference(processed_image, probability)
            
            return result
            
        except Exception as e:
            raise ImageProcessError(f"OCR识别失败: {str(e)}") from e
    
    def _preprocess_image(self, image: Image.Image, png_fix: bool) -> np.ndarray:
        """
        预处理图像
        
        Args:
            image: 输入图像
            png_fix: 是否修复PNG透明背景
            
        Returns:
            预处理后的numpy数组
        """
        try:
            # 处理PNG透明背景
            if png_fix and image.mode == 'RGBA':
                image = png_rgba_black_preprocess(image)
            
            # 调整图像尺寸
            if not self.use_import_onnx:
                # 默认模型的预处理
                target_height = 64
                target_width = int(image.size[0] * (target_height / image.size[1]))
                image = ImageProcessor.resize_image(image, (target_width, target_height))
                image = ImageProcessor.convert_to_grayscale(image)
            else:
                # 自定义模型的预处理
                if self.resize[0] == -1:
                    if self.word:
                        image = ImageProcessor.resize_image(image, (self.resize[1], self.resize[1]))
                    else:
                        target_height = self.resize[1]
                        target_width = int(image.size[0] * (target_height / image.size[1]))
                        image = ImageProcessor.resize_image(image, (target_width, target_height))
                else:
                    image = ImageProcessor.resize_image(image, (self.resize[0], self.resize[1]))
                
                # 根据通道数转换
                if self.channel == 1:
                    image = ImageProcessor.convert_to_grayscale(image)
            
            # 转换为numpy数组并标准化
            img_array = np.array(image).astype(np.float32)
            
            # 标准化到[0,1]
            img_array = img_array / 255.0
            
            # 调整维度
            if len(img_array.shape) == 2:
                img_array = np.expand_dims(img_array, axis=0)  # 添加通道维度
            elif len(img_array.shape) == 3:
                img_array = img_array.transpose(2, 0, 1)  # HWC -> CHW
            
            img_array = np.expand_dims(img_array, axis=0)  # 添加batch维度
            
            return img_array
            
        except Exception as e:
            raise ImageProcessError(f"图像预处理失败: {str(e)}") from e
    
    def _inference(self, image_array: np.ndarray, probability: bool) -> Union[str, Dict[str, Any]]:
        """
        执行模型推理
        
        Args:
            image_array: 预处理后的图像数组
            probability: 是否返回概率信息
            
        Returns:
            识别结果
        """
        try:
            # 获取输入名称
            input_name = self.session.get_inputs()[0].name
            
            # 执行推理
            outputs = self.session.run(None, {input_name: image_array})
            
            # 处理输出
            if probability:
                return self._process_probability_output(outputs[0])
            else:
                return self._process_text_output(outputs[0])
                
        except Exception as e:
            raise ModelLoadError(f"模型推理失败: {str(e)}") from e
    
    def _process_text_output(self, output: np.ndarray) -> str:
        """
        处理文本输出
        
        Args:
            output: 模型输出
            
        Returns:
            识别的文本
        """
        try:
            # 获取预测结果
            if len(output.shape) == 3:
                # 序列输出 (sequence_length, batch_size, num_classes) 或 (batch_size, sequence_length, num_classes)
                # 需要判断哪个维度是batch_size=1
                if output.shape[1] == 1:
                    # 形状为 (sequence_length, 1, num_classes)
                    predicted_indices = np.argmax(output[:, 0, :], axis=1)
                elif output.shape[0] == 1:
                    # 形状为 (1, sequence_length, num_classes)
                    predicted_indices = np.argmax(output[0, :, :], axis=1)
                else:
                    # 默认取第一个batch
                    predicted_indices = np.argmax(output[0, :, :], axis=1)
            else:
                # 单字符输出或2D序列输出
                predicted_indices = np.argmax(output, axis=-1)
                # 确保结果是数组形式，即使是单个值
                if predicted_indices.ndim == 0:
                    predicted_indices = np.array([predicted_indices])
            
            # 正确的CTC解码：在索引级别进行去重
            charset = self.charset_manager.get_charset()
            valid_indices = self.charset_manager.get_valid_indices()

            # 步骤1：CTC解码 - 在索引级别去除连续重复
            decoded_indices = self._ctc_decode_indices(predicted_indices)

            # 步骤2：转换为字符并应用字符集范围限制
            result_chars = []
            for idx in decoded_indices:
                # 检查字符集范围限制
                if valid_indices and idx not in valid_indices:
                    continue

                # 检查索引有效性并转换为字符
                if 0 <= idx < len(charset):
                    char = charset[idx]
                    # 注意：这里不跳过空字符，因为CTC解码已经处理了blank
                    result_chars.append(char)

            return ''.join(result_chars)
            
        except Exception as e:
            raise ModelLoadError(f"文本输出处理失败: {str(e)}") from e

    def _ctc_decode_indices(self, predicted_indices: np.ndarray) -> List[int]:
        """
        CTC解码：在索引级别去除连续重复和blank字符

        Args:
            predicted_indices: 预测的索引数组

        Returns:
            解码后的索引列表
        """
        if len(predicted_indices) == 0:
            return []

        decoded_indices = []
        prev_idx = None

        for idx in predicted_indices:
            # 转换为Python int类型以确保一致性
            idx = int(idx)

            # CTC解码规则：
            # 1. 跳过连续重复的索引
            # 2. 跳过blank字符（索引0，对应空字符）
            if idx != prev_idx:  # 不是连续重复
                if idx != 0:  # 不是blank字符（假设索引0是blank）
                    decoded_indices.append(idx)

            prev_idx = idx

        return decoded_indices

    def _process_probability_output(self, output: np.ndarray) -> Dict[str, Any]:
        """
        处理概率输出
        
        Args:
            output: 模型输出
            
        Returns:
            包含概率信息的字典
        """
        try:
            # 应用softmax
            if len(output.shape) == 3:
                probabilities = self._softmax(output, axis=2)
            else:
                probabilities = self._softmax(output, axis=1)
            
            # 获取文本结果
            text_result = self._process_text_output(output)
            
            # 构建概率信息
            charset = self.charset_manager.get_charset()
            prob_info = {
                'text': text_result,
                'probabilities': probabilities.tolist(),
                'charset': charset,
                'confidence': float(np.mean(np.max(probabilities, axis=-1)))
            }
            
            return prob_info
            
        except Exception as e:
            raise ModelLoadError(f"概率输出处理失败: {str(e)}") from e
    
    def _softmax(self, x: np.ndarray, axis: int = -1) -> np.ndarray:
        """
        计算softmax
        
        Args:
            x: 输入数组
            axis: 计算轴
            
        Returns:
            softmax结果
        """
        exp_x = np.exp(x - np.max(x, axis=axis, keepdims=True))
        return exp_x / np.sum(exp_x, axis=axis, keepdims=True)
    
    def set_charset_range(self, charset_range: Union[int, str, List[str]]) -> None:
        """
        设置字符集范围
        
        Args:
            charset_range: 字符集范围参数
        """
        self.charset_manager.set_ranges(charset_range)
    
    def get_charset(self) -> List[str]:
        """
        获取字符集
        
        Returns:
            字符集列表
        """
        return self.charset_manager.get_charset()
    
    def _reload_model(self) -> None:
        """重新加载模型"""
        self.initialize()
