# coding=utf-8
"""
模型加载器模块
负责ONNX模型的加载和管理
"""

import os
import json
from typing import List, Optional, Dict, Any
import onnxruntime

from ..utils.exceptions import ModelLoadError
from ..utils.validators import validate_model_config


class ModelLoader:
    """ONNX模型加载器"""
    
    def __init__(self, use_gpu: bool = False, device_id: int = 0):
        """
        初始化模型加载器
        
        Args:
            use_gpu: 是否使用GPU
            device_id: GPU设备ID
        """
        self.use_gpu = use_gpu
        self.device_id = device_id
        self._setup_providers()
    
    def _setup_providers(self) -> None:
        """设置ONNX运行时提供者"""
        try:
            if self.use_gpu:
                # GPU提供者
                self.providers = [
                    ('CUDAExecutionProvider', {
                        'device_id': self.device_id,
                        'arena_extend_strategy': 'kNextPowerOfTwo',
                        'gpu_mem_limit': 2 * 1024 * 1024 * 1024,  # 2GB
                        'cudnn_conv_algo_search': 'EXHAUSTIVE',
                        'do_copy_in_default_stream': True,
                    }),
                    'CPUExecutionProvider'
                ]
            else:
                # CPU提供者
                self.providers = ['CPUExecutionProvider']
        except Exception as e:
            # 如果GPU设置失败，回退到CPU
            self.providers = ['CPUExecutionProvider']
            if self.use_gpu:
                print(f"GPU设置失败，回退到CPU模式: {str(e)}")
    
    def load_model(self, model_path: str) -> onnxruntime.InferenceSession:
        """
        加载ONNX模型
        
        Args:
            model_path: 模型文件路径
            
        Returns:
            ONNX推理会话对象
            
        Raises:
            ModelLoadError: 当模型加载失败时
        """
        try:
            if not os.path.exists(model_path):
                raise ModelLoadError(f"模型文件不存在: {model_path}")
            
            # 设置ONNX运行时日志级别
            onnxruntime.set_default_logger_severity(3)
            
            # 创建推理会话
            session = onnxruntime.InferenceSession(model_path, providers=self.providers)
            
            return session
            
        except Exception as e:
            raise ModelLoadError(f"模型加载失败: {str(e)}") from e
    
    def get_model_info(self, session: onnxruntime.InferenceSession) -> Dict[str, Any]:
        """
        获取模型信息
        
        Args:
            session: ONNX推理会话
            
        Returns:
            模型信息字典
        """
        try:
            input_info = []
            for input_meta in session.get_inputs():
                input_info.append({
                    'name': input_meta.name,
                    'shape': input_meta.shape,
                    'type': input_meta.type
                })
            
            output_info = []
            for output_meta in session.get_outputs():
                output_info.append({
                    'name': output_meta.name,
                    'shape': output_meta.shape,
                    'type': output_meta.type
                })
            
            return {
                'inputs': input_info,
                'outputs': output_info,
                'providers': session.get_providers()
            }
            
        except Exception as e:
            return {'error': str(e)}
    
    def load_ocr_model(self, old: bool = False, beta: bool = False, 
                      import_onnx_path: str = "") -> onnxruntime.InferenceSession:
        """
        加载OCR模型
        
        Args:
            old: 是否使用旧版模型
            beta: 是否使用beta版模型
            import_onnx_path: 自定义模型路径
            
        Returns:
            ONNX推理会话对象
            
        Raises:
            ModelLoadError: 当模型加载失败时
        """
        try:
            if import_onnx_path:
                model_path = import_onnx_path
            else:
                base_dir = os.path.dirname(os.path.dirname(__file__))
                if old:
                    model_path = os.path.join(base_dir, 'common_old.onnx')
                elif beta:
                    model_path = os.path.join(base_dir, 'common.onnx')
                else:
                    model_path = os.path.join(base_dir, 'common_old.onnx')
            
            return self.load_model(model_path)
            
        except Exception as e:
            raise ModelLoadError(f"OCR模型加载失败: {str(e)}") from e
    
    def load_detection_model(self) -> onnxruntime.InferenceSession:
        """
        加载目标检测模型
        
        Returns:
            ONNX推理会话对象
            
        Raises:
            ModelLoadError: 当模型加载失败时
        """
        try:
            base_dir = os.path.dirname(os.path.dirname(__file__))
            model_path = os.path.join(base_dir, 'common_det.onnx')
            return self.load_model(model_path)
            
        except Exception as e:
            raise ModelLoadError(f"检测模型加载失败: {str(e)}") from e
    
    def load_custom_model(self, model_path: str, charset_path: str) -> tuple:
        """
        加载自定义模型和字符集
        
        Args:
            model_path: 模型文件路径
            charset_path: 字符集文件路径
            
        Returns:
            (推理会话, 字符集信息)
            
        Raises:
            ModelLoadError: 当加载失败时
        """
        try:
            # 加载模型
            session = self.load_model(model_path)
            
            # 加载字符集信息
            if not os.path.exists(charset_path):
                raise ModelLoadError(f"字符集文件不存在: {charset_path}")
            
            with open(charset_path, 'r', encoding="utf-8") as f:
                charset_info = json.loads(f.read())
            
            # 验证字符集信息格式
            required_keys = ['charset', 'word', 'image', 'channel']
            for key in required_keys:
                if key not in charset_info:
                    raise ModelLoadError(f"字符集文件缺少必需字段: {key}")
            
            return session, charset_info
            
        except Exception as e:
            raise ModelLoadError(f"自定义模型加载失败: {str(e)}") from e
    
    def validate_model_compatibility(self, session: onnxruntime.InferenceSession, 
                                   expected_input_shape: Optional[List[int]] = None) -> bool:
        """
        验证模型兼容性
        
        Args:
            session: ONNX推理会话
            expected_input_shape: 期望的输入形状
            
        Returns:
            是否兼容
        """
        try:
            inputs = session.get_inputs()
            if len(inputs) == 0:
                return False
            
            if expected_input_shape:
                input_shape = inputs[0].shape
                # 检查形状兼容性（忽略batch维度）
                if len(input_shape) != len(expected_input_shape):
                    return False
                
                for i, (actual, expected) in enumerate(zip(input_shape[1:], expected_input_shape[1:])):
                    if expected != -1 and actual != expected:
                        return False
            
            return True
            
        except Exception:
            return False
    
    def get_available_providers(self) -> List[str]:
        """
        获取可用的执行提供者
        
        Returns:
            可用提供者列表
        """
        return onnxruntime.get_available_providers()
    
    def switch_provider(self, use_gpu: bool, device_id: int = 0) -> None:
        """
        切换执行提供者
        
        Args:
            use_gpu: 是否使用GPU
            device_id: GPU设备ID
        """
        self.use_gpu = use_gpu
        self.device_id = device_id
        self._setup_providers()
    
    def __repr__(self) -> str:
        return f"ModelLoader(use_gpu={self.use_gpu}, device_id={self.device_id})"
